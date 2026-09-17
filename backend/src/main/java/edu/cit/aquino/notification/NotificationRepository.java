package edu.cit.aquino.notification;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Repository
class NotificationRepository {
    private final JdbcTemplate jdbcTemplate;

    NotificationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void save(String message) {
        jdbcTemplate.update("INSERT INTO notifications (message) VALUES (?)", message);
    }

    List<NotificationRecord> findAll() {
        return jdbcTemplate.query(
                "SELECT notification_id, message, created_at FROM notifications ORDER BY notification_id DESC",
                (rs, rowNum) -> {
                    Timestamp ts = rs.getTimestamp("created_at");
                    OffsetDateTime odt = ts != null ? ts.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime() : null;
                    return new NotificationRecord(
                            rs.getLong("notification_id"),
                            rs.getString("message"),
                            odt
                    );
                }
        );
    }
}
