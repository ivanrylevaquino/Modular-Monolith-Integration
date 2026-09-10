package edu.cit.aquino.shop;

public interface OrderService {
    OrderResult placeOrder(String productId, int quantity);
}
