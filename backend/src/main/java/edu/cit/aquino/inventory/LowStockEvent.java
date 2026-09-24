package edu.cit.aquino.inventory;

public record LowStockEvent(String productId, String productName, int remainingStock, int threshold) {}
