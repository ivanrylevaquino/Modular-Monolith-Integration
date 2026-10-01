package edu.cit.aquino.channel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record TianggeHeartbeatRequest(
        String appName,
        String startedAt,
        long uptimeSeconds
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
record TianggeHeartbeatResponse(
        String serverTime,
        Integer nextHeartbeatSeconds
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
record TianggeListingItem(
        String sellerSku,
        String title,
        String supplierSku
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
record TianggeStockItem(
        String sellerSku,
        int available
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
record TianggeOrderLine(
        String sellerSku,
        int qty
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
record TianggeBuyer(
        String name,
        String city
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
record TianggeFeedEvent(
        long seq,
        String eventId,
        String type,
        String orderId,
        String placedAt,
        String decisionDeadline,
        String cancelledAt,
        String confirmDeadline,
        List<TianggeOrderLine> lines,
        TianggeBuyer buyer
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
record TianggeFeedResponse(
        List<TianggeFeedEvent> events,
        Long nextCursor
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
record TianggeDecisionRequest(
        String decision,
        String shopOrderId,
        String reason
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
record TianggeResolutionRequest(
        String status
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
record TianggeCancellationRequest(
        boolean restocked
) {}

@JsonIgnoreProperties(ignoreUnknown = true)
record TianggeErrorResponse(
        String error,
        String message
) {}
