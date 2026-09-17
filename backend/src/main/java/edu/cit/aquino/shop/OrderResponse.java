package edu.cit.aquino.shop;

import java.time.OffsetDateTime;
import java.util.List;

public record OrderResponse(
        Long orderId,
        String status,
        String reason,
        OffsetDateTime createdAt,
        List<OrderLineItem> items
) {}
