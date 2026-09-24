package edu.cit.aquino.inventory;

import java.util.List;

public interface InventoryService {
    InventoryItem getItem(String productId);
    List<InventoryItem> getAllItems();
    InventoryItem reserve(String productId, int quantity);
    InventoryItem restock(String productId, int quantity);
}
