package edu.cit.aquino.channel;

import edu.cit.aquino.inventory.InventoryItem;
import edu.cit.aquino.inventory.InventoryService;
import edu.cit.aquino.inventory.StockReplenishedEvent;
import edu.cit.aquino.shop.OrderResult;
import edu.cit.aquino.shop.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TianggeBackorderManagerTest {
    private TianggeOrderRepository repository;
    private TianggeClient client;
    private OrderService orderService;
    private InventoryService inventoryService;
    private ChannelService channelService;
    private TianggeBackorderManager manager;

    @BeforeEach
    void setUp() {
        repository = mock(TianggeOrderRepository.class);
        client = mock(TianggeClient.class);
        orderService = mock(OrderService.class);
        inventoryService = mock(InventoryService.class);
        channelService = mock(ChannelService.class);
        manager = new TianggeBackorderManager(repository, client, orderService, inventoryService, channelService);
    }

    @Test
    void resolvesBackorderWhenStockReplenished() {
        TianggeOrderRecord backorder = new TianggeOrderRecord(
                "TG-BO-1",
                "evt_1",
                "BACKORDERED",
                "BO-TG-BO-1",
                "BACKORDERED",
                "[{\"sellerSku\":\"P300\",\"qty\":2}]",
                Instant.now(),
                Instant.now()
        );

        when(repository.findBackorderedOrders()).thenReturn(List.of(backorder));
        when(inventoryService.getItem("P300")).thenReturn(new InventoryItem("P300", "Hub", 5));
        when(orderService.placeOrder(anyList())).thenReturn(
                new OrderResult(701L, "CONFIRMED", "Confirmed", List.of(), List.of())
        );

        manager.onStockReplenished(new StockReplenishedEvent("P300", 20));

        verify(orderService).placeOrder(argThat(items -> items.size() == 1 && items.get(0).productId().equals("P300") && items.get(0).quantity() == 2));
        verify(client).sendResolution(eq("TG-BO-1"), argThat(req -> "ACCEPTED".equals(req.status())));
        verify(repository).updateOrderDecision("TG-BO-1", "ACCEPTED", "SO-701", "RESOLVED_ACCEPTED");
        verify(channelService).syncStock();
    }
}
