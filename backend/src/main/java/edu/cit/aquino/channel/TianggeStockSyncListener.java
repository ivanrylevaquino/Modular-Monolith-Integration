package edu.cit.aquino.channel;

import edu.cit.aquino.inventory.InventoryItem;
import edu.cit.aquino.inventory.InventoryService;
import edu.cit.aquino.inventory.StockReplenishedEvent;
import edu.cit.aquino.shop.OrderCancelledEvent;
import edu.cit.aquino.shop.OrderPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
class TianggeStockSyncListener {
    private static final Logger log = LoggerFactory.getLogger(TianggeStockSyncListener.class);

    private final InventoryService inventoryService;
    private final TianggeClient client;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private final Object lock = new Object();
    private ScheduledFuture<?> pendingTask = null;

    TianggeStockSyncListener(InventoryService inventoryService, TianggeClient client) {
        this.inventoryService = inventoryService;
        this.client = client;
    }

    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        log.info("Stock change detected from OrderPlacedEvent (Order #{}), triggering Tiangge stock sync", event.orderId());
        triggerSyncDebounced();
    }

    @EventListener
    public void onOrderCancelled(OrderCancelledEvent event) {
        log.info("Stock change detected from OrderCancelledEvent (Order #{}), triggering Tiangge stock sync", event.orderId());
        triggerSyncDebounced();
    }

    @EventListener
    public void onStockReplenished(StockReplenishedEvent event) {
        log.info("Stock change detected from StockReplenishedEvent (Product {} +{}), triggering Tiangge stock sync",
                event.productId(), event.quantity());
        triggerSyncDebounced();
    }

    void triggerSyncDebounced() {
        synchronized (lock) {
            if (pendingTask != null && !pendingTask.isDone()) {
                pendingTask.cancel(false);
            }
            // Debounce for 500ms to consolidate rapid bursts (flash sales) while well within 30s deadline
            pendingTask = scheduler.schedule(this::syncStockNow, 500, TimeUnit.MILLISECONDS);
        }
    }

    void syncStockNow() {
        try {
            List<InventoryItem> items = inventoryService.getAllItems();
            if (items == null || items.isEmpty()) {
                return;
            }

            List<TianggeStockItem> stockList = items.stream()
                    .map(it -> new TianggeStockItem(it.productId(), Math.max(0, it.stock())))
                    .toList();

            log.info("Publishing event-driven stock update to Tiangge: {}", stockList);
            client.publishStock(stockList);
        } catch (Exception e) {
            log.error("Failed to sync stock to Tiangge: {}", e.getMessage(), e);
        }
    }
}
