package edu.cit.aquino.channel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cit.aquino.inventory.InventoryItem;
import edu.cit.aquino.inventory.InventoryService;
import edu.cit.aquino.inventory.StockReplenishedEvent;
import edu.cit.aquino.shop.OrderLineItem;
import edu.cit.aquino.shop.OrderResult;
import edu.cit.aquino.shop.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class TianggeBackorderManager {
    private static final Logger log = LoggerFactory.getLogger(TianggeBackorderManager.class);

    private final TianggeOrderRepository repository;
    private final TianggeClient client;
    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final ChannelService channelService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    TianggeBackorderManager(
            TianggeOrderRepository repository,
            TianggeClient client,
            OrderService orderService,
            InventoryService inventoryService,
            ChannelService channelService
    ) {
        this.repository = repository;
        this.client = client;
        this.orderService = orderService;
        this.inventoryService = inventoryService;
        this.channelService = channelService;
    }

    @EventListener
    @org.springframework.core.annotation.Order(2)
    public void onStockReplenished(StockReplenishedEvent event) {
        log.info("Checking backorders following supplier delivery for {} (+{} units)",
                event.productId(), event.quantity());
        try {
            TianggeContext.set(true);
            resolveBackorders(event.productId());
        } finally {
            TianggeContext.clear();
            channelService.syncStock();
        }
    }

    synchronized void resolveBackorders(String productId) {
        List<TianggeOrderRecord> backorders = repository.findBackorderedOrders();
        if (backorders.isEmpty()) {
            return;
        }

        for (TianggeOrderRecord record : backorders) {
            try {
                List<TianggeOrderLine> lines = objectMapper.readValue(
                        record.linesJson(),
                        new TypeReference<List<TianggeOrderLine>>() {}
                );

                boolean involvesProduct = lines.stream().anyMatch(l -> l.sellerSku().equalsIgnoreCase(productId));
                if (!involvesProduct) {
                    continue;
                }

                // Check if all lines can now be fulfilled
                boolean canFill = lines.stream().allMatch(l -> {
                    InventoryItem item = inventoryService.getItem(l.sellerSku());
                    return item != null && item.stock() >= l.qty();
                });

                if (canFill) {
                    List<OrderLineItem> orderItems = lines.stream()
                            .map(l -> new OrderLineItem(l.sellerSku(), l.qty()))
                            .toList();

                    OrderResult orderResult = orderService.placeOrder(orderItems);
                    if ("CONFIRMED".equalsIgnoreCase(orderResult.status())) {
                        String shopOrderId = "SO-" + orderResult.orderId();
                        log.info("Resolving backorder {} to ACCEPTED (shopOrderId={})", record.orderId(), shopOrderId);
                        client.sendResolution(record.orderId(), new TianggeResolutionRequest("ACCEPTED"));
                        repository.updateOrderDecision(record.orderId(), "ACCEPTED", shopOrderId, "RESOLVED_ACCEPTED");
                    }
                }
            } catch (Exception e) {
                log.error("Failed to process backorder resolution for {}: {}", record.orderId(), e.getMessage(), e);
            }
        }
    }
}
