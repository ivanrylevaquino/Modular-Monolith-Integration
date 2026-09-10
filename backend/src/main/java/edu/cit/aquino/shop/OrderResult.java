package edu.cit.aquino.shop;

import edu.cit.aquino.inventory.InventoryItem;

public record OrderResult(String status, String reason, InventoryItem inventory) {}
