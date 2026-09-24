package edu.cit.aquino.shop;

import edu.cit.aquino.inventory.InventoryItem;
import edu.cit.aquino.inventory.InventoryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderServiceImplTest {
    private final InventoryService inventory = mock(InventoryService.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final OrderServiceImpl service = new OrderServiceImpl(inventory, orders, eventPublisher);

    @Test
    void confirmsMultiItemOrderWhenAllItemsAvailable() {
        InventoryItem p100Before = new InventoryItem("P100", "Wireless Mouse", 25);
        InventoryItem p100After = new InventoryItem("P100", "Wireless Mouse", 23);
        InventoryItem p200Before = new InventoryItem("P200", "Mechanical Keyboard", 10);
        InventoryItem p200After = new InventoryItem("P200", "Mechanical Keyboard", 9);

        when(inventory.getItem("P100")).thenReturn(p100Before);
        when(inventory.getItem("P200")).thenReturn(p200Before);
        when(inventory.reserve("P100", 2)).thenReturn(p100After);
        when(inventory.reserve("P200", 1)).thenReturn(p200After);
        when(orders.createOrder(eq("CONFIRMED"), anyString())).thenReturn(101L);

        List<OrderLineItem> items = List.of(
                new OrderLineItem("P100", 2),
                new OrderLineItem("P200", 1)
        );

        OrderResult result = service.placeOrder(items);

        assertEquals("CONFIRMED", result.status());
        assertEquals(101L, result.orderId());
        assertEquals(2, result.items().size());
        assertEquals("RESERVED", result.items().get(0).outcome());
        assertEquals("RESERVED", result.items().get(1).outcome());

        verify(inventory).reserve("P100", 2);
        verify(inventory).reserve("P200", 1);
        verify(orders).createOrder(eq("CONFIRMED"), anyString());
        verify(orders).saveOrderItems(eq(101L), eq(items));
        verify(eventPublisher).publishEvent(any(OrderPlacedEvent.class));
    }

    @Test
    void rejectsMultiItemOrderWithZeroPartialReservationsWhenOneItemExceedsStock() {
        InventoryItem p100 = new InventoryItem("P100", "Wireless Mouse", 25);
        InventoryItem p300 = new InventoryItem("P300", "USB-C Hub", 0);

        when(inventory.getItem("P100")).thenReturn(p100);
        when(inventory.getItem("P300")).thenReturn(p300);
        when(orders.createOrder(eq("REJECTED"), anyString())).thenReturn(102L);

        List<OrderLineItem> items = List.of(
                new OrderLineItem("P100", 2),
                new OrderLineItem("P300", 1)
        );

        OrderResult result = service.placeOrder(items);

        assertEquals("REJECTED", result.status());
        assertEquals(102L, result.orderId());

        // CRITICAL: All-or-nothing rollback check - reserve() must NEVER have been called on any product
        verify(inventory, never()).reserve(anyString(), anyInt());
        verify(orders).createOrder(eq("REJECTED"), contains("exceeds available stock"));
        verify(orders).saveOrderItems(eq(102L), eq(items));
        verify(eventPublisher).publishEvent(any(OrderRejectedEvent.class));
    }

    @Test
    void cancelsConfirmedOrderAndRestocksAllItems() {
        Long orderId = 201L;
        OrderRepository.OrderRecord record = new OrderRepository.OrderRecord(orderId, "CONFIRMED", "Order confirmed", OffsetDateTime.now());
        List<OrderLineItem> items = List.of(
                new OrderLineItem("P100", 2),
                new OrderLineItem("P200", 1)
        );

        when(orders.findById(orderId)).thenReturn(Optional.of(record));
        when(orders.findItemsByOrderId(orderId)).thenReturn(items);
        when(orders.updateStatus(orderId, "CANCELLED")).thenReturn(true);

        OrderResponse response = service.cancelOrder(orderId);

        assertEquals("CANCELLED", response.status());
        verify(inventory).restock("P100", 2);
        verify(inventory).restock("P200", 1);
        verify(orders).updateStatus(orderId, "CANCELLED");
    }

    @Test
    void rejectsCancellingAlreadyCancelledOrderWithConflict() {
        Long orderId = 202L;
        OrderRepository.OrderRecord record = new OrderRepository.OrderRecord(orderId, "CANCELLED", "Cancelled", OffsetDateTime.now());
        when(orders.findById(orderId)).thenReturn(Optional.of(record));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.cancelOrder(orderId));
        assertEquals(409, ex.getStatusCode().value());
        verify(inventory, never()).restock(anyString(), anyInt());
    }

    @Test
    void rejectsCancellingNonexistentOrderWithNotFound() {
        Long orderId = 999L;
        when(orders.findById(orderId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.cancelOrder(orderId));
        assertEquals(404, ex.getStatusCode().value());
        verify(inventory, never()).restock(anyString(), anyInt());
    }
}
