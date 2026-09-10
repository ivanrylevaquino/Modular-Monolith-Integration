# Modular Monolith Integration Lab — Order + Inventory

**Stack:** Java 17, Spring Boot, Spring JDBC, Supabase Postgres, React, Vite.

This project implements the lab as a **modular monolith**. `Order` and `Inventory` live inside one Spring Boot process and communicate through the `InventoryService` interface — there are no HTTP/network calls between the modules. The browser is the external client and calls `POST /api/orders` over HTTP.

## Project structure

```text
.
├── backend/
│   ├── pom.xml
│   └── src/main/java/edu/cit/aquino/
│       ├── ShopApplication.java
│       ├── inventory/
│       │   ├── InventoryItem.java
│       │   ├── InventoryRepository.java
│       │   ├── InventoryService.java
│       │   └── InventoryServiceImpl.java
│       └── shop/
│           ├── OrderController.java
│           ├── OrderRepository.java
│           ├── OrderRequest.java
│           ├── OrderResult.java
│           ├── OrderService.java
│           └── OrderServiceImpl.java
├── database/
│   └── schema.sql
└── frontend/
    ├── package.json
    └── src/
        ├── App.jsx
        ├── index.css
        └── main.jsx
```

The required package naming is `edu.cit.aquino.shop` (Order module) and `edu.cit.aquino.inventory` (Inventory module); the `@SpringBootApplication` class sits in the parent package `edu.cit.aquino` so it scans both. CORS for the Vite dev server is enabled with `@CrossOrigin(origins = "http://localhost:5173")` directly on `OrderController`.

## 1. Create the Supabase database

1. Create a free Supabase project.
2. Open **SQL Editor**.
3. Paste and run [`database/schema.sql`](database/schema.sql).
4. In Supabase, click **Connect** (or **Project Settings → Database**) and copy the **Session pooler** connection string — not the "Direct connection" one. The direct host (`db.<ref>.supabase.co`) is IPv6-only and will fail to resolve on many networks (school/lab Wi-Fi, some ISPs) with an error like `could not translate host name`. The pooler host (`aws-0-<region>.pooler.supabase.com`) resolves over IPv4 everywhere. Note the pooler also uses a different username format: `postgres.<project-ref>` instead of plain `postgres`.

```text
jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require
```

## 2. Configure backend credentials

`application.properties` reads the connection details from environment variables (`SUPABASE_DB_URL`, `SUPABASE_DB_USERNAME`, `SUPABASE_DB_PASSWORD`) — nothing real is committed. Do **not** put actual credentials back into `application.properties`.

**Windows (cmd.exe), running via the Maven wrapper:**

```bat
cd backend
set SUPABASE_DB_URL=jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require
set SUPABASE_DB_USERNAME=postgres.<project-ref>
set SUPABASE_DB_PASSWORD=YOUR_PASSWORD
mvnw.cmd spring-boot:run
```

`set` only applies to the current terminal session, so you'll set these each time you open a new one — or use the `run.bat` convenience script described below.

**macOS/Linux:**

```bash
cd backend
export SUPABASE_DB_URL='jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require'
export SUPABASE_DB_USERNAME='postgres.<project-ref>'
export SUPABASE_DB_PASSWORD='YOUR_PASSWORD'
./mvnw spring-boot:run
```

### Convenience launcher (local only, never committed)

`backend/run.bat` sets the three environment variables and calls `mvnw.cmd spring-boot:run` in one step. It is listed in `.gitignore` — Git will not track it, so it's safe to keep your real Supabase password in that file on your own machine. Double-click it, or run:

```bat
cd backend
run.bat
```

If you ever regenerate this project from scratch, recreate `run.bat` locally with your own credentials; never remove it from `.gitignore`.

## 3. Run the React frontend

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`.

The frontend sends:

```http
POST http://localhost:8080/api/orders
Content-Type: application/json

