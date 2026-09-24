package edu.cit.aquino.supplier;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/supplier")
@CrossOrigin(origins = "http://localhost:5173")
class SupplierController {
    private final SupplierGateway supplierGateway;
    private final SupplierOrderRepository repository;

    SupplierController(SupplierGateway supplierGateway, SupplierOrderRepository repository) {
        this.supplierGateway = supplierGateway;
        this.repository = repository;
    }

    @GetMapping("/orders")
    public ResponseEntity<List<SupplierOrderRecord>> getRecentOrders() {
        return ResponseEntity.ok(repository.findActiveOrders(100));
    }

    @PostMapping("/reorder")
    public ResponseEntity<SupplierOrderResult> triggerReorder(
            @RequestParam String productId,
            @RequestParam(defaultValue = "15") int units
    ) {
        SupplierOrderResult result = supplierGateway.orderReplenishment(productId, units);
        return ResponseEntity.ok(result);
    }
}
