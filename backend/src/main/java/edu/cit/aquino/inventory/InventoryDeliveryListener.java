package edu.cit.aquino.inventory;

import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
class InventoryDeliveryListener {
    private final InventoryService inventoryService;

    InventoryDeliveryListener(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @EventListener
    @Order(1)
    public void onStockReplenished(StockReplenishedEvent event) {
        inventoryService.restock(event.productId(), event.quantity());
    }
}
