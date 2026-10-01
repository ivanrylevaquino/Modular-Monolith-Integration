package edu.cit.aquino.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

record TianggeOrderRecord(
        String orderId,
        String eventId,
        String decision,
        String shopOrderId,
        String status,
        String linesJson,
        Instant createdAt,
        Instant updatedAt
) {}

@Repository
class TianggeOrderRepository {
    private static final Logger log = LoggerFactory.getLogger(TianggeOrderRepository.class);
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<TianggeOrderRecord> orderRowMapper = new RowMapper<>() {
        @Override
        public TianggeOrderRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp created = rs.getTimestamp("created_at");
            Timestamp updated = rs.getTimestamp("updated_at");
            return new TianggeOrderRecord(
                    rs.getString("order_id"),
                    rs.getString("event_id"),
                    rs.getString("decision"),
                    rs.getString("shop_order_id"),
                    rs.getString("status"),
                    rs.getString("lines_json"),
                    created != null ? created.toInstant() : Instant.now(),
                    updated != null ? updated.toInstant() : Instant.now()
            );
        }
    };

    TianggeOrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void initSchema() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS tiangge_feed_state (
                    id INTEGER PRIMARY KEY,
                    last_cursor BIGINT NOT NULL DEFAULT 0,
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                );
            """);

            jdbcTemplate.execute("""
                INSERT INTO tiangge_feed_state (id, last_cursor, updated_at)
                VALUES (1, 0, NOW())
                ON CONFLICT (id) DO NOTHING;
            """);

            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS tiangge_orders (
                    order_id VARCHAR(50) PRIMARY KEY,
                    event_id VARCHAR(100),
                    decision VARCHAR(30),
                    shop_order_id VARCHAR(50),
                    status VARCHAR(30) NOT NULL,
                    lines_json TEXT NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                );
            """);
            log.info("Initialized Tiangge channel database tables successfully");
        } catch (Exception e) {
            log.warn("Database init warning (may already exist or using remote DB): {}", e.getMessage());
        }
    }

    long getLastCursor() {
        try {
            Long cursor = jdbcTemplate.queryForObject(
                    "SELECT last_cursor FROM tiangge_feed_state WHERE id = 1",
                    Long.class
            );
            return cursor != null ? cursor : 0L;
        } catch (Exception e) {
            log.warn("Could not read feed cursor, falling back to 0: {}", e.getMessage());
            return 0L;
        }
    }

    void updateLastCursor(long cursor) {
        try {
            jdbcTemplate.update(
                    "UPDATE tiangge_feed_state SET last_cursor = ?, updated_at = NOW() WHERE id = 1",
                    cursor
            );
        } catch (Exception e) {
            log.warn("Failed to update last cursor to {}: {}", cursor, e.getMessage());
        }
    }

    Optional<TianggeOrderRecord> findOrder(String orderId) {
        try {
            List<TianggeOrderRecord> list = jdbcTemplate.query(
                    "SELECT * FROM tiangge_orders WHERE order_id = ?",
                    orderRowMapper,
                    orderId
            );
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        } catch (Exception e) {
            log.warn("Error querying order {}: {}", orderId, e.getMessage());
            return Optional.empty();
        }
    }

    void saveOrder(TianggeOrderRecord order) {
        jdbcTemplate.update("""
            INSERT INTO tiangge_orders (order_id, event_id, decision, shop_order_id, status, lines_json, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (order_id) DO UPDATE
            SET decision = EXCLUDED.decision,
                shop_order_id = EXCLUDED.shop_order_id,
                status = EXCLUDED.status,
                updated_at = NOW();
        """,
                order.orderId(),
                order.eventId(),
                order.decision(),
                order.shopOrderId(),
                order.status(),
                order.linesJson(),
                Timestamp.from(order.createdAt()),
                Timestamp.from(order.updatedAt())
        );
    }

    void updateOrderDecision(String orderId, String decision, String shopOrderId, String status) {
        jdbcTemplate.update(
                "UPDATE tiangge_orders SET decision = ?, shop_order_id = ?, status = ?, updated_at = NOW() WHERE order_id = ?",
                decision,
                shopOrderId,
                status,
                orderId
        );
    }

    void updateOrderStatus(String orderId, String status) {
        jdbcTemplate.update(
                "UPDATE tiangge_orders SET status = ?, updated_at = NOW() WHERE order_id = ?",
                status,
                orderId
        );
    }

    List<TianggeOrderRecord> findBackorderedOrders() {
        return jdbcTemplate.query(
                "SELECT * FROM tiangge_orders WHERE status = 'BACKORDERED' ORDER BY created_at ASC",
                orderRowMapper
        );
    }
}
