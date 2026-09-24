# Modular Monolith Integration --- Lab 2 & Lab 3

A single Spring Boot application implementing **Order**, **Inventory**,
**Notification**, and **Supplier** modules with in-process integration,
shared Supabase PostgreSQL persistence, in-process domain events, and an
Anti-Corruption Layer around the external LegacySupply system.

## Tech Stack

-   Java 21 (lab machine: Java 17 target)
-   Spring Boot
-   Spring JDBC
-   PostgreSQL / Supabase
-   React + Vite
-   Maven
-   Spring `ApplicationEventPublisher` / `@EventListener`
-   Jackson XML (`jackson-dataformat-xml`) for the LegacySupply adapter
-   `@Scheduled` background jobs (pending-order retry, delivery tracking)

## Project Structure

``` text
Modular-Monolith-Integration/
├── backend/
│   ├── pom.xml
│   ├── mvnw
│   ├── mvnw.cmd
│   └── src/
│       └── main/java/edu/cit/aquino/
│           ├── shop/          (Order module)
│           ├── inventory/     (Inventory module)
│           ├── notification/  (Notification module)
│           └── supplier/      (Anti-Corruption Layer for LegacySupply)
├── frontend/
├── database/
│   └── schema.sql
├── INTEGRATION.md   (Lab 3 contract discovery + ACL documentation)
├── REFLECTION.md    (Lab 3 self-check reflection questions)
└── README.md
```

The required module boundaries are:

``` text
edu.cit.aquino.shop
edu.cit.aquino.inventory
edu.cit.aquino.notification
edu.cit.aquino.supplier
```

`InventoryServiceImpl` remains package-private. The Order module depends
on the Inventory module through the `InventoryService` interface rather
than its implementation. The `supplier` package only exposes
`SupplierGateway`, `SupplierOrderResult`, and `SupplierOrderStatus`
publicly — everything LegacySupply-shaped (XML classes, the HTTP client,
session handling) stays package-private, so `Order` and `Inventory`
never import anything that describes LegacySupply.

------------------------------------------------------------------------

## Supabase Setup


1.  Create/open the Supabase project.
2.  Open **Connect** and select the **Session pooler** connection.
3.  Use the database username, password, host, port, and database shown
    by Supabase.
4.  For Spring Boot, the PostgreSQL URL uses the JDBC prefix:

``` text
jdbc:postgresql://<pooler-host>:5432/postgres?sslmode=require
```

5.  Set these environment variables before starting the backend:

``` text
SUPABASE_DB_URL=jdbc:postgresql://<pooler-host>:5432/postgres?sslmode=require
SUPABASE_DB_USERNAME=postgres.<project-ref>
SUPABASE_DB_PASSWORD=<database-password>
```

Do not commit the real password or other credentials.

7.  Also set the LegacySupply partner credentials before starting the
    backend (see `backend/.env.example` / your gitignored `run.bat`):

``` text
LS_CLIENT_ID=<your student ID>
LS_API_KEY=<your instructor-issued API key>
```

Never commit the real API key. It is read at runtime via
`System.getenv`/`@Value` in `LegacySupplyClient` and is not stored
anywhere in source control.

6.  Recreate the database from the supplied SQL script rather than
    manually editing the Supabase tables:

``` powershell
.\psql.exe "YOUR_CONNECTION_STRING" -f "C:\path\to\Modular-Monolith-Integration\database\schema.sql"
```

The script recreates and seeds:

-   `inventory`
-   `orders`
-   `order_items`
-   `notifications`

Seed inventory:

  Product               ID       Initial Stock
  --------------------- ------ ---------------
  Wireless Mouse        P100                25
  Mechanical Keyboard   P200                10
  USB-C Hub             P300                 0

------------------------------------------------------------------------

## Running the Backend

From `backend` on Windows:

``` powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

The backend runs on:

``` text
http://localhost:8080
```

## Running the Frontend

From `frontend`:

``` powershell
npm install
npm run dev
```

The Vite development server normally runs at:

``` text
http://localhost:5173
```

CORS is configured for the Vite development server.

------------------------------------------------------------------------

## Lab 2 Features

### 1. Multi-item Orders

`POST /api/orders`

Request:

``` json
{
  "items": [
    { "productId": "P100", "quantity": 9 },
    { "productId": "P200", "quantity": 3 },
    { "productId": "P300", "quantity": 10 }
  ]
}
```

Every line item is validated against current stock before any
reservation is attempted. If one line item cannot be fulfilled, the
whole order is rejected and no inventory is reserved.

Successful orders return `CONFIRMED` and line-item outcomes of
`RESERVED`. Rejected orders return `REJECTED` and identify
insufficient-stock items.

The backend transaction and validation-before-reservation design provide
the all-or-nothing behavior required for the lab.

### 2. Cancellation and Restock

``` text
POST /api/orders/{orderId}/cancel
```

A confirmed order can be cancelled once. Each reserved line item's
quantity is returned through `InventoryService.restock()`.

-   Unknown order → `404`
-   Already cancelled → `409`

### 3. Live Inventory and Order History

``` text
GET /api/inventory
GET /api/orders
```

The frontend refreshes inventory and order history after
order/cancellation operations.

### 4. Notification Events

Order processing publishes domain events through Spring's
`ApplicationEventPublisher`:

``` text
OrderPlaced
OrderRejected
```

The Notification module consumes these events with `@EventListener` and
writes notification records to the database.

``` text
GET /api/notifications
```

The Notification module only depends on the event classes. It does not
call OrderService or InventoryService.

### 5. Low-stock Alerts

After a successful reservation, if remaining stock falls below the
configured threshold of `5`, a `LowStockEvent` is published.

The Notification module records a separate reorder-needed notification,
and the frontend highlights low-stock inventory rows.

### Event Processing

The event listeners are **synchronous by default**. `@Async` was not
used because asynchronous processing is unnecessary for this lab and
synchronous listeners make the event-to-notification behavior immediate
and easy to demonstrate within the single deployable application.

------------------------------------------------------------------------

## API Summary

  --------------------------------------------------------------------------------
  Method                  Endpoint                         Purpose
  ----------------------- -------------------------------- -----------------------
  `POST`                  `/api/orders`                    Place a multi-item
                                                           order

  `POST`                  `/api/orders/{orderId}/cancel`   Cancel and restock a
                                                           confirmed order

  `GET`                   `/api/orders`                    Retrieve order history

  `GET`                   `/api/inventory`                 Retrieve current
                                                           inventory

  `GET`                   `/api/notifications`             Retrieve
                                                           notification/event log
  --------------------------------------------------------------------------------

------------------------------------------------------------------------

## Lab 3: LegacySupply Integration

The `edu.cit.aquino.supplier` module is an Anti-Corruption Layer (ACL)
that sits between the modular monolith and LegacySupply, an external
XML-only supplier system.

-   **`SupplierGateway`** (public interface) — `orderReplenishment(productId, unitsNeeded)`.
    Converts units to LegacySupply cases (rounding up via `PackSize`),
    generates a unique `BuyerRef`/`X-Request-Id` per reorder, and
    returns our own `SupplierOrderResult`/`SupplierOrderStatus` types.
-   **`LegacySupplyClient`** (package-private) — handles XML
    serialization, session sign-in/renewal, and talks to
    `POST /auth/token`, `POST /purchase-orders`,
    `GET /purchase-orders/{PoNumber}`, `GET /purchase-orders?buyerRef=`.
-   **Resilience** — 3-second timeouts, up to 3 retry attempts with
    backoff, idempotent via a persisted `X-Request-Id` (survives
    retries and app restarts), and a `PendingOrderRetryJob`
    (`@Scheduled`) that resubmits any reorder left `PENDING` after an
    outage so nothing is lost.
-   **Delivery tracking** — `DeliveryTrackingJob` (`@Scheduled`) polls
    open purchase orders, maps LegacySupply status codes to our own
    `SupplierOrderStatus` enum, and on `DELIVERED` publishes a
    `StockReplenishedEvent` that `Inventory` listens for and restocks
    from. `Order` and `Inventory` never call the supplier module
    directly for this.
-   **Auto-reorder rule** — `NotificationEventListener` reacts to
    `Inventory`'s existing `LowStockEvent` and calls `SupplierGateway`
    instead of just logging.

Full contract discovery notes (session lifetime, error codes, Qty/Uom
conversion) are in [`INTEGRATION.md`](./INTEGRATION.md). Self-check
reflection answers are in [`REFLECTION.md`](./REFLECTION.md).

As of this submission, LegacySupply's own `/verify` self-check page
confirms every integration check as met for this account: 11 sign-ins,
4 purchase orders on file, 39/39 order requests carrying `X-Request-Id`,
0 duplicates across 6 chaos events, both outage-blocked reorders
eventually placed, a delivered order and a cancelled order both
observed, and 51 status polls with 0 rate-limit hits. Those scores are
computed server-side from actual request traffic, not from anything
claimed in this repo.

------------------------------------------------------------------------

## Reflection

### 1. Atomic multi-item orders

The multi-item order flow stays atomic inside the modular monolith
because OrderService validates every line item against current inventory
before making any reservation calls. This prevents the common case where
the first few products are reserved and a later product fails. The order
operation is also transactional, so the database changes made during the
request participate in one transaction. Because Order and Inventory run
inside the same application and share the same database, the application
can use normal transaction management rather than coordinating
independent network calls. If Inventory were extracted into a separate
microservice, a single database transaction could no longer span both
services. I would need a distributed workflow such as a saga, with
compensating actions such as restocking previously reserved items when a
later reservation fails. The system would also need to handle timeouts,
retries, duplicate messages, and partial failures.

### 2. Event-driven Notification coupling

Publishing an event instead of directly calling Notification reduces
coupling between OrderService and Notification. OrderService only knows
that an `OrderPlaced` or `OrderRejected` event exists and does not need
to know how notifications are stored or displayed. Notification can
subscribe to those events independently, and additional consumers could
be added without modifying OrderService. If Notification became a
separate microservice, the in-process Spring event mechanism would need
to be replaced or bridged with a message broker such as RabbitMQ, Kafka,
or another durable messaging system. The design would also need
decisions about delivery guarantees, retries, duplicate-event handling,
ordering, persistence, and what happens when the notification service is
temporarily unavailable.

### 3. Extracting one module

If exactly one module had to be extracted first, I would choose
Notification because it is already event-driven and has no dependency on
OrderService or InventoryService implementations. The main change would
be replacing the in-process `ApplicationEventPublisher`/`@EventListener`
connection with a message broker and explicit event contracts. Order
would publish serialized `OrderPlaced`, `OrderRejected`, and `LowStock`
messages, while Notification would consume them independently and write
to its own database. The frontend's notification endpoint could then
point to the notification service or an API gateway. The Order and
Inventory modules could remain in the original monolith while the
notification boundary is introduced incrementally.

------------------------------------------------------------------------

## Submission Checklist

- [x] `edu.cit.aquino.supplier` ACL module implemented
- [x] Order/Inventory import no LegacySupply-specific types
- [x] `supplier_orders` table with own status enum
- [x] Timeouts, retries, idempotent `X-Request-Id`/`BuyerRef`
- [x] `PendingOrderRetryJob` for lost reorders
- [x] `DeliveryTrackingJob` + `StockReplenishedEvent` restock flow
- [x] `INTEGRATION.md` (mapping table, sessions, error codes, Qty/Uom)
- [x] `REFLECTION.md` (3 self-check questions, answered from real traffic)
- [x] `/verify` shows all checks Met (11 sign-ins, 4 orders, 0 duplicates, 0 rate-limited)
- [ ] `LS_API_KEY` confirmed absent from every committed file
- [ ] `.\mvnw.cmd test` passes
- [ ] `.\mvnw.cmd spring-boot:run` works against live LegacySupply
- [ ] Committed on a feature branch, merged into `main`
- [ ] Tagged `lab3-final` and pushed with `git push --tags`
- [ ] GitHub repository link ready for submission
