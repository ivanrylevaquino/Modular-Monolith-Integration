package edu.cit.aquino.channel;

import edu.cit.aquino.inventory.InventoryItem;
import edu.cit.aquino.inventory.InventoryService;
import edu.cit.aquino.shop.OrderLineItem;
import edu.cit.aquino.shop.OrderResult;
import edu.cit.aquino.shop.OrderService;
import edu.cit.aquino.supplier.SupplierGateway;
import edu.cit.aquino.supplier.SupplierOrderResult;
import edu.cit.aquino.supplier.SupplierOrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
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
    private TianggeStockSyncListener stockSyncListener;
    private TianggeFeedPoller poller;

    @BeforeEach
    void setUp() {
        client = mock(TianggeClient.class);
        repository = mock(TianggeOrderRepository.class);
        orderService = mock(OrderService.class);
        inventoryService = mock(InventoryService.class);
        supplierGateway = mock(SupplierGateway.class);
        stockSyncListener = mock(TianggeStockSyncListener.class);

        poller = new TianggeFeedPoller(
                client, repository, orderService, inventoryService, stockSyncListener, supplierGateway
        );
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
        verify(stockSyncListener).triggerSyncDebounced();
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
        verify(stockSyncListener).triggerSyncDebounced();
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

    @Test
    void rejectsBackorderWhenAnyShortLineHasNoConfirmedOpenPurchaseOrder() {
        when(repository.getLastCursor()).thenReturn(35L);
        TianggeFeedEvent event = new TianggeFeedEvent(
                36L, "evt_36", "ORDER_PLACED", "TG-MULTI-LINE",
                "2026-10-01T00:00:00Z", "2026-10-01T00:01:00Z",
                null, null,
                List.of(new TianggeOrderLine("P100", 2), new TianggeOrderLine("P200", 3)),
                null
        );
        when(client.getFeed(35L, 50)).thenReturn(new TianggeFeedResponse(List.of(event), 36L));
        when(repository.findOrder("TG-MULTI-LINE")).thenReturn(Optional.empty());
        when(inventoryService.getItem("P100")).thenReturn(new InventoryItem("P100", "Mouse", 0));
        when(inventoryService.getItem("P200")).thenReturn(new InventoryItem("P200", "Keyboard", 0));
        when(supplierGateway.hasIncomingStock("P100")).thenReturn(true);
        when(supplierGateway.hasIncomingStock("P200")).thenReturn(false);
        when(supplierGateway.orderReplenishment("P200", 15))
                .thenThrow(new IllegalStateException("supplier unavailable"));

        poller.pollFeed();

        ArgumentCaptor<TianggeDecisionRequest> captor = ArgumentCaptor.forClass(TianggeDecisionRequest.class);
        verify(client).sendDecision(eq("TG-MULTI-LINE"), captor.capture());
        assertEquals("REJECTED", captor.getValue().decision());
        verify(supplierGateway, never()).orderReplenishment("P100", 15);
    }

    @Test
    void rejectsBackorderWhenReplenishmentRequestRemainsPending() {
        when(repository.getLastCursor()).thenReturn(37L);
        TianggeFeedEvent event = new TianggeFeedEvent(
                38L, "evt_38", "ORDER_PLACED", "TG-PENDING-PO",
                "2026-10-01T00:00:00Z", "2026-10-01T00:01:00Z",
                null, null, List.of(new TianggeOrderLine("P300", 2)), null
        );
        when(client.getFeed(37L, 50)).thenReturn(new TianggeFeedResponse(List.of(event), 38L));
        when(repository.findOrder("TG-PENDING-PO")).thenReturn(Optional.empty());
        when(inventoryService.getItem("P300")).thenReturn(new InventoryItem("P300", "Hub", 0));
        when(supplierGateway.hasIncomingStock("P300")).thenReturn(false);
        when(supplierGateway.orderReplenishment("P300", 15)).thenReturn(new SupplierOrderResult(
                102L, "P300", "RO-102", "REQ-102", null, 1, 20,
                SupplierOrderStatus.PENDING, "not placed yet", Instant.now()
        ));

        poller.pollFeed();

        verify(client).sendDecision(eq("TG-PENDING-PO"), argThat(request -> "REJECTED".equals(request.decision())));
    }

    @Test
    void doesNotAdvanceCursorWhenOrderProcessingFails() {
        when(repository.getLastCursor()).thenReturn(40L);
        TianggeFeedEvent event = new TianggeFeedEvent(
                41L,
                "evt_41",
                "ORDER_PLACED",
                "TG-ORDER-3",
                "2026-10-01T00:00:00Z",
                "2026-10-01T00:01:00Z",
                null,
                null,
                List.of(new TianggeOrderLine("P100", 2)),
                null
        );
        when(client.getFeed(40L, 50)).thenReturn(new TianggeFeedResponse(List.of(event), 41L));
        when(repository.findOrder("TG-ORDER-3")).thenReturn(Optional.empty());
        when(inventoryService.getItem("P100")).thenReturn(new InventoryItem("P100", "Mouse", 10));
        when(orderService.placeOrder(anyList())).thenThrow(new IllegalStateException("database unavailable"));

        poller.pollFeed();

        verify(repository, never()).updateLastCursor(anyLong());
        verify(client, never()).sendDecision(anyString(), any());
    }

    @Test
    void doesNotAdvanceCursorWhenFeedRequestFails() {
        when(repository.getLastCursor()).thenReturn(50L);
        when(client.getFeed(50L, 50)).thenThrow(new IllegalStateException("Tiangge unavailable"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, poller::pollFeed);

        verify(repository, never()).updateLastCursor(anyLong());
    }

    @Test
    void retriesStockPublicationAfterCancellationEventWhenFirstPublishFails() {
        when(inventoryService.getAllItems()).thenReturn(List.of(
                new InventoryItem("P100", "Mouse", 4)
        ));
        doThrow(new IllegalStateException("Tiangge unavailable"))
                .doNothing()
                .when(client).publishStock(anyList());
        TianggeStockSyncListener stockSyncListener = new TianggeStockSyncListener(inventoryService, client, 100, 100);

        stockSyncListener.onOrderCancelled(new edu.cit.aquino.shop.OrderCancelledEvent(501L, List.of()));

        verify(client, timeout(1000).times(2)).publishStock(anyList());
    }
}
