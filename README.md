# Modular Monolith Integration Lab — Order + Inventory

**Stack:** Java 17, Spring Boot, Spring JDBC, Supabase Postgres, React, Vite.

This project implements the lab as a **modular monolith**. `Order` and `Inventory` live inside one Spring Boot process and communicate through the `InventoryService` interface — there are no HTTP/network calls between the modules. The browser is the external client and calls `POST /api/orders` over HTTP.

## Project structure

```text
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
├── frontend/
│   ├── package.json
│   └── src/
│       ├── App.jsx
│       ├── index.css
│       └── main.jsx
├── .gitignore
├── README.md
├── ss1.png
├── ss2.png
└── ss3.png

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

## 5. Network tab evidence

### Confirmed path

![Confirmed order image](ss1.png)

### Rejected path
![Rejected order image](ss2.png)

### Supabase db
![Supabase db](ss3.png)


The required manual end-to-end test is: **React → HTTP → OrderService → InventoryService (in-process) → Supabase Postgres**, followed by the Network-tab screenshots described above.

## Reflection (300–500 words)

### 1. In-process integration vs. microservices

In this project, Order and Inventory are separate modules, but they run inside the same Spring Boot application. Order calls the `InventoryService` interface directly, so the integration is just a normal Java method call. Because they are in the same process, we don't need HTTP clients, service discovery, network timeouts, serialization, or authentication between the modules. Database transactions are also easier to handle since both modules use the same application and database context.

The downside is that the modules cannot be deployed or scaled independently. They also share the same runtime and database resources.

If Inventory became a separate microservice, the Java method call would become a network API call. We would need things like HTTP clients, DTOs, service URLs, authentication, timeouts, retries, error handling, and monitoring. Network latency and serialization would also become factors. Database transactions would be more complicated because the services would no longer share the same application context.

### 2. Why package-private `InventoryServiceImpl` matters

Making `InventoryServiceImpl` package-private helps enforce the boundary between the modules. Order can use the `InventoryService` interface, but it cannot directly access or create `InventoryServiceImpl`.

This prevents Order from becoming dependent on the implementation itself. If the implementation were public, Order could start using implementation-specific methods or behavior. That would make future changes harder because changing the implementation could break Order even if the interface stayed the same.

Basically, the interface is the public contract, while the actual implementation stays inside the Inventory module. Java's access rules help enforce this instead of relying only on developers to follow the intended structure.

### 3. When to extract Inventory into a microservice

I would extract Inventory into a microservice when there is an actual reason to do so, such as having much more traffic, needing to scale independently, having a separate team manage it, or needing different availability requirements.

The direct `InventoryService` call would then be replaced with an HTTP API or messaging system. Inventory would have its own endpoints and DTOs, while Order would communicate with it through the network. We would also need authentication, timeouts, retries, monitoring, and better error handling.

The database would need to be reconsidered as well. Ideally, Inventory would own its inventory data instead of allowing another service to directly modify its tables.

For this project, keeping both modules in the same Spring Boot application makes more sense. We still get a clear separation between Order and Inventory without adding the extra complexity that comes with microservices.
