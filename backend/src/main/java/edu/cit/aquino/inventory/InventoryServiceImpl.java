package edu.cit.aquino.inventory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class InventoryServiceImpl implements InventoryService {
    private final InventoryRepository repository;

    InventoryServiceImpl(InventoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public InventoryItem getItem(String productId) {
        return repository.findByProductId(productId).orElse(null);
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
            return getItem(productId);
        }
        return item;
    }
}
