package edu.cit.aquino.shop;

import java.util.List;

public record OrderCancelledEvent(Long orderId, List<OrderLineItem> items) {}
