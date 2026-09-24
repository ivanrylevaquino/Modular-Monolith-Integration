package edu.cit.aquino.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
class SupplierGatewayImpl implements SupplierGateway {
    private static final Logger log = LoggerFactory.getLogger(SupplierGatewayImpl.class);
    private static final int MAX_ATTEMPTS = 3;

    private final LegacySupplyClient client;
    private final SupplierOrderRepository repository;

    SupplierGatewayImpl(LegacySupplyClient client, SupplierOrderRepository repository) {
        this.client = client;
        this.repository = repository;
    }

    @Override
    public SupplierOrderResult orderReplenishment(String productId, int unitsNeeded) {
        if (unitsNeeded <= 0) {
            throw new IllegalArgumentException("unitsNeeded must be greater than zero");
        }

        SupplierCatalogMapping.SkuInfo skuInfo = SupplierCatalogMapping.findByProductId(productId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown product for supplier order: " + productId));

        int packSize = skuInfo.packSize();
        int cases = (int) Math.ceil((double) unitsNeeded / packSize);
        int totalUnits = cases * packSize;

        long orderId = repository.nextOrderId();
        String buyerRef = "RO-" + orderId;
        String requestId = "REQ-" + orderId + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Instant now = Instant.now();

        SupplierOrderRecord record = new SupplierOrderRecord(
                orderId,
                productId,
                buyerRef,
                requestId,
                null,
                cases,
                totalUnits,
                SupplierOrderStatus.PENDING,
                now,
                now
        );
        repository.insert(record);
        log.info("Recorded supplier order #{} for product {} ({} cases / {} units, buyerRef={}) as PENDING",
                orderId, productId, cases, totalUnits, buyerRef);

        return submitOrderWithRetry(orderId, skuInfo.supplierSku(), cases, totalUnits, buyerRef, requestId, productId, now);
    }

    SupplierOrderResult submitOrderWithRetry(
            long orderId,
            String supplierSku,
            int cases,
            int totalUnits,
            String buyerRef,
            String requestId,
            String productId,
            Instant createdAt
    ) {
        PurchaseOrderXml orderXml = new PurchaseOrderXml(supplierSku, cases, buyerRef);
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                // Check if order already reached LegacySupply before retrying
                if (attempt > 1) {
                    try {
                        PurchaseOrderListXml existing = client.getOrdersByBuyerRef(buyerRef);
                        if (existing != null && existing.getOrders() != null && !existing.getOrders().isEmpty()) {
                            PurchaseOrderAckXml matched = existing.getOrders().get(0);
                            String po = matched.getPoNumber();
                            repository.updateStatusAndPo(orderId, SupplierOrderStatus.PLACED, po);
                            log.info("Order #{} already placed at supplier as PO {} (verified by buyerRef)", orderId, po);
                            return new SupplierOrderResult(orderId, productId, buyerRef, requestId, po, cases, totalUnits,
                                    SupplierOrderStatus.PLACED, "Confirmed on LegacySupply", createdAt);
                        }
                    } catch (Exception ignored) {
                        // proceed with retry attempt
                    }
                }

                log.info("Sending order #{} to LegacySupply (attempt {}/{}, requestId={})",
                        orderId, attempt, MAX_ATTEMPTS, requestId);
                PurchaseOrderAckXml ack = client.placePurchaseOrder(orderXml, requestId);
                String poNumber = ack.getPoNumber();
                repository.updateStatusAndPo(orderId, SupplierOrderStatus.PLACED, poNumber);
                log.info("Order #{} placed successfully on LegacySupply: PO={}", orderId, poNumber);
                return new SupplierOrderResult(orderId, productId, buyerRef, requestId, poNumber, cases, totalUnits,
                        SupplierOrderStatus.PLACED, "Order placed successfully", createdAt);

            } catch (Exception ex) {
                lastException = ex;
                log.warn("Attempt {}/{} failed for order #{}: {}", attempt, MAX_ATTEMPTS, orderId, ex.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(attempt * 500L); // backoff 500ms, 1000ms
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.warn("LegacySupply unavailable after {} attempts for order #{}. Preserving order in PENDING status for background retry.",
                MAX_ATTEMPTS, orderId);
        return new SupplierOrderResult(
                orderId,
                productId,
                buyerRef,
                requestId,
                null,
                cases,
                totalUnits,
                SupplierOrderStatus.PENDING,
                "LegacySupply currently unavailable. Reorder kept PENDING: " + (lastException != null ? lastException.getMessage() : "timeout"),
                createdAt
        );
    }
}
