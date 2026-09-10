package edu.cit.aquino.shop;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class OrderRepository {
    private final JdbcTemplate jdbcTemplate;

    OrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void save(String productId, int quantity, String status, String reason) {
        jdbcTemplate.update(
                "INSERT INTO orders (product_id, quantity, status, reason) VALUES (?, ?, ?, ?)",
                productId, quantity, status, reason
        );
    }
}
