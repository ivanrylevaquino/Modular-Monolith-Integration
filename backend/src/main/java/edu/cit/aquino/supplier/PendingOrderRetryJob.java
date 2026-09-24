package edu.cit.aquino.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class PendingOrderRetryJob {
    private static final Logger log = LoggerFactory.getLogger(PendingOrderRetryJob.class);

    private final SupplierOrderRepository repository;
    private final SupplierGatewayImpl gateway;

    PendingOrderRetryJob(SupplierOrderRepository repository, SupplierGatewayImpl gateway) {
        this.repository = repository;
        this.gateway = gateway;
    }

    @Scheduled(fixedDelay = 10000, initialDelay = 5000)
    public void retryPendingOrders() {
        List<SupplierOrderRecord> pending = repository.findPendingOrders();
        if (pending.isEmpty()) {
            return;
        }

        log.info("Found {} pending supplier order(s) awaiting delivery to LegacySupply", pending.size());
        for (SupplierOrderRecord order : pending) {
            try {
                var skuInfoOpt = SupplierCatalogMapping.findByProductId(order.productId());
                if (skuInfoOpt.isEmpty()) {
                    log.error("Unknown product {} for pending order #{}, skipping", order.productId(), order.id());
                    continue;
                }
                String sku = skuInfoOpt.get().supplierSku();
                SupplierOrderResult result = gateway.submitOrderWithRetry(
                        order.id(),
                        sku,
                        order.cases(),
                        order.units(),
                        order.buyerRef(),
                        order.requestId(),
                        order.productId(),
                        order.createdAt()
                );
                if (result.status() == SupplierOrderStatus.PLACED) {
                    log.info("Pending order #{} successfully submitted: PO={}", order.id(), result.poNumber());
                }
            } catch (Exception ex) {
                log.warn("Error retrying pending order #{}: {}", order.id(), ex.getMessage());
            }
        }
    }
}
