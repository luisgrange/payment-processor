# Payment Processor

Sistema de processamento de pagamentos assíncrono construído para praticar (e demonstrar) padrões de mensageria e resiliência usados em sistemas financeiros reais: idempotência, retry com backoff exponencial, Dead Letter Queue e circuit breaker.

O fluxo simula um cenário comum em fintechs: uma API recebe uma solicitação de pagamento, publica o evento numa fila, e um worker assíncrono processa a cobrança junto a um "banco emissor" — que falha propositalmente parte das vezes, pra exercitar os mecanismos de resiliência.

---

## Arquitetura

```
                 ┌──────────────┐
   HTTP POST     │              │
 ───────────────▶│ PaymentController │
  /payments       │ (Spring Web) │
                 └──────┬───────┘
                        │ salva PENDING + publica evento
                        ▼
              ┌───────────────────┐
              │ Exchange: payments │
              │   (topic exchange) │
              └─────────┬──────────┘
                         │ routing key: payment.created
                         ▼
              ┌───────────────────┐
              │ Queue: payment.process │
              └─────────┬──────────┘
                         │ consume
                         ▼
              ┌───────────────────────────┐
              │      PaymentConsumer       │
              │  - checa idempotência      │
              │  - chama PaymentBankClient │
              │    (protegido por circuit  │
              │     breaker)               │
              └─────────┬──────────────────┘
                 sucesso │  falha
             ┌───────────┘  └───────────┐
             ▼                          ▼
   ┌──────────────────┐       ┌──────────────────────┐
   │ Queue: payment.ok │       │ Retry local (Spring    │
   └──────────────────┘       │ AMQP): 2s → 4s → 8s     │
                               │ depois: DLQ              │
                               └──────────┬───────────────┘
                                          ▼
                               ┌──────────────────────┐
                               │ Queue: payment.dlq     │
                               └──────────────────────┘
```

**Persistência:** PostgreSQL guarda o estado de cada pagamento (`PENDING` → `PROCESSING` → `SUCCESS`/`FAILED`), com `external_id` único garantindo idempotência no nível de banco.

---

## Padrões demonstrados

| Padrão | Onde |
|---|---|
| **Idempotência na entrada** | `PaymentController` rejeita `externalId` duplicado com `409 Conflict` |
| **Idempotência no consumo** | `PaymentConsumer` ignora mensagens de pagamentos que não estão mais `PENDING` (evita reprocessar em caso de redelivery) |
| **Retry com backoff exponencial** | `RabbitRetryConfig`, via API nativa do Spring AMQP 4 (2s → 4s → 8s, 3 tentativas) |
| **Dead Letter Queue** | Fila `payment.process` configurada com `x-dead-letter-exchange`; mensagens que esgotam o retry vão pra `payment.dlq` automaticamente |
| **Circuit Breaker** | `PaymentBankClient`, via Spring Cloud CircuitBreaker + Resilience4j, evitando martelar um serviço externo já detectado como indisponível |
| **DTOs separados de entidade/evento** | Contrato de API (`PaymentRequest`/`PaymentResponse`) e contrato de mensageria (`PaymentCreatedEvent`) são desacoplados da entidade JPA `Payment` |

---

## Stack

- **Java 21**
- **Spring Boot 4.1** (Web, AMQP, Data JPA, Actuator)
- **Spring Cloud CircuitBreaker (Resilience4j)**
- **RabbitMQ 3.13** (management plugin)
- **PostgreSQL 16**
- **Docker / Docker Compose**

---

## Como rodar

### Pré-requisitos
- Docker e Docker Compose
- Java 21
- Maven

### Passos

```bash
# 1. Sobe RabbitMQ + Postgres
docker compose up -d

# 2. Confirma que os containers estão saudáveis
docker compose ps

# 3. Roda a aplicação
mvn spring-boot:run
```

A aplicação sobe em `http://localhost:8080`.

O RabbitMQ Management UI fica em `http://localhost:15672` (usuário/senha: `admin`/`admin`) — útil pra observar as filas em tempo real.

---

## Endpoints

### Criar pagamento

```bash
curl -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -d '{"customerId":"cust-001","amount":150.00,"externalId":"ext-001"}'
```

Resposta: `202 Accepted` — o pagamento foi aceito, mas ainda será processado de forma assíncrona.

Reenviar o mesmo `externalId` retorna `409 Conflict`.

### Consultar status

```bash
curl http://localhost:8080/payments/ext-001
```

Retorna o pagamento com seu `status` atual (`PENDING`, `PROCESSING`, `SUCCESS` ou `FAILED`).

---

## Observando o fluxo

- **RabbitMQ UI** → aba *Queues*: acompanhe mensagens passando por `payment.process`, `payment.ok` e, em caso de falha persistente, `payment.dlq`.
- **Banco de dados**:
  ```sql
  SELECT external_id, status, created_at, updated_at FROM payments;
  ```
- **Actuator**:
    - `GET /actuator/health` — inclui o estado do circuit breaker (`CLOSED`, `OPEN`, `HALF_OPEN`)
    - `GET /actuator/metrics` — métricas gerais da aplicação

O `PaymentBankClient` simula falha em ~30% das chamadas, então rodar várias requisições seguidas é o suficiente pra ver o retry e, eventualmente, uma mensagem chegando na DLQ.

---

## Estrutura do projeto

```
payment-processor/
├── docker-compose.yml
├── pom.xml
└── src/main/java/com/paymentprocessor/demo/
    ├── api/
    │   ├── PaymentController.java
    │   ├── GlobalExceptionHandler.java
    │   ├── PaymentAlreadyExistsException.java
    │   ├── PaymentNotFoundException.java
    │   └── dto/
    │       ├── PaymentRequest.java
    │       └── PaymentResponse.java
    ├── domain/
    │   ├── entities/Payment.java
    │   ├── PaymentStatus.java
    │   └── PaymentRepository.java
    ├── messaging/
    │   ├── RabbitMQConfig.java
    │   ├── RabbitRetryConfig.java
    │   ├── PaymentProducer.java
    │   ├── PaymentConsumer.java
    │   └── dto/PaymentCreatedEvent.java
    ├── external/
    │   ├── PaymentBankClient.java
    │   ├── BankCircuitBreakerConfig.java
    │   ├── BankUnavailableException.java
    │   └── BankChargeException.java
    └── Application.java
```

---

## Decisões de design (e trade-offs)

- **Retry local (Spring AMQP) em vez de delay queue no broker**: mais simples de implementar e entender, mas a thread do listener fica ocupada durante o backoff. Em cenários de alto volume, uma alternativa seria retry não-bloqueante via `x-message-ttl` + dead-letter chain, liberando a thread entre tentativas.
- **`ddl-auto: update`**: usado para acelerar o setup local. Em produção, o correto seria migrations versionadas (Flyway/Liquibase).
- **`BigDecimal` para valores monetários**: evita os erros de arredondamento binário de `double`/`float`, prática padrão em sistemas financeiros.
- **Status `STRING` no enum (não `ORDINAL`)**: protege contra corrupção de dados caso o enum seja reordenado no futuro.

---

## Possíveis extensões

- Consumer dedicado para `payment.dlq`, disparando alertas/notificações.
- Testes de integração com Testcontainers (RabbitMQ + Postgres reais).
- Métricas customizadas (contadores de sucesso/falha/DLQ) expostas via Micrometer.
- Branch alternativa trocando RabbitMQ por Kafka, para comparação prática entre os dois modelos de mensageria.