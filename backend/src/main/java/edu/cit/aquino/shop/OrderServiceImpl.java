package edu.cit.aquino.shop;

import edu.cit.aquino.inventory.InventoryItem;
import edu.cit.aquino.inventory.InventoryService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
class OrderServiceImpl implements OrderService {
    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    OrderServiceImpl(
            InventoryService inventoryService,
            OrderRepository orderRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public OrderResult placeOrder(List<OrderLineItem> items) {
        if (items == null || items.isEmpty()) {
            Long orderId = orderRepository.createOrder("REJECTED", "At least one line item is required.");
            OrderResult result = new OrderResult(orderId, "REJECTED", "At least one line item is required.", List.of(), List.of());
            eventPublisher.publishEvent(new OrderRejectedEvent(orderId, result.reason(), List.of()));
            return result;
        }

        // Validate basic inputs (quantities must be > 0)
        boolean hasInvalidQuantity = items.stream().anyMatch(item -> item.productId() == null || item.productId().isBlank() || item.quantity() <= 0);
        if (hasInvalidQuantity) {
            Long orderId = orderRepository.createOrder("REJECTED", "All items must have a valid product ID and quantity greater than zero.");
            List<OrderItemOutcome> outcomes = items.stream()
                    .map(it -> new OrderItemOutcome(it.productId(), it.quantity(), it.quantity() <= 0 ? "INVALID_QUANTITY" : "INVALID_PRODUCT"))
                    .toList();
            orderRepository.saveOrderItems(orderId, items);
            OrderResult result = new OrderResult(orderId, "REJECTED", "All items must have a valid product ID and quantity greater than zero.", outcomes, List.of());
            eventPublisher.publishEvent(new OrderRejectedEvent(orderId, result.reason(), items));
            return result;
        }

        // Aggregate requested quantities per product to correctly handle duplicates in the cart
        Map<String, Integer> requestedTotals = new LinkedHashMap<>();
        for (OrderLineItem item : items) {
            requestedTotals.merge(item.productId(), item.quantity(), Integer::sum);
        }

        // Pre-validate stock for every single product (All-or-Nothing)
        boolean allPassed = true;
        Map<String, InventoryItem> currentInventoryMap = new HashMap<>();
        Map<String, String> productStatusMap = new HashMap<>();

        for (Map.Entry<String, Integer> entry : requestedTotals.entrySet()) {
            String pid = entry.getKey();
            int totalReq = entry.getValue();
            InventoryItem current = inventoryService.getItem(pid);

            if (current == null) {
                allPassed = false;
                productStatusMap.put(pid, "NOT_FOUND");
            } else {
                currentInventoryMap.put(pid, current);
                if (current.stock() < totalReq) {
                    allPassed = false;
                    productStatusMap.put(pid, "INSUFFICIENT_STOCK");
                } else {
                    productStatusMap.put(pid, "AVAILABLE");
                }
            }
        }

        // If any item fails validation, REJECT entire order - no items are reserved!
        if (!allPassed) {
            List<OrderItemOutcome> outcomes = items.stream()
                    .map(it -> new OrderItemOutcome(it.productId(), it.quantity(), productStatusMap.getOrDefault(it.productId(), "INSUFFICIENT_STOCK")))
                    .toList();

            String reason = "Requested quantity exceeds available stock for one or more items.";
            Long orderId = orderRepository.createOrder("REJECTED", reason);
            orderRepository.saveOrderItems(orderId, items);

            List<InventoryItem> relevantInventory = new ArrayList<>(currentInventoryMap.values());
            OrderResult result = new OrderResult(orderId, "REJECTED", reason, outcomes, relevantInventory);
            eventPublisher.publishEvent(new OrderRejectedEvent(orderId, reason, items));
            return result;
        }

        // All items passed validation! Reserve each item sequentially
        List<OrderItemOutcome> outcomes = new ArrayList<>();
        List<InventoryItem> updatedInventory = new ArrayList<>();

        for (OrderLineItem item : items) {
            InventoryItem updated = inventoryService.reserve(item.productId(), item.quantity());
            outcomes.add(new OrderItemOutcome(item.productId(), item.quantity(), "RESERVED"));
            if (updated != null && updatedInventory.stream().noneMatch(i -> i.productId().equals(updated.productId()))) {
                updatedInventory.add(updated);
            }
        }

        String reason = "Order confirmed and inventory reserved.";
        Long orderId = orderRepository.createOrder("CONFIRMED", reason);
        orderRepository.saveOrderItems(orderId, items);

        OrderResult result = new OrderResult(orderId, "CONFIRMED", reason, outcomes, updatedInventory);
        eventPublisher.publishEvent(new OrderPlacedEvent(orderId, items));
        return result;
    }

    @Override
    public List<OrderResponse> getOrders() {
        return orderRepository.findAll();
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long orderId) {
        OrderRepository.OrderRecord order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order #" + orderId + " not found."));

        if ("CANCELLED".equalsIgnoreCase(order.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order #" + orderId + " is already CANCELLED.");
        }

        if ("REJECTED".equalsIgnoreCase(order.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot cancel order #" + orderId + " because it was REJECTED.");
        }

        List<OrderLineItem> items = orderRepository.findItemsByOrderId(orderId);
        for (OrderLineItem item : items) {
            inventoryService.restock(item.productId(), item.quantity());
        }

        orderRepository.updateStatus(orderId, "CANCELLED");

        return new OrderResponse(
                orderId,
                "CANCELLED",
                "Order cancelled and reserved stock restored.",
                order.createdAt(),
                items
        );
    }
}
