package edu.cit.aquino.notification;

import edu.cit.aquino.inventory.LowStockEvent;
import edu.cit.aquino.shop.OrderLineItem;
import edu.cit.aquino.shop.OrderPlacedEvent;
import edu.cit.aquino.shop.OrderRejectedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationEventListenerTest {
    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final NotificationEventListener listener = new NotificationEventListener(repository);

    @Test
    void logsConfirmedOrderEvent() {
        OrderPlacedEvent event = new OrderPlacedEvent(42L, List.of(new OrderLineItem("P100", 2)));
        listener.handleOrderPlaced(event);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(repository).save(captor.capture());
        assertTrue(captor.getValue().contains("Order #42 confirmed"));
    }

    @Test
    void logsRejectedOrderEvent() {
        OrderRejectedEvent event = new OrderRejectedEvent(43L, "Out of stock", List.of());
        listener.handleOrderRejected(event);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(repository).save(captor.capture());
        assertTrue(captor.getValue().contains("Order #43 rejected: Out of stock"));
    }

    @Test
    void logsLowStockEvent() {
        LowStockEvent event = new LowStockEvent("P200", "Mechanical Keyboard", 3, 5);
        listener.handleLowStock(event);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(repository).save(captor.capture());
        assertTrue(captor.getValue().contains("Low stock alert for P200"));
        assertTrue(captor.getValue().contains("reorder needed"));
    }
}
