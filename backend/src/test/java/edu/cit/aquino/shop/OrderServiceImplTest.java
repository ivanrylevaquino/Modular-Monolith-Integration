package edu.cit.aquino.shop;

import edu.cit.aquino.inventory.InventoryItem;
import edu.cit.aquino.inventory.InventoryService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderServiceImplTest {
    private final InventoryService inventory = Mockito.mock(InventoryService.class);
    private final OrderRepository orders = Mockito.mock(OrderRepository.class);
    private final OrderServiceImpl service = new OrderServiceImpl(inventory, orders);

    @Test
    void confirmsWhenInventoryIsReserved() {
        InventoryItem before = new InventoryItem("P100", "Wireless Mouse", 25);
        InventoryItem after = new InventoryItem("P100", "Wireless Mouse", 23);
        Mockito.when(inventory.getItem("P100")).thenReturn(before);
        Mockito.when(inventory.reserve("P100", 2)).thenReturn(after);

        OrderResult result = service.placeOrder("P100", 2);

        assertEquals("CONFIRMED", result.status());
        assertEquals(23, result.inventory().stock());
        Mockito.verify(orders).save("P100", 2, "CONFIRMED", "Order confirmed and inventory reserved.");
    }

    @Test
    void rejectsWhenQuantityExceedsStock() {
        InventoryItem before = new InventoryItem("P200", "Mechanical Keyboard", 10);
        Mockito.when(inventory.getItem("P200")).thenReturn(before);
        Mockito.when(inventory.reserve("P200", 11)).thenReturn(before);

        OrderResult result = service.placeOrder("P200", 11);

        assertEquals("REJECTED", result.status());
        assertEquals("Requested quantity exceeds available stock.", result.reason());
        Mockito.verify(orders).save("P200", 11, "REJECTED", "Requested quantity exceeds available stock.");
    }
}
