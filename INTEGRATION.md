# LegacySupply Integration Documentation

This document describes the integration between our Modular Monolith application and LegacySupply Distribution (API Rev. 2.3.1).

---

## 1. Product Mapping Table

The following table maps our internal inventory products to LegacySupply catalog items:

| Product ID | Product Name | LegacySupply SupplierSku | PackSize | Supplier Description | Unit Cost (PHP) |
|---|---|---|---|---|---|
| `P100` | Wireless Mouse | `KTB-7686` | 12 | WIRELESS MOUSE 2.4GHZ | 450.00 |
| `P200` | Mechanical Keyboard | `KTB-7976` | 24 | KEYBOARD MECH TKL | 1899.00 |
| `P300` | USB-C Hub | `KTB-1734` | 20 | USB HUB 4-PORT | 399.00 |

*Note*: Pack sizes define how many individual units are packaged inside one case (`CS`).

---

## 2. LegacySupply Session Lifecycle

### How Sessions Work
1. **Authentication**:
   - The client performs a `POST /api/v1/auth/token` with an XML payload containing `ClientId` (Student ID) and `ApiKey`:
     ```xml
     <AuthRequest>
       <ClientId>22-2068-823</ClientId>
       <ApiKey>***REDACTED*** (read from LS_API_KEY env var, never committed)</ApiKey>
     </AuthRequest>
     ```
   - On success (HTTP 200), LegacySupply returns an `AuthResponse` containing `SessionToken` and `IssuedAt`:
     ```xml
     <AuthResponse>
       <SessionToken>637921899d227982f21f0ba61248ad5679fd</SessionToken>
       <IssuedAt>2026-09-24T11:07:59.492Z</IssuedAt>
     </AuthResponse>
     ```
2. **Authorized Calls**:
   - Subsequent calls to catalog, order placement, and tracking endpoints must supply the session token in the HTTP header:
     ```http
     X-LS-Session: <SessionToken>
     ```

### Session Lifetime Measurement
- Empirical measurement was conducted by polling `/api/v1/catalog` at regular intervals following token issuance.
- **Measured Duration**: A LegacySupply session token is valid for **180 seconds (3 minutes)**.
- At $t > 180$ seconds, any request carrying the expired token is rejected with HTTP 401:
  ```xml
  <LSError>
    <Code>E-AUTH-07</Code>
    <Message>Session not valid.</Message>
  </LSError>
  ```
- **Adapter Strategy**:
  - The adapter caches the current session token with its creation timestamp.
  - Proactive refresh: If the token is older than 160 seconds, a fresh token is requested before initiating network operations.
  - Reactive refresh: If an endpoint returns HTTP 401 or error code `E-AUTH-02`, `E-AUTH-03`, or `E-AUTH-07`, the adapter clears the token, signs in again, and retries the request transparently without user intervention.

---

## 3. Error Codes and Causes

During probing and testing, the following error codes were observed:

| Error Code | HTTP Status | Response Message | Exact Cause Encountered |
|---|---|---|---|
| `E-AUTH-01` | 401 | Credentials rejected. | Invalid `ApiKey` or unknown `ClientId` provided in `AuthRequest`. |
| `E-AUTH-02` | 401 | Session header missing. | Protected endpoint called without supplying the `X-LS-Session` header. |
| `E-AUTH-03` | 401 | Session not recognized. | Bogus or unrecognized token passed in `X-LS-Session`. |
| `E-AUTH-07` | 401 | Session not valid. | Session token expired after its 3-minute lifetime. |
| `E-FMT-01` | 415 | Unsupported media. | Request body sent with non-XML media type (e.g. `text/plain` or `application/json`). |
| `E-FMT-02` | 400 | Malformed document. | Request body contains invalid / unclosed XML syntax. |
| `E-REF-05` | 400 | BuyerRef invalid. | `BuyerRef` is empty or exceeds the maximum length of 40 characters. |
| `E-SKU-02` | 422 | Item not recognized. | `SupplierSku` does not exist in the partner's catalog. |
| `E-QTY-11` | 422 | Quantity invalid. | `Qty` is not a valid whole number between 1 and 99 (e.g., 0 or negative). |
| `E-IDEM-04` | 409 | Request id reused with different content. | Reusing the same `X-Request-Id` header for a request with different body fields (e.g. changed quantity). |
| `E-PO-04` | 404 | Order not found. | Attempting to track a `PoNumber` that does not exist. |
| `E-QRY-06` | 400 | Query parameter required. | Calling `GET /api/v1/purchase-orders` without the mandatory `buyerRef` parameter. |
| `E-RATE-03` | 429 | Request quota exceeded. | Exceeding the allowed request frequency / burst quota. |
| `E-SYS-50` | 503 | Processing error. | Server-side transient processing error. |
| `E-SYS-99` | 503 | Service unavailable. Try later. | Injected chaos / maintenance window where the supplier system is temporarily offline. |

---

## 4. Quantity and Unit of Measure (Uom)

### Explanation in Our Own Words
LegacySupply is a bulk wholesale distributor and operates strictly in **Cases (`CS`)**, not individual customer units.
- **`Uom` (Unit of Measure)**: Specifies the packaging unit used by the supplier. For all items in our catalog, `Uom` is `"CS"` (Cases).
- **`Qty`**: The number of cases ordered (a positive integer from 1 to 99). Each case contains `PackSize` individual units of the item.
- **Conversion Rule**:
  Because our inventory tracks individual units while LegacySupply only sells whole cases, the Anti-Corruption Layer must calculate:
  $$\text{Cases} = \left\lceil \frac{\text{Units Needed}}{\text{PackSize}} \right\rceil$$
  Any fractional case is rounded **up** to ensure the inventory receives at least the required units.

### Worked Example
Suppose product `P100` (Wireless Mouse, `PackSize = 12`) drops below the low-stock threshold, and our auto-reorder rule determines that **15 units** are needed to replenish stock:
1. **Calculation**:
   $$\text{Cases} = \left\lceil \frac{15}{12} \right\rceil = \lceil 1.25 \rceil = 2 \text{ cases}$$
2. **Order Placement**:
   The adapter submits a purchase order to LegacySupply with `Qty = 2` cases and `SupplierSku = "KTB-7686"`.
3. **Delivery & Restock**:
   When LegacySupply marks the purchase order as Delivered (`StatusCode = 40`), the delivery listener calculates the total units received:
   $$\text{Units Received} = 2 \text{ cases} \times 12 \frac{\text{units}}{\text{case}} = 24 \text{ units}$$
   Inventory is then restocked by 24 units, successfully replenishing the stock level.

---

## 5. Handling Unexpected Statuses

The normal lifecycle status codes are:
- `10`: Accepted
- `20`: Picking
- `30`: Shipped
- `40`: Delivered

### Unexpected Status Strategy
If LegacySupply returns an unexpected status code (such as a cancelled order, an unannounced numeric status, or an unrecognized string):
1. **Mapping**: The status is mapped to `SupplierOrderStatus.CANCELLED` (if cancelled) or `SupplierOrderStatus.UNKNOWN`.
2. **State Persistence**: The `supplier_orders` record is updated with this status and the timestamp.
3. **No Automatic Restock**: Because the order was not successfully delivered, no inventory restock event is fired.
4. **Alert Notification**: An internal notification/log entry is generated warning administrators that the purchase order could not be completed normally, allowing manual review without crashing the background tracking job.
