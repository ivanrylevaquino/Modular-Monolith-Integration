package edu.cit.aquino.supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SupplierGatewayImplTest {
    private LegacySupplyClient client;
    private SupplierOrderRepository repository;
    private SupplierGatewayImpl gateway;

    @BeforeEach
    void setUp() {
        client = mock(LegacySupplyClient.class);
        repository = mock(SupplierOrderRepository.class);
        when(repository.nextOrderId()).thenReturn(101L);
        gateway = new SupplierGatewayImpl(client, repository);
    }

    @Test
    void convertsUnitsRoundingUpAndPlacesOrder() {
        PurchaseOrderAckXml ack = new PurchaseOrderAckXml();
        ack.setPoNumber("PO-200001");
        ack.setStatusCode("10");
        ack.setSupplierSku("KTB-7686");
        ack.setQty(2);
        ack.setUom("CS");
        ack.setBuyerRef("RO-101");

        when(client.placePurchaseOrder(any(PurchaseOrderXml.class), anyString())).thenReturn(ack);

        // Product P100 packSize is 12. 15 units needed -> ceil(15/12) = 2 cases = 24 units
        SupplierOrderResult result = gateway.orderReplenishment("P100", 15);

        assertNotNull(result);
        assertEquals(101L, result.id());
        assertEquals("P100", result.productId());
        assertEquals("RO-101", result.buyerRef());
        assertEquals("PO-200001", result.poNumber());
        assertEquals(2, result.cases());
        assertEquals(24, result.units());
        assertEquals(SupplierOrderStatus.PLACED, result.status());

        ArgumentCaptor<PurchaseOrderXml> orderCaptor = ArgumentCaptor.forClass(PurchaseOrderXml.class);
        verify(client).placePurchaseOrder(orderCaptor.capture(), eq(result.requestId()));
        assertEquals("KTB-7686", orderCaptor.getValue().getSupplierSku());
        assertEquals(2, orderCaptor.getValue().getQty());
        assertEquals("RO-101", orderCaptor.getValue().getBuyerRef());

        verify(repository).updateStatusAndPo(101L, SupplierOrderStatus.PLACED, "PO-200001");
    }

    @Test
    void retriesOnTransientFailureAndSucceeds() {
        PurchaseOrderAckXml ack = new PurchaseOrderAckXml();
        ack.setPoNumber("PO-200002");
        ack.setStatusCode("10");

        when(client.placePurchaseOrder(any(PurchaseOrderXml.class), anyString()))
                .thenThrow(new LegacySupplyException("Timeout", "TIMEOUT", 504))
                .thenReturn(ack);

        SupplierOrderResult result = gateway.orderReplenishment("P200", 25);

        assertEquals(SupplierOrderStatus.PLACED, result.status());
        assertEquals("PO-200002", result.poNumber());
        verify(client, times(2)).placePurchaseOrder(any(), any());
    }

    @Test
    void preservesPendingStatusWhenOutagePersists() {
        when(client.placePurchaseOrder(any(PurchaseOrderXml.class), anyString()))
                .thenThrow(new LegacySupplyException("Service unavailable", "E-SYS-99", 503));

        SupplierOrderResult result = gateway.orderReplenishment("P300", 20);

        assertNotNull(result);
        assertEquals(SupplierOrderStatus.PENDING, result.status());
        assertNull(result.poNumber());
        verify(client, times(3)).placePurchaseOrder(any(), any());
        verify(repository, never()).updateStatusAndPo(eq(101L), eq(SupplierOrderStatus.PLACED), any());
    }

    @Test
    void throwsOnUnknownProduct() {
        assertThrows(IllegalArgumentException.class, () -> gateway.orderReplenishment("UNKNOWN-PRODUCT", 10));
    }
}
