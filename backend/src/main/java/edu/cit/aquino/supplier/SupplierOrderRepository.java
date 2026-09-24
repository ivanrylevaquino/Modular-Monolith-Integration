package edu.cit.aquino.supplier;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
class SupplierOrderRepository {
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<SupplierOrderRecord> rowMapper = new RowMapper<>() {
        @Override
        public SupplierOrderRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp created = rs.getTimestamp("created_at");
            Timestamp updated = rs.getTimestamp("updated_at");
            return new SupplierOrderRecord(
                    rs.getLong("id"),
                    rs.getString("product_id"),
                    rs.getString("buyer_ref"),
                    rs.getString("request_id"),
                    rs.getString("po_number"),
                    rs.getInt("cases"),
                    rs.getInt("units"),
                    SupplierOrderStatus.valueOf(rs.getString("status")),
                    created != null ? created.toInstant() : Instant.now(),
                    updated != null ? updated.toInstant() : Instant.now()
            );
        }
    };

    SupplierOrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    long nextOrderId() {
        Long next = jdbcTemplate.queryForObject("SELECT nextval('supplier_orders_id_seq')", Long.class);
        return next != null ? next : System.currentTimeMillis();
    }

    void insert(SupplierOrderRecord order) {
        jdbcTemplate.update(
                "INSERT INTO supplier_orders (id, product_id, buyer_ref, request_id, po_number, cases, units, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                order.id(),
                order.productId(),
                order.buyerRef(),
                order.requestId(),
                order.poNumber(),
                order.cases(),
                order.units(),
                order.status().name(),
                Timestamp.from(order.createdAt()),
                Timestamp.from(order.updatedAt())
        );
    }

    void updateStatusAndPo(long id, SupplierOrderStatus status, String poNumber) {
        jdbcTemplate.update(
                "UPDATE supplier_orders SET status = ?, po_number = ?, updated_at = NOW() WHERE id = ?",
                status.name(),
                poNumber,
                id
        );
    }

    void updateStatus(long id, SupplierOrderStatus status) {
        jdbcTemplate.update(
                "UPDATE supplier_orders SET status = ?, updated_at = NOW() WHERE id = ?",
                status.name(),
                id
        );
    }

    Optional<SupplierOrderRecord> findById(long id) {
        List<SupplierOrderRecord> list = jdbcTemplate.query(
                "SELECT * FROM supplier_orders WHERE id = ?",
                rowMapper,
                id
        );
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    Optional<SupplierOrderRecord> findByBuyerRef(String buyerRef) {
        List<SupplierOrderRecord> list = jdbcTemplate.query(
                "SELECT * FROM supplier_orders WHERE buyer_ref = ?",
                rowMapper,
                buyerRef
        );
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    List<SupplierOrderRecord> findPendingOrders() {
        return jdbcTemplate.query(
                "SELECT * FROM supplier_orders WHERE status = 'PENDING' ORDER BY id ASC",
                rowMapper
        );
    }

    List<SupplierOrderRecord> findActiveOrders(int limit) {
        return jdbcTemplate.query(
                "SELECT * FROM supplier_orders WHERE status IN ('PLACED', 'PICKING', 'SHIPPED') AND po_number IS NOT NULL ORDER BY updated_at ASC LIMIT ?",
                rowMapper,
                limit
        );
    }
}
