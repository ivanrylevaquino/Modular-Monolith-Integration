package edu.cit.aquino.inventory;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InventoryServiceImplTest {
    private final InventoryRepository repository = mock(InventoryRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final InventoryServiceImpl service = new InventoryServiceImpl(repository, eventPublisher, 5);

    @Test
    void publishesLowStockEventWhenStockDropsBelowThreshold() {
        InventoryItem itemBefore = new InventoryItem("P200", "Mechanical Keyboard", 6);
        InventoryItem itemAfter = new InventoryItem("P200", "Mechanical Keyboard", 4);

        when(repository.findByProductId("P200"))
                .thenReturn(Optional.of(itemBefore))
                .thenReturn(Optional.of(itemAfter));
        when(repository.reserve("P200", 2)).thenReturn(true);

        InventoryItem result = service.reserve("P200", 2);

        assertEquals(4, result.stock());
        ArgumentCaptor<LowStockEvent> captor = ArgumentCaptor.forClass(LowStockEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        LowStockEvent event = captor.getValue();
        assertEquals("P200", event.productId());
        assertEquals(4, event.remainingStock());
        assertEquals(5, event.threshold());
    }

    @Test
    void doesNotPublishLowStockEventWhenStockRemainsAboveOrAtThreshold() {
        InventoryItem itemBefore = new InventoryItem("P100", "Wireless Mouse", 25);
        InventoryItem itemAfter = new InventoryItem("P100", "Wireless Mouse", 23);

        when(repository.findByProductId("P100"))
                .thenReturn(Optional.of(itemBefore))
                .thenReturn(Optional.of(itemAfter));
        when(repository.reserve("P100", 2)).thenReturn(true);

        InventoryItem result = service.reserve("P100", 2);

        assertEquals(23, result.stock());
        verify(eventPublisher, never()).publishEvent(any(LowStockEvent.class));
    }

    @Test
    void restocksItemSuccessfully() {
        InventoryItem itemBefore = new InventoryItem("P100", "Wireless Mouse", 20);
        InventoryItem itemAfter = new InventoryItem("P100", "Wireless Mouse", 25);

        when(repository.findByProductId("P100"))
                .thenReturn(Optional.of(itemBefore))
                .thenReturn(Optional.of(itemAfter));
        when(repository.restock("P100", 5)).thenReturn(true);

        InventoryItem result = service.restock("P100", 5);

        assertEquals(25, result.stock());
        verify(repository).restock("P100", 5);
    }
}
