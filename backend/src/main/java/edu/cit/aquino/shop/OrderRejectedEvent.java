package edu.cit.aquino.shop;

import java.util.List;

public record OrderRejectedEvent(Long orderId, String reason, List<OrderLineItem> items) {}
