package edu.cit.aquino.supplier;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.ArrayList;
import java.util.List;

@JacksonXmlRootElement(localName = "PurchaseOrderList")
@JsonIgnoreProperties(ignoreUnknown = true)
class PurchaseOrderListXml {
    @JacksonXmlProperty(localName = "Count")
    private int count;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "PurchaseOrder")
    private List<PurchaseOrderAckXml> orders = new ArrayList<>();

    PurchaseOrderListXml() {}

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
    public List<PurchaseOrderAckXml> getOrders() { return orders; }
    public void setOrders(List<PurchaseOrderAckXml> orders) { this.orders = orders; }
}
