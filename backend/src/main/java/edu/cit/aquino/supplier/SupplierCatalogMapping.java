package edu.cit.aquino.supplier;

import java.util.Map;
import java.util.Optional;

class SupplierCatalogMapping {
    record SkuInfo(String supplierSku, int packSize, String description) {}

    private static final Map<String, SkuInfo> PRODUCT_TO_SKU = Map.of(
            "P100", new SkuInfo("KTB-7686", 12, "WIRELESS MOUSE 2.4GHZ"),
            "P200", new SkuInfo("KTB-7976", 24, "KEYBOARD MECH TKL"),
            "P300", new SkuInfo("KTB-1734", 20, "USB HUB 4-PORT")
    );

    private static final Map<String, String> SKU_TO_PRODUCT = Map.of(
            "KTB-7686", "P100",
            "KTB-7976", "P200",
            "KTB-1734", "P300"
    );

    static Optional<SkuInfo> findByProductId(String productId) {
        return Optional.ofNullable(PRODUCT_TO_SKU.get(productId));
    }

    static Optional<String> findProductIdBySku(String sku) {
        return Optional.ofNullable(SKU_TO_PRODUCT.get(sku));
    }
}
