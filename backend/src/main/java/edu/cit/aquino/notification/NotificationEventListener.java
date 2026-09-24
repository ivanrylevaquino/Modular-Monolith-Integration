package edu.cit.aquino.notification;

import edu.cit.aquino.inventory.LowStockEvent;
import edu.cit.aquino.shop.OrderPlacedEvent;
import edu.cit.aquino.shop.OrderRejectedEvent;
import edu.cit.aquino.supplier.SupplierGateway;
import edu.cit.aquino.supplier.SupplierOrderResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class NotificationEventListener {
    private final NotificationRepository notificationRepository;
    private final SupplierGateway supplierGateway;

    NotificationEventListener(NotificationRepository notificationRepository) {
        this(notificationRepository, null);
    }

    @Autowired
    NotificationEventListener(NotificationRepository notificationRepository, @Autowired(required = false) SupplierGateway supplierGateway) {
        this.notificationRepository = notificationRepository;
        this.supplierGateway = supplierGateway;
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
        if (supplierGateway != null) {
            int unitsNeeded = 15;
            SupplierOrderResult result = supplierGateway.orderReplenishment(event.productId(), unitsNeeded);
            String msg = String.format("Low stock alert for %s (%s): %d remaining (threshold: %d) - reorder needed (PO: %s, %d units)",
                    event.productId(), event.productName(), event.remainingStock(), event.threshold(),
                    result.poNumber() != null ? result.poNumber() : "PENDING", result.units());
            notificationRepository.save(msg);
        } else {
            String msg = String.format("Low stock alert for %s (%s): %d remaining (threshold: %d) - reorder needed",
                    event.productId(), event.productName(), event.remainingStock(), event.threshold());
            notificationRepository.save(msg);
        }
    }
}
