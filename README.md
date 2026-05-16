# Yuno Payment Orchestration System

A production-grade simplified payment orchestration engine built with **Java 21 + Spring Boot 3.5**, inspired by Yuno's real-world architecture.

---

## Architecture

```
Client
  ↓  HTTP (POST /api/v1/payments)
PaymentController          ← Input validation, header extraction
  ↓
PaymentOrchestrationService ← Idempotency check, orchestration, retry/failover
  ↓
RoutingEngine              ← CARD → Provider A | UPI → Provider B
  ↓
ProviderConnector (A or B) ← External PSP call (Resilience4j retry + circuit breaker)
  ↓
PaymentRepository (JPA)   ← PostgreSQL persistence
IdempotencyStore (Redis)   ← Exactly-once processing guarantee
```

---

## Functional Requirements Implemented

| Feature | Implementation |
|---|---|
| Create Payment API | `POST /api/v1/payments` |
| Fetch Payment API | `GET /api/v1/payments/{id}` |
| Routing (CARD → A, UPI → B) | `RoutingEngine` with Strategy Pattern |
| Idempotency | Redis store keyed by `X-Idempotency-Key` header |
| Retry | Resilience4j `@Retry` (3 attempts, 500ms wait) |
| Failover | `processWithFailover()` in service layer |
| Circuit Breaker | Resilience4j `@CircuitBreaker` (opens at 50% failure rate) |
| Payment Status Tracking | `PENDING → PROCESSING → SUCCESS/FAILED` |
| Metrics | Micrometer + Prometheus (`/actuator/prometheus`) |

---

## Prerequisites

