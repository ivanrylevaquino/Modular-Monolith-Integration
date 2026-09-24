package edu.cit.aquino.supplier;

public interface SupplierGateway {
    SupplierOrderResult orderReplenishment(String productId, int unitsNeeded);
}
