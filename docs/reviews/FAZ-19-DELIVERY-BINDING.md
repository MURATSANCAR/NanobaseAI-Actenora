# FAZ-19 Delivery Binding

**Phase:** FAZ 19  
**Date:** 2026-07-25  
**Status:** Complete (InMemory delivery orders + auth HTTP; mail enqueue deferred to FAZ 20)

## 1. Faz özeti

Approval GRANTED sonrası dış gönderim ADR-010 ile gate’liydi ama order persist edilmiyor ve HTTP yüzeyi yoktu. Bu turda `DeliveryOrder` InMemory store + idempotent key eklendi, `DeliveryApi` order view döndürüyor ve auth-bound `POST/GET /delivery/orders` platform’a bağlandı. Recipient enqueue / MailHog SMTP worker bilerek FAZ 20’ye bırakıldı.

## 2. Akış

```text
Approval GRANTED (FAZ 18)
        ↓
POST /api/v1/delivery/orders  { approvalId, noteVersionId?, channel }
        ↓
ApprovalApi.isGrantedForSubject → READY DeliveryOrder (idempotent)
        ↓
GET /api/v1/delivery/orders/{orderId}
```

Pending/denied approval → `EXTERNAL_DELIVERY_BLOCKED` (409).

## 3. Bu turda değişenler

### Delivery module
- `DeliveryOrderRepository` + `InMemoryDeliveryOrderRepository`
- `ExternalDeliveryService` — persist + idempotency + audit
- `DeliveryOrderView` + `DeliveryApi.requestExternalDelivery` / `getOrder`
- `DeliveryModuleConfiguration` — order repo bean wiring

### Platform
- `DeliveryPlatformConfiguration` — AuditApi → DeliveryAuditPort
- `DeliveryAuthController` — DELIVERY_MANAGE, tenant from security context
- OpenAPI delivery order paths/schemas

## 4. Kabul matrisi

| Gereksinim | Durum |
|------------|--------|
| Gate: only GRANTED for version | ✓ |
| Persist READY order | ✓ InMemory |
| Idempotent re-request | ✓ |
| Auth HTTP create/get | ✓ |
| Permission DELIVERY_MANAGE | ✓ |
| Tenant isolation on get | ✓ |
| OpenAPI | ✓ |
| Recipient enqueue / worker | deferred (FAZ 20) |
| Real SMTP → MailHog | deferred |
| JDBC order repo | deferred |

## 5. Testler

- `ExternalDeliveryGateTest` — block + grant + idempotent id
- `DeliveryAuthBindingTest` (4): grant→order, pending block, permission, foreign tenant
- Regresyon: `ApprovalAuthBindingTest`, delivery module suite

```bash
export JAVA_HOME=$PWD/.tools/jdk-21
./mvnw -pl modules/delivery test
./mvnw -pl apps/platform-backend test -Dtest='DeliveryAuthBindingTest,ApprovalAuthBindingTest'
```

## 6. Bilinen riskler

- Order READY ≠ mail gönderildi; FAZ 20 enqueue/worker gerekir.
- `DeliveryModuleConfiguration` hâlâ scan ile gelir; platform sadece audit bridge + HTTP ekler.
- Channel normalize (lowercase) yalnızca idempotency key’de.
