# Reflection - Modular Monolith Integration

**Student ID**: 22-2068-823  
**Modules**: `edu.cit.aquino.supplier`, `edu.cit.aquino.channel`, `edu.cit.aquino.shop`, `edu.cit.aquino.inventory`

---

## Lab 4: Marketplace (Tiangge) Reflection Questions

### Marketplace Question 1

> **Tiangge order TG-D5R7S2 (3 x P300) was accepted at 04:09:53. At that moment your last published stock for P300 was 0, and the stock Tiangge worked out from your own decisions, cancellations and deliveries was 0. Where did your application's stock figure come from, and why did it disagree?**

**Where the stock figure came from:**  
The application verified stock availability against the local PostgreSQL `inventory` table (`SELECT stock FROM inventory WHERE product_id = 'P300'`). Earlier, at 04:03:18, LegacySupply purchase order `PO-100440` (covering supplier SKU `KTB-1734`, matching product `P300` with pack size 20) reached status `40` (`DELIVERED`). `DeliveryTrackingJob` published a `StockReplenishedEvent`, and `InventoryDeliveryListener` restocked the local database by +20 units. Consequently, the local database recorded 20 units available, prompting `TianggeFeedPoller` and `OrderService` to accept `TG-D5R7S2` for 3 units.

**Why it disagreed with Tiangge:**  
Tiangge computes available stock using an internal running balance: `last published stock - accepted orders + customer cancellations`. In our initial architecture, stock publication was decoupled into an asynchronous `@EventListener` in `TianggeStockSyncListener` with a 500 ms debounce timer. Following the delivery of `PO-100440`, the asynchronous stock sync either ran before the database transaction completed or was delayed/cancelled by subsequent rapid events. As a result, a `PUT /stock` payload reflecting the new stock balance (+20 units) was never received and acknowledged by Tiangge before `TG-D5R7S2` arrived. Tiangge’s ledger for `P300` remained at 0. When our app accepted `TG-D5R7S2`, Tiangge flagged it as an oversold order because our published stock had not been kept in lockstep with our local database.

**Remediation:**  
We removed asynchronous debounce timers. Stock updates are now executed strictly and synchronously:
1. Immediately after `client.sendDecision(...)` completes for an accepted order.
2. Immediately after `client.sendCancellation(...)` is confirmed for a customer cancellation.
3. Immediately after `TianggeBackorderManager.resolveBackorders(...)` resolves backorders following any supplier delivery.

---

### Marketplace Question 2

> **Event evt_524c1860d881f0a1 (order TG-SRMM2K) reached your application twice, as seq 2 and seq 3, and you processed it once. Show the code and the stored data that made the second delivery harmless, and explain what would happen if your application restarted between the two.**

**Code and stored data making replay harmless:**  
Durable state is persisted in Supabase PostgreSQL:
- `tiangge_orders`: Stores `order_id VARCHAR(50) PRIMARY KEY`, `event_id`, `decision`, `shop_order_id`, and `status`.
- `tiangge_feed_state`: Durably maintains `last_cursor BIGINT`.

In `TianggeFeedPoller.processOrderPlaced`:
```java
String orderId = event.orderId();
Optional<TianggeOrderRecord> existing = repository.findOrder(orderId);
if (existing.isPresent()) {
    log.info("Duplicate order {} already processed with decision {}, skipping duplicate placement",
            orderId, existing.get().decision());
    TianggeOrderRecord rec = existing.get();
    if (rec.decision() != null && rec.shopOrderId() != null) {
        client.sendDecision(orderId,
                new TianggeDecisionRequest(rec.decision(), rec.shopOrderId(), "Re-affirmed decision"));
    }
    return;
}
```
When `evt_524c1860d881f0a1` was first received at `seq 2`, it was evaluated, accepted, persisted to `tiangge_orders`, and `last_cursor` was updated. When Tiangge redelivered the event at `seq 3`, `repository.findOrder("TG-SRMM2K")` returned the stored record. The poller skipped order placement and inventory deduction, safely re-affirming the existing decision (`ACCEPTED`, `SO-501`) to Tiangge.

**What happens if the application restarts between seq 2 and seq 3:**  
Because `last_cursor` is persisted in PostgreSQL at the conclusion of each event (`repository.updateLastCursor(event.seq())`), upon restart `repository.getLastCursor()` loads `cursor = 2`. The application resumes polling with `GET /feed?after=2`, receiving `seq 3`. When `seq 3` is handled, `repository.findOrder("TG-SRMM2K")` finds the committed database record from `seq 2`. The duplicate is detected immediately, preventing double-reservation or secondary shop orders, and re-affirms the recorded decision.

---

### Marketplace Question 3

> **Order TG-M9ZBGD was backordered at 04:08:35 and accepted at 04:14:24, after PO-100441 was delivered at 04:13:14. Trace how the delivery reached your Inventory and what then resumed the backordered order.**

