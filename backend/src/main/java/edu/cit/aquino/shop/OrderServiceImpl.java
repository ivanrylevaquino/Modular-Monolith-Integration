package edu.cit.aquino.shop;

import edu.cit.aquino.inventory.InventoryItem;
import edu.cit.aquino.inventory.InventoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OrderServiceImpl implements OrderService {
    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;

    OrderServiceImpl(InventoryService inventoryService, OrderRepository orderRepository) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
    }

    @Override
    @Transactional
    public OrderResult placeOrder(String productId, int quantity) {
        if (productId == null || productId.isBlank()) {
            OrderResult result = new OrderResult("REJECTED", "Product ID is required.", null);
            orderRepository.save(productId, quantity, result.status(), result.reason());
            return result;
        }

        if (quantity <= 0) {
            InventoryItem item = inventoryService.getItem(productId);
            OrderResult result = new OrderResult("REJECTED", "Quantity must be greater than zero.", item);
            orderRepository.save(productId, quantity, result.status(), result.reason());
            return result;
        }

        InventoryItem before = inventoryService.getItem(productId);
        if (before == null) {
            OrderResult result = new OrderResult("REJECTED", "Product not found.", null);
            orderRepository.save(productId, quantity, result.status(), result.reason());
            return result;
        }

        InventoryItem after = inventoryService.reserve(productId, quantity);
        if (after != null && after.stock() == before.stock() - quantity) {
            OrderResult result = new OrderResult("CONFIRMED", "Order confirmed and inventory reserved.", after);
            orderRepository.save(productId, quantity, result.status(), result.reason());
            return result;
        }

        OrderResult result = new OrderResult(
                "REJECTED",
                "Requested quantity exceeds available stock.",
                before
        );
        orderRepository.save(productId, quantity, result.status(), result.reason());
        return result;
    }
}
