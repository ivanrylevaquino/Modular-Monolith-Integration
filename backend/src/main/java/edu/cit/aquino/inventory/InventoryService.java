package edu.cit.aquino.inventory;

public interface InventoryService {
    InventoryItem getItem(String productId);
    InventoryItem reserve(String productId, int quantity);
}
