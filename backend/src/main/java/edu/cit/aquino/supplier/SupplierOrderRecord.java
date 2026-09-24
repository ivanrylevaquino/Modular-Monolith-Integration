package edu.cit.aquino.supplier;

import java.time.Instant;

record SupplierOrderRecord(
        long id,
        String productId,
        String buyerRef,
        String requestId,
        String poNumber,
        int cases,
        int units,
        SupplierOrderStatus status,
        Instant createdAt,
        Instant updatedAt
) {}
