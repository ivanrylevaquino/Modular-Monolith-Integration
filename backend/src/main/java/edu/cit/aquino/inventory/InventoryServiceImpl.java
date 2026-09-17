package edu.cit.aquino.inventory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
class InventoryServiceImpl implements InventoryService {
    private final InventoryRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final int lowStockThreshold;

    InventoryServiceImpl(
            InventoryRepository repository,
            ApplicationEventPublisher eventPublisher,
            @Value("${inventory.low-stock-threshold:5}") int lowStockThreshold
    ) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.lowStockThreshold = lowStockThreshold;
    }

    @Override
    public InventoryItem getItem(String productId) {
        return repository.findByProductId(productId).orElse(null);
    }

    @Override
    public List<InventoryItem> getAllItems() {
        return repository.findAll();
    }

    @Override
    @Transactional
    public InventoryItem reserve(String productId, int quantity) {
        if (quantity <= 0) {
            return getItem(productId);
        }

        InventoryItem item = getItem(productId);
        if (item == null) {
            return null;
        }

        if (repository.reserve(productId, quantity)) {
            InventoryItem updated = getItem(productId);
            if (updated != null && updated.stock() < lowStockThreshold) {
                eventPublisher.publishEvent(new LowStockEvent(
                        updated.productId(),
                        updated.name(),
                        updated.stock(),
                        lowStockThreshold
                ));
            }
            return updated;
        }
        return item;
    }

    @Override
    @Transactional
    public InventoryItem restock(String productId, int quantity) {
        if (quantity <= 0) {
            return getItem(productId);
        }

        InventoryItem item = getItem(productId);
        if (item == null) {
            return null;
        }

        repository.restock(productId, quantity);
        return getItem(productId);
    }
}
