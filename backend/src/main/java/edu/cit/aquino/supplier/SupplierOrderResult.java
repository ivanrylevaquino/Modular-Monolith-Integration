package edu.cit.aquino.supplier;

import java.time.Instant;

public record SupplierOrderResult(
        long id,
        String productId,
        String buyerRef,
        String requestId,
        String poNumber,
        int cases,
        int units,
        SupplierOrderStatus status,
        String message,
        Instant createdAt
) {}