{
  "productId": "P100",
  "quantity": 2
}
```

A successful response looks like:

```json
{
  "status": "CONFIRMED",
  "reason": "Order confirmed and inventory reserved.",
  "inventory": {
    "productId": "P100",
    "name": "Wireless Mouse",
    "stock": 23
  }
}
```

## 4. Required module boundary

`InventoryServiceImpl` is intentionally **package-private**:

```java
@Service
class InventoryServiceImpl implements InventoryService { ... }
```

The Order module imports only:

```java
import edu.cit.aquino.inventory.InventoryService;
```

This prevents Order from depending on the concrete Inventory implementation. The implementation can change internally without making it part of the module's public API.

## 5. Test both paths

### Confirmed path

Use `P100` and quantity `2` (or another quantity that fits the current stock). The UI should display `CONFIRMED` and the remaining inventory.

### Rejected path

Use `P300` and quantity `1`, because its seed stock is `0`. The UI should display `REJECTED` with an insufficient-stock reason.

You can also use `P200` with quantity `11` to exceed its seed stock of `10`.

### Network tab evidence

The submission requires screenshots captured from your own browser. After testing each path:

1. Open DevTools → **Network**.
2. Submit the order.
3. Select the `orders` request.
4. Capture the request URL/method, request payload, HTTP response, and response JSON.
5. Add the screenshots to the repository and link them below.

Suggested files:

```text
README.md
screenshots/
├── confirmed-network.png
└── rejected-network.png
```

> Do not fabricate these screenshots. They need to come from the actual running application and your Supabase-backed database.

## 6. Tests

The backend includes unit tests for both the confirmed and rejected service paths:

```bash
cd backend
mvn test
```

The required manual end-to-end test is: **React → HTTP → OrderService → InventoryService (in-process) → Supabase Postgres**, followed by the Network-tab screenshots described above.

## Reflection (300–500 words)

### 1. In-process integration vs. microservices

In this project, Order and Inventory are separate logical modules but run inside the same Spring Boot process. Order calls the InventoryService interface directly, so the integration is a normal in-process Java method call. This gives us several things for free: there is no HTTP client configuration between the modules, no service discovery, no network timeout handling, no serialization of the method arguments and result, and no need to authenticate one internal service to another. A shared database transaction can also be coordinated much more simply because both modules participate in the same application and database connection context. The trade-off is that the modules are not independently deployable and they share the runtime and database resources.

If Inventory became a separate microservice, the direct Java call would become a network API call. We would need to add an HTTP client or messaging mechanism, request/response DTOs, service discovery or a configured service URL, authentication and authorization between services, timeout and retry policies, error handling for unavailable services, observability such as distributed tracing, and potentially distributed transaction or consistency strategies. Serialization and network latency would also become part of the design.

### 2. Why package-private InventoryServiceImpl matters

The package-private implementation enforces the intended module boundary at the Java visibility level. Order can see and depend on the `InventoryService` interface, but it cannot directly construct or reference `InventoryServiceImpl`. This keeps the implementation behind the Inventory module's public contract. If the implementation were public, Order could start depending on implementation-specific methods, fields, or behavior. That would make future refactoring harder because changes to the concrete class could break Order even when the interface contract stayed stable. The boundary is therefore not just documentation; the Java compiler helps enforce it.

### 3. When to extract Inventory into a microservice

I would extract Inventory when it has a strong reason to be independently deployed or scaled, such as substantially different traffic, a separate team owning it, stricter availability requirements, or a domain boundary that has become stable enough to justify operational overhead. The code would change from direct constructor injection of InventoryService to an HTTP client or messaging adapter. The Inventory API would need endpoints and DTOs, while Order would call that API rather than the in-process implementation. Configuration, authentication, retries, timeouts, observability, and failure handling would also need to be introduced. The database boundary would need reconsideration as well; ideally Inventory would own its inventory data rather than allowing another service to modify the same tables directly.
