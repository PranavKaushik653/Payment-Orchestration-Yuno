# Yuno Payment Orchestration System

A simplified payment orchestration engine built for the **Yuno Backend Developer (Java Core) Assessment**.

---

## What This Does

Accepts payment requests from merchants, routes them to the correct payment provider (CARD → Provider A, UPI → Provider B), handles retries and failover if a provider is down, and guarantees a payment is never processed twice — even if the same request is sent multiple times.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5.14 |
| Database | PostgreSQL 16 |
| Cache / Idempotency Store | Redis 7 |
| Build Tool | Maven 3.9+ |
| Resilience | Resilience4j (retry + circuit breaker) |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Metrics | Micrometer + Prometheus |

---

## Prerequisites

You need three things installed before starting. Check each one:

```bash
java -version
# Must print: openjdk 21 or higher
# Download from: https://adoptium.net/ (choose Java 21, any OS)

mvn -version
# Must print: Apache Maven 3.9 or higher
# Download from: https://maven.apache.org/download.cgi

docker --version
# Must print: Docker version 24 or higher
# Download from: https://docs.docker.com/get-docker/
```

If all three commands print a version number you are ready to proceed.

---

## Running the Application

### Step 1 — Start PostgreSQL and Redis with Docker

Run these two commands. They start both services in the background:

```bash
docker run -d --name yuno-postgres -e POSTGRES_DB=yuno_payments -e POSTGRES_USER=yuno -e POSTGRES_PASSWORD=yuno_secret -p 5432:5432 --health-cmd="pg_isready -U yuno" --health-interval=3s postgres:16-alpine

docker run -d --name yuno-redis -p 6379:6379 --health-cmd="redis-cli ping" --health-interval=3s redis:7-alpine
```

Wait about 10 seconds, then verify both containers are healthy:

```bash
docker ps
```

You should see `(healthy)` next to both `yuno-postgres` and `yuno-redis`. If it still says `(health: starting)`, wait a few more seconds and run `docker ps` again.

### Step 2 — Clone and Build

```bash
git clone https://github.com/YOUR_USERNAME/yuno-payment-orchestration.git
cd yuno-payment-orchestration
mvn clean package -DskipTests
```

Expected output ends with:
```
BUILD SUCCESS
```

If you see `BUILD FAILURE`, confirm `java -version` prints 21 or higher — that is the most common cause.

### Step 3 — Run the Application

```bash
mvn spring-boot:run
```

The application is ready when you see this line in the terminal output:
```
Started PaymentOrchestrationApplication in X.XXX seconds
```

> **Note:** The database schema (the `payments` table and its indexes) is created automatically on first startup. You do not need to run any SQL scripts manually.

### Step 4 — Confirm It Is Running

Open a new terminal tab and run:

```bash
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{"status":"UP"}
```

The application is running correctly. Proceed to the API usage section.

---

## API Usage

### Create a CARD Payment

```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: test-key-001" \
  -d '{
    "merchantId": "merchant-001",
    "amount": 150.00,
    "currency": "USD",
    "paymentMethod": "CARD"
  }'
```

Expected response (`201 Created`):
```json
{
  "paymentId": "a1b2c3d4-...",
  "status": "SUCCESS",
  "assignedProvider": "PROVIDER_A",
  "providerTransactionId": "PA-XXXXXXXX",
  "merchantId": "merchant-001",
  "amount": 150.00,
  "currency": "USD",
  "paymentMethod": "CARD"
}
```

`PROVIDER_A` in the response confirms that CARD routing worked correctly.

### Create a UPI Payment

```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: test-key-002" \
  -d '{
    "merchantId": "merchant-002",
    "amount": 500.00,
    "currency": "INR",
    "paymentMethod": "UPI"
  }'
```

`assignedProvider` will be `PROVIDER_B`, confirming UPI routing.

### Fetch a Payment by ID

Copy the `paymentId` UUID from any create response above and substitute it:

```bash
curl http://localhost:8080/api/v1/payments/{paymentId}
```

### Test Idempotency

This demonstrates that a customer cannot be charged twice if a merchant retries. Run this command **twice** without changing anything:

```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: idempotency-demo-key" \
  -d '{
    "merchantId": "merchant-demo",
    "amount": 200.00,
    "currency": "USD",
    "paymentMethod": "CARD"
  }'
```

Both responses will contain the **identical `paymentId`**. The second call returns the cached result — no new charge is created.

---

## API Documentation (Swagger UI)

With the application running, open this in your browser:

```
http://localhost:8080/swagger-ui/index.html
```

This shows the full interactive API documentation with request/response schemas. You can try the endpoints directly from the browser.

---

## Running the Tests

The test suite uses H2 (in-memory database) and embedded Redis, so **Docker does not need to be running to run the tests**.

```bash
mvn test
```

Expected output:
```
Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### What the Tests Cover

| Suite | Class | What It Tests |
|---|---|---|
| Sanity | `ApplicationSanityTest` | Context loads, all beans wired, DB connection healthy |
| Integration | `PaymentIntegrationTest` | Full HTTP flow for CARD payment, UPI payment, fetch by ID |
| Negative | `PaymentNegativeTest` | Validation errors, provider failure, 404, idempotency replay |
| Unit | `RoutingEngineUnitTest` | Routing rules and failover logic in isolation |

---

## Stopping and Cleaning Up

```bash
# Stop the application
Ctrl + C   (in the terminal where mvn spring-boot:run is running)

