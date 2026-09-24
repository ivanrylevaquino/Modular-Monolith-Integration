package edu.cit.aquino.supplier;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "PurchaseOrder")
class PurchaseOrderXml {
    @JacksonXmlProperty(localName = "SupplierSku")
    private String supplierSku;

    @JacksonXmlProperty(localName = "Qty")
    private int qty;

    @JacksonXmlProperty(localName = "BuyerRef")
    private String buyerRef;

    PurchaseOrderXml() {}

    PurchaseOrderXml(String supplierSku, int qty, String buyerRef) {
        this.supplierSku = supplierSku;
        this.qty = qty;
        this.buyerRef = buyerRef;
    }

    public String getSupplierSku() { return supplierSku; }
    public void setSupplierSku(String supplierSku) { this.supplierSku = supplierSku; }
    public int getQty() { return qty; }
    public void setQty(int qty) { this.qty = qty; }
    public String getBuyerRef() { return buyerRef; }
    public void setBuyerRef(String buyerRef) { this.buyerRef = buyerRef; }
}
