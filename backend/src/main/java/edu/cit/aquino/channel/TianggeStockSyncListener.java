package edu.cit.aquino.channel;

import edu.cit.aquino.inventory.InventoryItem;
import edu.cit.aquino.inventory.InventoryService;
import edu.cit.aquino.inventory.StockReplenishedEvent;
import edu.cit.aquino.shop.OrderCancelledEvent;
import edu.cit.aquino.shop.OrderPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
class TianggeStockSyncListener {
    private static final Logger log = LoggerFactory.getLogger(TianggeStockSyncListener.class);
    private static final long COALESCE_DELAY_MILLIS = 4000;
    private static final long RETRY_DELAY_MILLIS = 1000;

    private final InventoryService inventoryService;
    private final TianggeClient client;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final long coalesceDelayNanos;
    private final long retryDelayMillis;

    private final Object lock = new Object();
    private ScheduledFuture<?> pendingTask = null;
    private long firstPendingTriggerNanos;
    private long changeVersion;
    private boolean syncInProgress;

    TianggeStockSyncListener(InventoryService inventoryService, TianggeClient client) {
        this(inventoryService, client, COALESCE_DELAY_MILLIS, RETRY_DELAY_MILLIS);
    }

    TianggeStockSyncListener(
            InventoryService inventoryService,
            TianggeClient client,
            long coalesceDelayMillis,
            long retryDelayMillis
    ) {
        this.inventoryService = inventoryService;
        this.client = client;
        this.coalesceDelayNanos = TimeUnit.MILLISECONDS.toNanos(coalesceDelayMillis);
        this.retryDelayMillis = retryDelayMillis;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderPlaced(OrderPlacedEvent event) {
        log.info("Stock change detected from OrderPlacedEvent (Order #{}), triggering Tiangge stock sync", event.orderId());
        triggerSyncDebounced();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderCancelled(OrderCancelledEvent event) {
        log.info("Stock change detected from OrderCancelledEvent (Order #{}), triggering Tiangge stock sync", event.orderId());
        triggerSyncDebounced();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onStockReplenished(StockReplenishedEvent event) {
        log.info("Stock change detected from StockReplenishedEvent (Product {} +{}), triggering Tiangge stock sync",
                event.productId(), event.quantity());
        triggerSyncDebounced();
    }

    void triggerSyncDebounced() {
        synchronized (lock) {
            changeVersion++;
            if (firstPendingTriggerNanos == 0) {
                firstPendingTriggerNanos = System.nanoTime();
            }
            schedulePendingSyncLocked();
        }
    }

    void syncStockNow() {
        long versionAtStart;
        long firstTriggerAtStart;
        synchronized (lock) {
            pendingTask = null;
            syncInProgress = true;
            versionAtStart = changeVersion;
            firstTriggerAtStart = firstPendingTriggerNanos;
            firstPendingTriggerNanos = 0;
        }

        boolean succeeded = false;
        try {
            List<InventoryItem> items = inventoryService.getAllItems();
            if (items == null || items.isEmpty()) {
                throw new IllegalStateException("Cannot publish Tiangge stock because inventory is empty");
            }

            List<TianggeStockItem> stockList = items.stream()
                    .map(it -> new TianggeStockItem(it.productId(), Math.max(0, it.stock())))
                    .toList();

            log.info("Publishing event-driven stock update to Tiangge: {}", stockList);
            client.publishStock(stockList);
            succeeded = true;
        } catch (Exception e) {
            log.error("Failed to sync stock to Tiangge: {}", e.getMessage(), e);
        } finally {
            synchronized (lock) {
                syncInProgress = false;
                if (!succeeded) {
                    if (firstPendingTriggerNanos == 0 || firstTriggerAtStart < firstPendingTriggerNanos) {
                        firstPendingTriggerNanos = firstTriggerAtStart != 0
                                ? firstTriggerAtStart
                                : System.nanoTime();
                    }
                    scheduleRetryLocked();
                } else if (changeVersion == versionAtStart) {
                    firstPendingTriggerNanos = 0;
                } else {
                    schedulePendingSyncLocked();
                }
            }
        }
    }

    private void schedulePendingSyncLocked() {
        if (!syncInProgress && pendingTask == null && firstPendingTriggerNanos != 0) {
            long elapsedNanos = System.nanoTime() - firstPendingTriggerNanos;
            long remainingNanos = Math.max(0, coalesceDelayNanos - elapsedNanos);
            pendingTask = scheduler.schedule(this::syncStockNow, remainingNanos, TimeUnit.NANOSECONDS);
        }
    }

    private void scheduleRetryLocked() {
        if (pendingTask == null) {
            pendingTask = scheduler.schedule(this::syncStockNow, retryDelayMillis, TimeUnit.MILLISECONDS);
        }
    }
}
