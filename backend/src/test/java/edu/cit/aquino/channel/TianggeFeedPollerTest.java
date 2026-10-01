package edu.cit.aquino.channel;

import edu.cit.aquino.inventory.InventoryItem;
import edu.cit.aquino.inventory.InventoryService;
import edu.cit.aquino.shop.OrderLineItem;
import edu.cit.aquino.shop.OrderResult;
import edu.cit.aquino.shop.OrderService;
import edu.cit.aquino.supplier.SupplierGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TianggeFeedPollerTest {
    private TianggeClient client;
    private TianggeOrderRepository repository;
    private OrderService orderService;
    private InventoryService inventoryService;
    private SupplierGateway supplierGateway;
    private TianggeFeedPoller poller;

    @BeforeEach
    void setUp() {
        client = mock(TianggeClient.class);
        repository = mock(TianggeOrderRepository.class);
        orderService = mock(OrderService.class);
        inventoryService = mock(InventoryService.class);
        supplierGateway = mock(SupplierGateway.class);

        poller = new TianggeFeedPoller(client, repository, orderService, inventoryService, supplierGateway);
    }

    @Test
    void acceptsOrderWhenInStock() {
        when(repository.getLastCursor()).thenReturn(10L);
        TianggeFeedEvent event = new TianggeFeedEvent(
                11L,
                "evt_11",
                "ORDER_PLACED",
                "TG-ORDER-1",
                "2026-10-01T00:00:00Z",
                "2026-10-01T00:01:00Z",
                null,
                null,
                List.of(new TianggeOrderLine("P100", 2)),
                new TianggeBuyer("Test Buyer", "Cebu")
        );
        when(client.getFeed(10L, 50)).thenReturn(new TianggeFeedResponse(List.of(event), 11L));
        when(repository.findOrder("TG-ORDER-1")).thenReturn(Optional.empty());

        when(inventoryService.getItem("P100")).thenReturn(new InventoryItem("P100", "Mouse", 10));
        when(orderService.placeOrder(anyList())).thenReturn(
                new OrderResult(501L, "CONFIRMED", "Confirmed", List.of(), List.of())
        );

        poller.pollFeed();

        verify(orderService).placeOrder(argThat(list -> list.size() == 1 && list.get(0).productId().equals("P100")));
        ArgumentCaptor<TianggeDecisionRequest> captor = ArgumentCaptor.forClass(TianggeDecisionRequest.class);
        verify(client).sendDecision(eq("TG-ORDER-1"), captor.capture());
        assertEquals("ACCEPTED", captor.getValue().decision());
        assertEquals("SO-501", captor.getValue().shopOrderId());
        verify(repository).updateLastCursor(11L);
    }

    @Test
    void skipsDuplicateOrder() {
        when(repository.getLastCursor()).thenReturn(10L);
        TianggeFeedEvent event = new TianggeFeedEvent(
                11L,
                "evt_11",
                "ORDER_PLACED",
                "TG-ORDER-1",
                "2026-10-01T00:00:00Z",
                "2026-10-01T00:01:00Z",
                null,
                null,
                List.of(new TianggeOrderLine("P100", 2)),
                null
        );
        when(client.getFeed(10L, 50)).thenReturn(new TianggeFeedResponse(List.of(event), 11L));
        TianggeOrderRecord existing = new TianggeOrderRecord(
                "TG-ORDER-1", "evt_11", "ACCEPTED", "SO-501", "ACCEPTED", "[]", null, null
        );
        when(repository.findOrder("TG-ORDER-1")).thenReturn(Optional.of(existing));

        poller.pollFeed();

        verify(orderService, never()).placeOrder(any());
        verify(repository).updateLastCursor(11L);
    }

    @Test
    void cancelsOrderAndConfirmsToTiangge() {
        when(repository.getLastCursor()).thenReturn(20L);
        TianggeFeedEvent event = new TianggeFeedEvent(
                21L,
                "evt_21",
                "ORDER_CANCELLED",
                "TG-ORDER-1",
                null,
                null,
                "2026-10-01T00:02:00Z",
                "2026-10-01T00:03:00Z",
                null,
                null
        );
        when(client.getFeed(20L, 50)).thenReturn(new TianggeFeedResponse(List.of(event), 21L));
        TianggeOrderRecord existing = new TianggeOrderRecord(
                "TG-ORDER-1", "evt_11", "ACCEPTED", "SO-501", "ACCEPTED", "[]", null, null
        );
        when(repository.findOrder("TG-ORDER-1")).thenReturn(Optional.of(existing));

        poller.pollFeed();

        verify(orderService).cancelOrder(501L);
        verify(repository).updateOrderStatus("TG-ORDER-1", "CANCELLED");
        verify(client).sendCancellation(eq("TG-ORDER-1"), argThat(r -> r.restocked()));
        verify(repository).updateLastCursor(21L);
    }

    @Test
    void answersBackorderedWhenStockLowAndRestockActive() {
        when(repository.getLastCursor()).thenReturn(30L);
        TianggeFeedEvent event = new TianggeFeedEvent(
                31L,
                "evt_31",
                "ORDER_PLACED",
                "TG-ORDER-2",
                "2026-10-01T00:00:00Z",
                "2026-10-01T00:01:00Z",
                null,
                null,
                List.of(new TianggeOrderLine("P200", 5)),
                null
        );
        when(client.getFeed(30L, 50)).thenReturn(new TianggeFeedResponse(List.of(event), 31L));
        when(repository.findOrder("TG-ORDER-2")).thenReturn(Optional.empty());

        when(inventoryService.getItem("P200")).thenReturn(new InventoryItem("P200", "Keyboard", 2));
        when(supplierGateway.hasIncomingStock("P200")).thenReturn(true);

        poller.pollFeed();

        verify(orderService, never()).placeOrder(any());
        ArgumentCaptor<TianggeDecisionRequest> captor = ArgumentCaptor.forClass(TianggeDecisionRequest.class);
        verify(client).sendDecision(eq("TG-ORDER-2"), captor.capture());
        assertEquals("BACKORDERED", captor.getValue().decision());
        verify(repository).updateLastCursor(31L);
    }
}
