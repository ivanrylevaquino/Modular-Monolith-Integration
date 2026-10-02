package edu.cit.aquino.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cit.aquino.inventory.InventoryItem;
import edu.cit.aquino.inventory.InventoryService;
import edu.cit.aquino.shop.OrderLineItem;
import edu.cit.aquino.shop.OrderResult;
import edu.cit.aquino.shop.OrderService;
import edu.cit.aquino.supplier.SupplierGateway;
import edu.cit.aquino.supplier.SupplierOrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
class TianggeFeedPoller {
    private static final Logger log = LoggerFactory.getLogger(TianggeFeedPoller.class);

    private final TianggeClient client;
    private final TianggeOrderRepository repository;
    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final SupplierGateway supplierGateway;
    private final ObjectMapper objectMapper = new ObjectMapper();

    TianggeFeedPoller(
            TianggeClient client,
            TianggeOrderRepository repository,
            OrderService orderService,
            InventoryService inventoryService,
            @Autowired(required = false) SupplierGateway supplierGateway) {
        this.client = client;
        this.repository = repository;
        this.orderService = orderService;
        this.inventoryService = inventoryService;
        this.supplierGateway = supplierGateway;
    }

    @Scheduled(fixedDelay = 2500, initialDelay = 8000)
    public void pollFeed() {
        long cursor = repository.getLastCursor();
        TianggeFeedResponse response = client.getFeed(cursor, 50);

        if (response == null || response.events() == null || response.events().isEmpty()) {
            if (response != null && response.nextCursor() != null && response.nextCursor() > cursor) {
                repository.updateLastCursor(response.nextCursor());
            }
            return;
        }

        log.info("Received {} events from Tiangge feed (after={})", response.events().size(), cursor);

        long currentCursor = cursor;
        boolean processingFailed = false;
        for (TianggeFeedEvent event : response.events()) {
            try {
                if ("ORDER_PLACED".equalsIgnoreCase(event.type())) {
                    processOrderPlaced(event);
                } else if ("ORDER_CANCELLED".equalsIgnoreCase(event.type())) {
                    processOrderCancelled(event);
                } else {
                    log.info("Ignoring feed event type: {}", event.type());
                }
            } catch (Exception e) {
                log.error("Error processing feed event #{}: {}", event.seq(), e.getMessage(), e);
                processingFailed = true;
                break;
            }
            currentCursor = Math.max(currentCursor, event.seq());
            repository.updateLastCursor(currentCursor);
        }

        if (!processingFailed && response.nextCursor() != null && response.nextCursor() > currentCursor) {
            repository.updateLastCursor(response.nextCursor());
        }
    }

