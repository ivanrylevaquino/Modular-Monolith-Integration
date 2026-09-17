package edu.cit.aquino.shop;

import java.util.List;

public record OrderPlacedEvent(Long orderId, List<OrderLineItem> items) {}