# Stop and remove the Docker containers
docker stop yuno-postgres yuno-redis
docker rm yuno-postgres yuno-redis
```

---

## Project Structure

```
src/main/java/com/yuno/payments/
├── controller/
│   └── PaymentController.java           # HTTP entry point — POST and GET endpoints
├── service/
│   └── PaymentOrchestrationService.java # Orchestration brain — idempotency + failover
├── routing/
│   └── RoutingEngine.java               # CARD → ProviderA, UPI → ProviderB
├── provider/
│   └── ProviderConnectors.java          # Provider A and B with retry + circuit breaker
├── idempotency/
│   └── IdempotencyStore.java            # Redis-backed exactly-once guarantee
├── model/
│   ├── Payment.java                     # JPA entity (PostgreSQL)
│   └── PaymentDTOs.java                 # Request and Response DTOs
├── repository/
│   └── PaymentRepository.java           # Spring Data JPA
└── exception/
    ├── PaymentExceptions.java            # Typed domain exceptions
    └── GlobalExceptionHandler.java       # Maps exceptions to HTTP status codes
```

---

## Architecture

```
Client
  ↓  POST /api/v1/payments  +  X-Idempotency-Key header
PaymentController            → validates input, extracts header
  ↓
PaymentOrchestrationService  → checks Redis for duplicate key
                             → saves PENDING record to PostgreSQL
  ↓
RoutingEngine                → CARD → PROVIDER_A  |  UPI → PROVIDER_B
  ↓
ProviderConnector            → calls provider (Resilience4j: 3 retries, 500ms wait)
  ↓  if all retries fail  ↓
  └─ resolveFailover()       → switches to the other provider
  ↓
PaymentRepository            → saves SUCCESS or FAILED status to PostgreSQL
IdempotencyStore             → stores idempotency key → paymentId in Redis (24h TTL)
```

---

## Demo Video

Will be put soon....

The video covers:
- Architecture walkthrough (code)
- Swagger UI tour
- Live Postman demo — happy paths, idempotency, and failure scenarios

---

## Troubleshooting

**`Connection refused` on port 5432 or 6379**
Docker containers are not running. Run `docker ps` to check. If you do not see `yuno-postgres` and `yuno-redis` listed, repeat Step 1.

**`BUILD FAILURE` during `mvn clean package`**
Run `java -version`. Must be Java 21 or higher. If you have multiple Java versions installed, set `JAVA_HOME` to point to Java 21.

**Port 8080 already in use**
Run the app on a different port:
```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=9090
```
Then replace `8080` with `9090` in all commands above.

**Tests pass but application fails to start**
Tests use an in-memory database and do not require Docker. The running application requires both PostgreSQL and Redis. Confirm Step 1 completed and `docker ps` shows both containers as `(healthy)`.


## Production Readiness & Future Improvements

This project is intentionally designed as a modular monolith first, focusing on correctness, reliability, and maintainability before introducing distributed-system complexity.

The current implementation already includes:
- provider routing and failover
- retries and circuit breakers
- Redis-backed idempotency
- PostgreSQL persistence
- stateless architecture
- resilience patterns using Resilience4j

To evolve this into a production-grade payment orchestration platform, the following improvements are planned.

---

### Reliability Improvements

| Improvement | Why It Matters |
|---|---|
| Correlation IDs | Enables end-to-end request tracing and easier production debugging |
| Structured JSON Logging | Improves centralized monitoring and log analysis |
| Advanced Retry Policies | Prevents aggressive retries during provider outages |
| Provider-specific Timeouts | Avoids thread exhaustion from slow providers |
| Bulkhead Isolation | Prevents one failing provider from impacting the whole system |
| Distributed Rate Limiting | Protects APIs from abuse and traffic spikes |

---

### Scalability Improvements

| Improvement | Why It Matters |
|---|---|
| Kafka-based Async Processing | Decouples API latency from provider response time |
| Redis Response Caching | Reduces repeated database lookups |
| Horizontal Scaling | Allows multiple application instances behind a load balancer |
| Read Replica Support | Scales read-heavy payment lookup traffic |
| Distributed Locking | Prevents race conditions in concurrent workflows |
| Outbox Pattern | Ensures reliable event publishing and consistency |

---

### Planned Async Payment Flow

```text
Merchant Request
    ↓
Save Payment as PENDING
    ↓
Publish Kafka Event
    ↓
Return Response Immediately

Background Worker
    ↓
Process Provider Logic
    ↓
Update Payment Status
    ↓
Trigger Merchant Webhook
```

### Benefits
- non-blocking request handling
- improved throughput
- better fault tolerance
- resilient provider failure handling
- scalable asynchronous workflows

---

### Observability & Monitoring

Planned observability improvements include:
- Prometheus metrics
- Grafana dashboards
- OpenTelemetry tracing
- centralized logging
- health checks and readiness probes
- correlation-based request tracing

These improvements help with:
- production monitoring
- incident debugging
- performance analysis
- operational visibility

---

### Cloud-Native Deployment Roadmap

The application is being designed for cloud-native deployment patterns using:
- Docker
- Docker Compose
- Kubernetes
- horizontal autoscaling
- rolling deployments
- externalized configuration

This enables:
- easier deployment
- scalable infrastructure
- fault tolerance
- zero-downtime releases

---

### Architectural Philosophy

This project intentionally follows a modular monolith architecture instead of premature microservice decomposition.

The goal is to:
- reduce operational complexity
- simplify debugging
- maintain strong transactional consistency
- improve development velocity

Internal modules are designed with clear boundaries so they can later evolve into independently deployable services if scaling requirements justify it.