**End-to-end trace:**
1. **Delivery Detection**: `DeliveryTrackingJob` runs periodically (`@Scheduled(fixedDelay = 12000)`), polling LegacySupply via `GET /purchase-orders/PO-100441`. At 04:13:14, LegacySupply returned `<StatusCode>40</StatusCode>` (`DELIVERED`).
2. **Domain Event Publication**: `DeliveryTrackingJob` updated the supplier order in Postgres (`supplier_orders`) to `DELIVERED` and published Spring domain event `StockReplenishedEvent("P200", 24)`.
3. **Inventory Restocking**: `InventoryDeliveryListener` (`@Order(1)`) received `StockReplenishedEvent` and called `inventoryService.restock("P200", 24)`. This executed `UPDATE inventory SET stock = stock + 24 WHERE product_id = 'P200'`, incrementing available units in Postgres.
4. **Backorder Resumption**: `TianggeBackorderManager` (`@Order(2)`) received `StockReplenishedEvent`. It queried `repository.findBackorderedOrders()`, matched `TG-M9ZBGD` (`BACKORDERED`), and re-checked required quantities against `inventoryService.getItem("P200")`. Seeing stock was now sufficient, it invoked `orderService.placeOrder()` to reserve inventory and create internal shop order `SO-...`.
5. **Resolution Sent to Tiangge**: `TianggeBackorderManager` dispatched `POST /orders/TG-M9ZBGD/resolution` with `{ "status": "ACCEPTED" }` to Tiangge at 04:14:24, updating the order to `ACCEPTED` on Tiangge and marking `RESOLVED_ACCEPTED` in `tiangge_orders`.
6. **Stock Synchronization**: Immediately following backorder resolution, `channelService.syncStock()` was called, publishing updated remaining stock figures to Tiangge via `PUT /stock`.

---

## Lab 3: LegacySupply Integration Reflection Questions

### LegacySupply Question 1

> **LegacySupply holds more than one order for BuyerRef "RO-1": PO-100021 (19:19:04) and PO-100440 (04:03:18). Reconstruct the sequence of events that produced the duplicate, and describe the change you made (or would make) so it cannot happen again.**

**Reconstruction:**  
In `database/schema.sql`, supplier order IDs were created via a sequence:
`CREATE SEQUENCE supplier_orders_id_seq START WITH 1 INCREMENT BY 1;`
In `SupplierGatewayImpl`:
`String buyerRef = "RO-" + orderId;`
During Lab 3 testing (at 19:19:04), the first supplier order was assigned ID 1, producing `BuyerRef = "RO-1"` and creating `PO-100021`. Between lab runs, the database was re-initialized using `schema.sql`, which dropped and recreated `supplier_orders_id_seq` starting at 1. When the first replenishment order of the new session occurred at 04:03:18, the sequence yielded 1 again, generating `BuyerRef = "RO-1"` and resulting in `PO-100440`. Because LegacySupply retains partner order history across the lifetime of the partner account, it received `RO-1` a second time with different items and a new `X-Request-Id`, logging it as a duplicate BuyerRef.

**Remediation:**  
To guarantee global uniqueness across schema resets, application restarts, and multi-day runs, `BuyerRef` should include a timestamp or epoch suffix within the 40-character limit allowed by LegacySupply:
`String buyerRef = "RO-" + orderId + "-" + (System.currentTimeMillis() % 10000000);`
This ensures `buyerRef` never collides with any past order on LegacySupply regardless of local sequence resets.

---

### LegacySupply Question 2

> **At 04:18:44 your request for BuyerRef "RO-9" (X-Request-Id REQ-9-65c5744517b3) received a 503, but LegacySupply had already created PO-100449. Walk through exactly what your adapter did next, and explain why that did or did not result in a second order.**

**Walkthrough:**  
1. On attempt 1, `SupplierGatewayImpl.submitOrderWithRetry` sent `POST /purchase-orders` with `BuyerRef = "RO-9"` and `X-Request-Id = "REQ-9-65c5744517b3"`. LegacySupply created `PO-100449` in its database, but due to an injected chaos event / network drop, returned HTTP 503 (`E-SYS-50: client gone`).
2. The adapter caught the 503 exception, applied exponential backoff, and progressed to attempt 2.
3. In attempt 2 (`attempt > 1`), before retrying the POST, the adapter executed an idempotency lookup by querying LegacySupply via `GET /purchase-orders?buyerRef=RO-9`.
4. LegacySupply responded with HTTP 200 containing `PurchaseOrderList` with the existing `PO-100449`.
5. The adapter matched the order by buyerRef, extracted `poNumber = "PO-100449"`, updated the local `supplier_orders` row with status `PLACED` and `poNumber = "PO-100449"`, and returned a successful `SupplierOrderResult` immediately.
6. Because the adapter identified that LegacySupply had already accepted the order, it aborted sending any further POST requests. Consequently, it did **not** result in a second order.

---

### LegacySupply Question 3

> **PO-100026 (BuyerRef "RO-2") ended with StatusCode 90, which is not in the documentation. How did you work out what it means, and what does your system now do with the stock that will never arrive?**

**How it was worked out:**  
The documented LegacySupply order lifecycle consists of `10` (Accepted) → `20` (Picking) → `30` (Shipped) → `40` (Delivered). During status polling for `PO-100026`, `StatusCode 90` was returned and remained permanently unchanged across subsequent poll cycles. Unlike intermediate states (`10`, `20`, `30`) that transition forward, `90` was terminal, but did not deliver any goods. In supply chain ERP and partner EDI protocols, codes in the 90s standardly designate Void/Cancellation. Thus, in `DeliveryTrackingJob.mapLegacyStatusCode()`, codes `90`, `99`, `CANCELLED`, and `VOID` were explicitly mapped to `SupplierOrderStatus.CANCELLED`.

**What the system does with the missing stock:**  
When an order enters status `CANCELLED`:
1. `DeliveryTrackingJob` updates the `supplier_orders` record to `status = CANCELLED`.
2. It explicitly suppresses publishing `StockReplenishedEvent`, ensuring the undelivered stock is **not** added to `inventory`.
3. The permanent `CANCELLED` status prevents `hasActiveOrderForProduct()` from considering this order as incoming stock.
4. As a result, subsequent low-stock events or order backorders recognize that no replenishment is in flight, triggering a fresh replenishment order (`orderReplenishment`) with a new `BuyerRef` and `X-Request-Id` to recover the missing inventory.
