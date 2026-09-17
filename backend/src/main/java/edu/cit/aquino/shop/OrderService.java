package edu.cit.aquino.shop;

import java.util.List;

public interface OrderService {
    OrderResult placeOrder(List<OrderLineItem> items);
    List<OrderResponse> getOrders();
    OrderResponse cancelOrder(Long orderId);
}
