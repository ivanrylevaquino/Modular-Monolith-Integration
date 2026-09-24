package edu.cit.aquino.inventory;

public record StockReplenishedEvent(String productId, int quantity) {}
