package edu.cit.aquino.channel;

import edu.cit.aquino.inventory.InventoryItem;
import edu.cit.aquino.inventory.InventoryService;
import edu.cit.aquino.inventory.StockReplenishedEvent;
import edu.cit.aquino.shop.OrderCancelledEvent;
import edu.cit.aquino.shop.OrderPlacedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

class TianggeStockSyncListenerTest {
    private InventoryService inventoryService;
    private TianggeClient client;
    private TianggeStockSyncListener listener;

    @BeforeEach
    void setUp() {
        inventoryService = mock(InventoryService.class);
        client = mock(TianggeClient.class);
        listener = new TianggeStockSyncListener(inventoryService, client, 500, 600);
    }

    @Test
    void syncsStockDirectly() {
        when(inventoryService.getAllItems()).thenReturn(List.of(
                new InventoryItem("P100", "Mouse", 12),
                new InventoryItem("P200", "Keyboard", 5)
        ));

        listener.syncStockNow();

        verify(client).publishStock(argThat(list ->
                list.size() == 2 &&
                list.get(0).sellerSku().equals("P100") && list.get(0).available() == 12 &&
                list.get(1).sellerSku().equals("P200") && list.get(1).available() == 5
        ));
    }

    @Test
    void respondsToDomainEvents() throws InterruptedException {
        when(inventoryService.getAllItems()).thenReturn(List.of(
                new InventoryItem("P100", "Mouse", 10)
        ));

        listener.onOrderPlaced(new OrderPlacedEvent(1L, List.of()));
        listener.onOrderCancelled(new OrderCancelledEvent(1L, List.of()));
        listener.onStockReplenished(new StockReplenishedEvent("P100", 10));

        // Wait for debounce execution
        Thread.sleep(700);

        verify(client, atLeastOnce()).publishStock(anyList());
    }

    @Test
    void publishesWithinBoundedWindowDespiteContinuousTriggers() throws InterruptedException {
        when(inventoryService.getAllItems()).thenReturn(List.of(
                new InventoryItem("P100", "Mouse", 10)
        ));

        listener.onOrderPlaced(new OrderPlacedEvent(1L, List.of()));
        Thread.sleep(200);
        listener.onOrderCancelled(new OrderCancelledEvent(1L, List.of()));
        Thread.sleep(200);
        listener.onStockReplenished(new StockReplenishedEvent("P100", 5));

        verify(client, timeout(250).times(1)).publishStock(anyList());
    }

    @Test
    void organicTriggerDoesNotDelayAnAlreadyScheduledRetry() throws InterruptedException {
        when(inventoryService.getAllItems()).thenReturn(List.of(
                new InventoryItem("P100", "Mouse", 10)
        ));
        doThrow(new IllegalStateException("temporary Tiangge outage"))
                .doNothing()
                .when(client).publishStock(anyList());

        listener.syncStockNow();
        Thread.sleep(400);
        listener.onOrderPlaced(new OrderPlacedEvent(2L, List.of()));

        verify(client, timeout(350).times(2)).publishStock(anyList());
    }

    @Test
    void retriesInsteadOfTreatingEmptyInventoryAsSuccessfulSync() throws InterruptedException {
        when(inventoryService.getAllItems())
                .thenReturn(List.of())
                .thenReturn(List.of(new InventoryItem("P100", "Mouse", 10)));

        listener.onOrderPlaced(new OrderPlacedEvent(3L, List.of()));

        verify(client, timeout(2000)).publishStock(argThat(items ->
                items.size() == 1
                        && items.get(0).sellerSku().equals("P100")
                        && items.get(0).available() == 10
        ));
        verify(inventoryService, times(2)).getAllItems();
    }
}