    private void processOrderPlaced(TianggeFeedEvent event) {
        String orderId = event.orderId();
        Optional<TianggeOrderRecord> existing = repository.findOrder(orderId);
        if (existing.isPresent()) {
            log.info("Duplicate order {} already processed with decision {}, skipping duplicate placement",
                    orderId, existing.get().decision());
            // Re-affirm existing decision to Tiangge if needed
            TianggeOrderRecord rec = existing.get();
            if (rec.decision() != null && rec.shopOrderId() != null) {
                client.sendDecision(orderId,
                        new TianggeDecisionRequest(rec.decision(), rec.shopOrderId(), "Re-affirmed decision"));
            }
            return;
        }

        List<TianggeOrderLine> lines = event.lines() != null ? event.lines() : List.of();
        String linesJson;
        try {
            linesJson = objectMapper.writeValueAsString(lines);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize Tiangge order lines for " + orderId, e);
        }

        // Check stock availability across all line items
        boolean allInStock = !lines.isEmpty() && lines.stream().allMatch(l -> {
            InventoryItem item = inventoryService.getItem(l.sellerSku());
            return item != null && item.stock() >= l.qty();
        });

        if (allInStock) {
            // Fulfill immediately via OrderService
            List<OrderLineItem> items = lines.stream()
                    .map(l -> new OrderLineItem(l.sellerSku(), l.qty()))
                    .toList();

            OrderResult result = orderService.placeOrder(items);
            if ("CONFIRMED".equalsIgnoreCase(result.status())) {
                String shopOrderId = "SO-" + result.orderId();
                log.info("Order {} ACCEPTED (shopOrderId={})", orderId, shopOrderId);

                TianggeOrderRecord record = new TianggeOrderRecord(
                        orderId,
                        event.eventId(),
                        "ACCEPTED",
                        shopOrderId,
                        "ACCEPTED",
                        linesJson,
                        Instant.now(),
                        Instant.now());
                repository.saveOrder(record);
                client.sendDecision(orderId,
                        new TianggeDecisionRequest("ACCEPTED", shopOrderId, "Order confirmed and stock reserved"));
                return;
            }
        }

        // Insufficient stock - check if incoming purchase order from supplier exists or
        // can be placed
        boolean anyShort = false;
        boolean allShortLinesCovered = true;
        for (TianggeOrderLine line : lines) {
            InventoryItem item = inventoryService.getItem(line.sellerSku());
            int currentStock = item != null ? item.stock() : 0;
            if (currentStock < line.qty()) {
                anyShort = true;
                if (!hasConfirmedOpenPurchaseOrder(line.sellerSku(), currentStock, line.qty())) {
                    allShortLinesCovered = false;
                }
            }
        }

        if (anyShort && allShortLinesCovered) {
            String boId = "BO-" + orderId;
            log.info("Order {} BACKORDERED awaiting supplier delivery", orderId);

            TianggeOrderRecord record = new TianggeOrderRecord(
                    orderId,
                    event.eventId(),
                    "BACKORDERED",
                    boId,
                    "BACKORDERED",
                    linesJson,
                    Instant.now(),
                    Instant.now());
            repository.saveOrder(record);
            client.sendDecision(orderId,
                    new TianggeDecisionRequest("BACKORDERED", boId, "Restock order currently in flight"));
        } else {
            String rejId = "REJ-" + orderId;
            log.info("Order {} REJECTED: insufficient stock and no incoming restock", orderId);

            TianggeOrderRecord record = new TianggeOrderRecord(
                    orderId,
                    event.eventId(),
                    "REJECTED",
                    rejId,
                    "REJECTED",
                    linesJson,
                    Instant.now(),
                    Instant.now());
            repository.saveOrder(record);
            client.sendDecision(orderId,
                    new TianggeDecisionRequest("REJECTED", rejId, "Item out of stock and unavailable"));
        }
    }

    private boolean hasConfirmedOpenPurchaseOrder(String productId, int currentStock, int requestedQuantity) {
        if (supplierGateway == null) {
            return false;
        }

        try {
            if (supplierGateway.hasIncomingStock(productId)) {
                return true;
            }

            int needed = Math.max(15, requestedQuantity - currentStock);
            var replenishment = supplierGateway.orderReplenishment(productId, needed);
            return replenishment != null
                    && replenishment.poNumber() != null
                    && !replenishment.poNumber().isBlank()
                    && switch (replenishment.status()) {
                        case PLACED, PICKING, SHIPPED -> true;
                        default -> false;
                    };
        } catch (Exception e) {
            log.warn("Could not confirm an open supplier purchase order for product {}: {}",
                    productId, e.getMessage());
            return false;
        }
    }

    private void processOrderCancelled(TianggeFeedEvent event) {
        String orderId = event.orderId();
        Optional<TianggeOrderRecord> existing = repository.findOrder(orderId);

        if (existing.isPresent()) {
            TianggeOrderRecord record = existing.get();
            if ("ACCEPTED".equalsIgnoreCase(record.status()) && record.shopOrderId() != null) {
                String shopIdStr = record.shopOrderId().replaceAll("[^0-9]", "");
                if (shopIdStr.isBlank()) {
                    throw new IllegalStateException("Invalid internal order ID for Tiangge order " + orderId);
                }
                long shopOrderId = Long.parseLong(shopIdStr);
                log.info("Cancelling shop order #{} for Tiangge cancellation {}", shopOrderId, orderId);
                try {
                    orderService.cancelOrder(shopOrderId);
                } catch (ResponseStatusException e) {
                    if (e.getStatusCode() != HttpStatus.CONFLICT) {
                        throw e;
                    }
                }
            } else if (!"CANCELLED".equalsIgnoreCase(record.status())) {
                throw new IllegalStateException("Cannot confirm Tiangge cancellation for unaccepted order " + orderId);
            }
            repository.updateOrderStatus(orderId, "CANCELLED");
        } else {
            throw new IllegalStateException("Tiangge cancellation arrived before its order was recorded: " + orderId);
        }

        // Confirm cancellation to Tiangge
        log.info("Confirming cancellation to Tiangge for order {}", orderId);
        client.sendCancellation(orderId, new TianggeCancellationRequest(true));
    }
}