- **Java 21** ([download](https://adoptium.net/))
- **Maven 3.9+** (`mvn -v` to verify)
- **Docker** (for PostgreSQL + Redis)

---

## Installation & Setup

### 1. Start Infrastructure (PostgreSQL + Redis)

```bash
# Start PostgreSQL
docker run -d \
  --name yuno-postgres \
  -e POSTGRES_DB=yuno_payments \
  -e POSTGRES_USER=yuno \
  -e POSTGRES_PASSWORD=yuno_secret \
  -p 5432:5432 \
  postgres:16-alpine

# Start Redis
docker run -d \
  --name yuno-redis \
  -p 6379:6379 \
  redis:7-alpine
```

### 2. Create Database Schema

```sql
-- Connect to postgres and run:
CREATE TABLE payments (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key  VARCHAR(64) UNIQUE NOT NULL,
    merchant_id      VARCHAR(64) NOT NULL,
    amount           DECIMAL(19, 4) NOT NULL,
    currency         VARCHAR(3) NOT NULL,
    payment_method   VARCHAR(20) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    assigned_provider VARCHAR(20),
    provider_transaction_id VARCHAR(128),
    failure_reason   VARCHAR(512),
    created_at       TIMESTAMPTZ DEFAULT NOW(),
    updated_at       TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_payments_idempotency_key ON payments(idempotency_key);
CREATE INDEX idx_payments_merchant_id ON payments(merchant_id);
```

### 3. Clone & Build

```bash
git clone <your-repo-url>
cd payment-orchestration
mvn clean install -DskipTests
```

### 4. Run the Application

```bash
mvn spring-boot:run
```

The server starts on **http://localhost:8080**.

---

## Execution Guide

### Create a CARD Payment

```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: $(uuidgen)" \
  -d '{
    "merchantId": "merchant-001",
    "amount": 150.00,
    "currency": "USD",
    "paymentMethod": "CARD"
  }'
```

**Expected Response (201 Created):**
```json
{
  "paymentId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "SUCCESS",
  "assignedProvider": "PROVIDER_A",
  "providerTransactionId": "PA-A1B2C3D4"
}
```

### Create a UPI Payment

```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: $(uuidgen)" \
  -d '{
    "merchantId": "merchant-002",
    "amount": 500.00,
    "currency": "INR",
    "paymentMethod": "UPI"
  }'
```

### Fetch a Payment

```bash
curl http://localhost:8080/api/v1/payments/{paymentId}
```

### Test Idempotency (Same Key, Two Requests)

```bash
KEY=$(uuidgen)
# Both calls return the SAME payment — provider called only once
curl -X POST http://localhost:8080/api/v1/payments \
  -H "X-Idempotency-Key: $KEY" \
  -H "Content-Type: application/json" \
  -d '{"merchantId":"m1","amount":100.00,"currency":"USD","paymentMethod":"CARD"}'

curl -X POST http://localhost:8080/api/v1/payments \
  -H "X-Idempotency-Key: $KEY" \
  -H "Content-Type: application/json" \
  -d '{"merchantId":"m1","amount":100.00,"currency":"USD","paymentMethod":"CARD"}'
```

---

## Running Tests

```bash
# Run all tests (H2 + EmbeddedRedis — no Docker needed)
mvn test

# Run only a specific test category
mvn test -Dtest="ApplicationSanityTest"
mvn test -Dtest="PaymentIntegrationTest"
mvn test -Dtest="PaymentNegativeTest"
mvn test -Dtest="RoutingEngineUnitTest"

# Run with verbose output
mvn test -Dsurefire.useFile=false
```

---

## Test Case Documentation

### Sanity Tests (`ApplicationSanityTest`)

| ID | Test | Expected |
|---|---|---|
| SAN-001 | Spring context loads | No exceptions |
| SAN-002 | All core beans wired | Not null |
| SAN-003 | Database connection healthy | count() ≥ 0 |
| SAN-004 | RoutingEngine has both providers | PROVIDER_A, PROVIDER_B found |

**Classification:** Sanity

---

### Integration Tests (`PaymentIntegrationTest`)

| ID | Test | Method | Expected |
|---|---|---|---|
| INT-001 | Successful CARD payment | POST | 201, status=SUCCESS |
| INT-002 | UPI routes to Provider B | POST | 201, providerB called |
| INT-003 | Fetch payment by ID | GET | 200, correct data |

**Classification:** Integration (Regression)

---

### Negative Tests (`PaymentNegativeTest`)

| ID | Test | Scenario | Expected |
|---|---|---|---|
| NEG-001 | Missing merchantId | Validation | 400, field error |
| NEG-002 | Zero amount | Validation | 400, field error |
| NEG-003 | Missing idempotency header | Missing header | 400 |
| NEG-004 | Duplicate idempotency key | Retry scenario | 2xx, provider called once |
| NEG-005 | Primary provider down | Failover | 201, failover provider used |
| NEG-006 | All providers down | Cascading failure | 502 Bad Gateway |
| NEG-007 | Non-existent payment ID | GET | 404 Not Found |
| NEG-008 | Invalid currency format | Validation | 400, field error |
| NEG-009 | Malformed UUID in path | Path param | 400 |

**Classification:** Negative (Regression)

---

### Unit Tests (`RoutingEngineUnitTest`)

| ID | Test | Expected |
|---|---|---|
| UNIT-001 | CARD → Provider A | Correct connector returned |
| UNIT-002 | UPI → Provider B | Correct connector returned |
| UNIT-003 | Failover A → B | Provider B returned |
| UNIT-004 | Failover B → A | Provider A returned |
| UNIT-005 | All providers registered | No exception |

**Classification:** Sanity (Regression)

---

## Performance Considerations

- **Connection Pooling:** HikariCP (max 20 DB connections)
- **Redis timeouts:** 2s command timeout (fail-fast prevents thread starvation)
- **Circuit Breaker:** Opens after 50% failure rate, preventing provider flood
- **`@Transactional(readOnly = true)`** on GET operations: skips dirty-checking
- **DB Indexes:** on `idempotency_key` and `merchant_id` for O(log n) lookups
- **Metrics:** `payment.processing.duration` timer + `payment.success/failure` counters available at `/actuator/prometheus`

---

## Prompts Used During Development (Vibe Coding Log)

1. *"Scaffold a Spring Boot 3.2 Java 21 Maven project for a payment orchestration system with PostgreSQL, Redis, and Resilience4j. Follow this architecture: Controller → Service → RoutingEngine → ProviderConnectors → Repository + IdempotencyStore."*

2. *"Implement idempotency using Redis. The key is from X-Idempotency-Key header. If key exists, return cached payment. If not, process and store. Explain the belt-and-suspenders approach with a DB unique constraint as fallback."*

3. *"Implement retry and failover logic in PaymentOrchestrationService. Resilience4j handles micro-retries per provider. The service handles macro-failover (switch providers). Explain the difference in code comments."*

4. *"Generate three tiers of tests: Sanity (context loads), Integration (full HTTP → DB flow with MockMvc and @MockBean), and Negative (provider down, duplicate key, validation failures). Add detailed comments explaining how Mockito's when/thenReturn/thenThrow/verify work."*

5. *"Apply clean code: Strategy Pattern in RoutingEngine, separate DTO/Entity classes, GlobalExceptionHandler with typed exceptions, Lombok to reduce boilerplate, BigDecimal for money (never float), UUID PKs."*# Yuno-Payments
