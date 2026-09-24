package edu.cit.aquino.shop;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

@Repository
class OrderRepository {
    private final JdbcTemplate jdbcTemplate;

    OrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Long createOrder(String status, String reason) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO orders (status, reason) VALUES (?, ?) RETURNING order_id",
                Long.class,
                status,
                reason
        );
    }

    void saveOrderItems(Long orderId, List<OrderLineItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (OrderLineItem item : items) {
            jdbcTemplate.update(
                    "INSERT INTO order_items (order_id, product_id, quantity) VALUES (?, ?, ?)",
                    orderId,
                    item.productId(),
                    item.quantity()
            );
        }
    }

    Optional<OrderRecord> findById(Long orderId) {
        List<OrderRecord> list = jdbcTemplate.query(
                "SELECT order_id, status, reason, created_at FROM orders WHERE order_id = ?",
                (rs, rowNum) -> {
                    Timestamp ts = rs.getTimestamp("created_at");
                    OffsetDateTime odt = ts != null ? ts.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime() : null;
                    return new OrderRecord(
                            rs.getLong("order_id"),
                            rs.getString("status"),
                            rs.getString("reason"),
                            odt
                    );
                },
                orderId
        );
        return list.stream().findFirst();
    }

    List<OrderLineItem> findItemsByOrderId(Long orderId) {
        return jdbcTemplate.query(
                "SELECT product_id, quantity FROM order_items WHERE order_id = ? ORDER BY item_id ASC",
                (rs, rowNum) -> new OrderLineItem(
                        rs.getString("product_id"),
                        rs.getInt("quantity")
                ),
                orderId
        );
    }

    List<OrderResponse> findAll() {
        List<OrderRecord> orders = jdbcTemplate.query(
                "SELECT order_id, status, reason, created_at FROM orders ORDER BY order_id DESC",
                (rs, rowNum) -> {
                    Timestamp ts = rs.getTimestamp("created_at");
                    OffsetDateTime odt = ts != null ? ts.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime() : null;
                    return new OrderRecord(
                            rs.getLong("order_id"),
                            rs.getString("status"),
                            rs.getString("reason"),
                            odt
                    );
                }
        );

        List<OrderResponse> result = new ArrayList<>();
        for (OrderRecord order : orders) {
            List<OrderLineItem> items = findItemsByOrderId(order.orderId());
            result.add(new OrderResponse(
                    order.orderId(),
                    order.status(),
                    order.reason(),
                    order.createdAt(),
                    items
            ));
        }
        return result;
    }

    boolean updateStatus(Long orderId, String status) {
        int updated = jdbcTemplate.update(
                "UPDATE orders SET status = ? WHERE order_id = ?",
                status,
                orderId
        );
        return updated == 1;
    }

    record OrderRecord(Long orderId, String status, String reason, OffsetDateTime createdAt) {}
}
