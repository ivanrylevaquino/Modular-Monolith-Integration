package edu.cit.aquino.shop;

import edu.cit.aquino.inventory.InventoryItem;

import java.util.List;

public record OrderResult(
        Long orderId,
        String status,
        String reason,
        List<OrderItemOutcome> items,
        List<InventoryItem> inventory
) {}
