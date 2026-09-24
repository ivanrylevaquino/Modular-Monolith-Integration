package edu.cit.aquino.shop;

import java.util.List;

public record OrderRequest(List<OrderLineItem> items) {}
