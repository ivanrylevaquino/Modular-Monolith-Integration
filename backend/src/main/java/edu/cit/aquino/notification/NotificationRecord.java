package edu.cit.aquino.notification;

import java.time.OffsetDateTime;

public record NotificationRecord(Long notificationId, String message, OffsetDateTime createdAt) {}
