package edu.cit.aquino.supplier;

import edu.cit.aquino.inventory.StockReplenishedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class DeliveryTrackingJob {
    private static final Logger log = LoggerFactory.getLogger(DeliveryTrackingJob.class);
    private static final int BATCH_SIZE = 4; // Stay polite within rate quota

    private final LegacySupplyClient client;
    private final SupplierOrderRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    DeliveryTrackingJob(
            LegacySupplyClient client,
            SupplierOrderRepository repository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.client = client;
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelay = 12000, initialDelay = 8000)
    public void trackActiveOrders() {
        List<SupplierOrderRecord> active = repository.findActiveOrders(BATCH_SIZE);
        if (active.isEmpty()) {
            return;
        }

        for (SupplierOrderRecord order : active) {
            String po = order.poNumber();
            if (po == null || po.isBlank()) continue;

            try {
                PurchaseOrderStatusXml statusXml = client.getOrderStatus(po);
                String statusCode = statusXml.getStatusCode();
                SupplierOrderStatus newStatus = mapLegacyStatusCode(statusCode);

                if (newStatus != order.status()) {
                    log.info("Order #{} (PO={}) transitioned from {} to {}", order.id(), po, order.status(), newStatus);
                    repository.updateStatus(order.id(), newStatus);

                    if (newStatus == SupplierOrderStatus.DELIVERED) {
                        log.info("Order #{} delivered! Publishing StockReplenishedEvent for {} (+{} units)",
                                order.id(), order.productId(), order.units());
                        eventPublisher.publishEvent(new StockReplenishedEvent(order.productId(), order.units()));
                    } else if (newStatus == SupplierOrderStatus.CANCELLED) {
                        log.warn("Order #{} (PO={}) was CANCELLED by supplier. No restock performed.", order.id(), po);
                    }
                }
            } catch (LegacySupplyException e) {
                if ("E-RATE-03".equals(e.getErrorCode())) {
                    log.warn("Rate limit approached (E-RATE-03), backing off status polling");
                    break;
                } else if ("E-PO-04".equals(e.getErrorCode())) {
                    log.warn("PO {} not found on LegacySupply, marking as UNKNOWN", po);
                    repository.updateStatus(order.id(), SupplierOrderStatus.UNKNOWN);
                } else {
                    log.warn("Failed to check status for PO {}: {}", po, e.getMessage());
                }
            } catch (Exception ex) {
                log.warn("Unexpected error polling status for PO {}: {}", po, ex.getMessage());
            }
        }
    }

    private SupplierOrderStatus mapLegacyStatusCode(String code) {
        if (code == null) return SupplierOrderStatus.UNKNOWN;
        return switch (code.trim()) {
            case "10" -> SupplierOrderStatus.PLACED;
            case "20" -> SupplierOrderStatus.PICKING;
            case "30" -> SupplierOrderStatus.SHIPPED;
            case "40" -> SupplierOrderStatus.DELIVERED;
            case "90", "99", "CANCELLED", "VOID" -> SupplierOrderStatus.CANCELLED;
            default -> {
                log.warn("Received unmapped/unexpected supplier status code: '{}', mapping to UNKNOWN", code);
                yield SupplierOrderStatus.UNKNOWN;
            }
        };
    }
}
