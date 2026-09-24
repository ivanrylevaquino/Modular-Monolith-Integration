package edu.cit.aquino.inventory;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class InventoryDeliveryListener {
    private final InventoryService inventoryService;

    InventoryDeliveryListener(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @EventListener
    public void onStockReplenished(StockReplenishedEvent event) {
        inventoryService.restock(event.productId(), event.quantity());
    }
}
