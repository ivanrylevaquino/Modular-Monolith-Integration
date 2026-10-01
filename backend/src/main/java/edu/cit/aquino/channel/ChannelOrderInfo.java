package edu.cit.aquino.channel;

public record ChannelOrderInfo(
        String orderId,
        String eventId,
        String decision,
        String shopOrderId,
        String status
) {}
