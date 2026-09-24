package edu.cit.aquino.supplier;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "PurchaseOrderAck")
@JsonIgnoreProperties(ignoreUnknown = true)
class PurchaseOrderAckXml {
    @JacksonXmlProperty(localName = "PoNumber")
    private String poNumber;

    @JacksonXmlProperty(localName = "StatusCode")
    private String statusCode;

    @JacksonXmlProperty(localName = "SupplierSku")
    private String supplierSku;

    @JacksonXmlProperty(localName = "Qty")
    private int qty;

    @JacksonXmlProperty(localName = "Uom")
    private String uom;

    @JacksonXmlProperty(localName = "BuyerRef")
    private String buyerRef;

    @JacksonXmlProperty(localName = "CreatedAt")
    private String createdAt;

    PurchaseOrderAckXml() {}

    public String getPoNumber() { return poNumber; }
    public void setPoNumber(String poNumber) { this.poNumber = poNumber; }
    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }
    public String getSupplierSku() { return supplierSku; }
    public void setSupplierSku(String supplierSku) { this.supplierSku = supplierSku; }
    public int getQty() { return qty; }
    public void setQty(int qty) { this.qty = qty; }
    public String getUom() { return uom; }
    public void setUom(String uom) { this.uom = uom; }
    public String getBuyerRef() { return buyerRef; }
    public void setBuyerRef(String buyerRef) { this.buyerRef = buyerRef; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
