package edu.cit.aquino.inventory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class InventoryRepository {
    private final JdbcTemplate jdbcTemplate;

    InventoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Optional<InventoryItem> findByProductId(String productId) {
        return jdbcTemplate.query(
                "SELECT product_id, name, stock FROM inventory WHERE product_id = ?",
                (rs, rowNum) -> new InventoryItem(
                        rs.getString("product_id"),
                        rs.getString("name"),
                        rs.getInt("stock")
                ),
                productId
        ).stream().findFirst();
    }

    boolean reserve(String productId, int quantity) {
        int updated = jdbcTemplate.update(
                "UPDATE inventory SET stock = stock - ? WHERE product_id = ? AND stock >= ?",
                quantity, productId, quantity
        );
        return updated == 1;
    }
}
