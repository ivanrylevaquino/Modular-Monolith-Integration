package edu.cit.aquino.notification;

import edu.cit.aquino.inventory.LowStockEvent;
import edu.cit.aquino.shop.OrderPlacedEvent;
import edu.cit.aquino.shop.OrderRejectedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class NotificationEventListener {
    private final NotificationRepository notificationRepository;

    NotificationEventListener(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @EventListener
    public void handleOrderPlaced(OrderPlacedEvent event) {
        int totalItems = event.items().stream().mapToInt(it -> it.quantity()).sum();
        String msg = String.format("Order #%d confirmed: %d item(s) reserved successfully.", event.orderId(), totalItems);
        notificationRepository.save(msg);
    }

    @EventListener
    public void handleOrderRejected(OrderRejectedEvent event) {
        String msg = String.format("Order #%d rejected: %s", event.orderId(), event.reason());
        notificationRepository.save(msg);
    }

    @EventListener
    public void handleLowStock(LowStockEvent event) {
        String msg = String.format("Low stock alert for %s (%s): %d remaining (threshold: %d) - reorder needed",
                event.productId(), event.productName(), event.remainingStock(), event.threshold());
        notificationRepository.save(msg);
    }
}
