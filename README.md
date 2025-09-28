# 🎟️ 초고동시성 티켓 예매 시스템 (Ticketing Project)

> **목표**  
> 동시 다발적인 좌석 클릭에도 **oversell 없이** 안정적으로 예약/주문/결제를 처리하는 백엔드 중심 학습 프로젝트.  
> Redis를 **좌석 상태의 단일 진실(Source of Truth)** 로 삼고, Kafka로 주문 사가 흐름을 느슨 결합으로 구성했습니다.  
> 운영 관점에서는 **EC2 + Docker Compose + Nginx**로 배포, **Prometheus/Grafana/Jaeger**로 관측을 시도했습니다.

---

## 📚 목차
- [프로젝트 한눈에](#프로젝트-한눈에)
- [아키텍처](#아키텍처)
- [핵심 도메인 & 기능](#핵심-도메인--기능)
- [API Contracts](#-api-contracts-v10--ec2--nginx-환경)
- [백엔드 구현 세부(계약/동시성/사가/오류)](#백엔드-구현-세부계약동시성사가오류)
- [공통 모듈 (common)](#공통-모듈-common)
- [성능 & 관측(사실 위주)](#성능--관측사실-위주)
- [배포(EC2/Compose/Nginx/레지스트리)](#배포ec2composenginx레지스트리)
- [CI/CD(GitHub Actions)](#cicdgithub-actions)
- [로컬 실행](#로컬-실행)
- [폴더 구조](#폴더-구조)
- [고찰 & 배운 점](#고찰--배운-점)
- [한계 & 이후 계획](#한계--이후-계획)

---

## 프로젝트 한눈에
- **백엔드(MSA)**: `catalog`(조회/SSE) · `reservation`(좌석 hold/confirm) · `order`(주문 + Outbox, 사가 오케스트레이션) · `payment`(모의 결제)  
- **데이터/브로커**: MySQL 8, Redis 7(TTL+Lua), Kafka 7.6(+ Zookeeper)  
- **인프라**: AWS EC2, Docker Compose, Nginx Reverse Proxy  
- **관측**: Prometheus/Grafana(메트릭 대시보드), Jaeger(분산추적 **시도/부분 계측**)  
- **프론트엔드**: React 18 + Vite + TypeScript, TanStack Query, Zustand, SSE 클라이언트  
- **성능 목표(SLO)**: Oversell 0, p95 < 300ms, Error < 0.5%  *(학습 프로젝트 기준 측정/개선 진행)*

### 📸 프로젝트 시연/결과물
https://github.com/user-attachments/assets/17d0211e-2712-4a98-8759-69cf3e6f6b64
- 좌석 쟁탈 테스트 동영상 (실시간 1000명 동시 접속 시뮬레이션)

---

## 아키텍처
<img width="3840" height="1807" alt="티켓팅 프로젝트 아키텍처" src="https://github.com/user-attachments/assets/16ac1f9a-df29-4135-b13c-5c2ca17517c5" />

---

## 핵심 도메인 & 기능
- **이벤트/좌석 조회 (Catalog)**  
  - `GET /api/events`, `GET /api/events/{id}`, `GET /api/events/{id}/seats`  
  - `GET /api/events/{id}/seats/stream` (SSE)로 좌석 상태 실시간 송신
- **좌석 예약/홀드 (Reservation)**  
  - `POST /api/reservations {eventId, seatId, holdSeconds}` → 201/409  
  - `POST /api/reservations/{eventId}/{seatId}/confirm|extend`, `DELETE ...`  
- **주문 (Order)**  
  - `POST /api/orders` (헤더: `Idempotency-Key` 필수)  
  - Outbox → Kafka 발행, 사가 오케스트레이션(성공/실패 보상)  
- **결제 (Payment)**  
  - `POST /api/payments/authorize {orderId}` (모의 지연/성공/실패 확률)  
  - 이벤트 직접 발행 X (Order가 결과를 받아 사가/Outbox 처리)
- **표준 오류 바디**  
  ```json
  { "code":"RESERVATION_CONFLICT", "message":"...", "traceId":"..." }
  ```

---

## 📄 API Contracts (v1.0 – EC2 + Nginx 환경)

### 🔹 공통 규약
- **Base URL**
  - Catalog: `http://<EC2 Public IP>/ticketing/catalog/api/...`
  - Reservation: `http://<EC2 Public IP>/ticketing/reservation/api/...`
  - Order: `http://<EC2 Public IP>/ticketing/order/api/...`
  - Payment: `http://<EC2 Public IP>/ticketing/payment/api/...`

- **오류 응답 바디 (표준)**
```json
{
  "code": "RESERVATION_CONFLICT",
  "message": "Seat already held",
  "traceId": "f1a2b3c4-5678-90ab-cdef-1234567890ab"
}
```

### 🔹 공통 헤더
- **요청**: `Idempotency-Key` → `POST /ticketing/order/api/orders` 필수  
- **응답**: `Trace-Id` → 모든 API 응답 헤더에 포함 (TraceIdFilter)

---

### 🔹 Catalog 모듈
**Base URL:** `http://<EC2 Public IP>/ticketing/catalog/api`

- **이벤트**
  - `GET /events` → 이벤트 목록 조회
  - `GET /events/{id}` → 단일 이벤트 조회

- **좌석**
  - `GET /events/{id}/seats` → 좌석 맵 조회
  - `GET /events/{id}/seats/stream` → 좌석 상태 스트리밍 (SSE)

- **내부 좌석 상태 업데이트**
  - `POST /internal/seat-update`  
    Reservation 모듈이 호출 → Catalog SSE 반영

---

### 🔹 Reservation 모듈
**Base URL:** `http://<EC2 Public IP>/ticketing/reservation/api`

- `POST /reservations` → 좌석 홀드  
- `POST /reservations/{eventId}/{seatId}/extend` → 홀드 연장  
- `DELETE /reservations/{eventId}/{seatId}` → 좌석 해제  

---

### 🔹 Order 모듈
**Base URL:** `http://<EC2 Public IP>/ticketing/order/api`

- `POST /orders`  
  Header: `Idempotency-Key` 필수 → 주문 생성  

- `GET /orders/{id}` → 주문 조회  

---

### 🔹 Payment 모듈
**Base URL:** `http://<EC2 Public IP>/ticketing/payment/api`

- `POST /payments/authorize`  
  모의 결제 (랜덤 지연 + 80:20 성공/실패)  

---

### 🔹 Health Check
- 모든 모듈 공통:  
  `GET /actuator/health`  
  예시: `http://<EC2 Public IP>/ticketing/catalog/actuator/health`

응답 예시:
```json
{ "status": "UP" }
```

---
  
## 백엔드 구현 세부(계약/동시성/사가/오류)

### 1) 계약 우선(Contract-first)
- API 스펙과 오류코드, Idempotency, traceId 노출을 먼저 고정 후 구현.
- **Idempotency**: `/api/orders`는 동일 키 재시도 시 최초 결과만 반환.
- **Trace-Id**: 모든 서비스 응답 헤더/로그에 추적 ID 포함(분산추적 시도).

### 2) 동시성 제어(좌석은 Redis가 진실)
- 좌석 클릭 → Redis Lua로 원자적 `SETNX + TTL` 홀드.
- TTL 만료 시 자동 해제, 확정 시만 MySQL 반영(**RDB 상태는 캐시적**).
- 장점: DB 락 경합 최소화, 초고동시성에서 oversell 예방.

### 3) 사가 패턴(오케스트레이터 = Order)
- `Order`가 결제 요청 → `Payment` 응답(성공/실패/지연) 수신 →  
  Outbox로 결과 이벤트 발행 → `Reservation`이 확정 또는 보상(홀드 해제).
- 네트워크/지연/실패에 내성을 갖도록 **재시도/서킷/타임아웃 기본값**을 설계 지향.

### 4) 표준 오류 & 재시도 UX
- 409(CONFLICT)/410(GONE)/422(UNPROCESSABLE) 등 명확한 원인 전달.
- 프론트는 남은 시간 카운트다운·토스트·재시도 가이드로 사용자 경험 유지.

---

## 공통 모듈 (common)

백엔드 4개 서비스(`catalog`, `reservation`, `order`, `payment`)는 모두 동일한 규약을 따라야 하므로,  
반복되는 코드를 **common 모듈**로 분리했습니다.

### 📌 구현 내용

- **ApiException**
  - 서비스 내에서 비즈니스 에러 발생 시  
    `throw new ApiException(code, message)` 형태로 사용
  - 모든 모듈에서 동일한 에러 코드 규약 유지

- **ErrorResponse**
  - 공통 오류 응답 바디를 `record`로 정의  

```
{ "code": "RESERVATION_CONFLICT", "message": "이미 예약된 좌석입니다", "traceId": "..." }  
```

---

### 📌 Errors
- 에러 코드 상수 정의
- 예:
  - `RESERVATION_CONFLICT` (중복 예약 충돌)  
  - `RESERVATION_EXPIRED` (홀드 만료)  
  - `VALIDATION_FAILED` (입력 검증 실패)

---

### 📌 TraceIdFilter
- 모든 응답 헤더에 `Trace-Id` 포함  
- **FE ↔ BE ↔ 로그** 전 구간 동일 ID 추적
- OpenTelemetry `Span`에서 추출하여 헤더·로그·Request attribute에 삽입
- 로그 MDC `%X{traceId}`로 출력되어 Kibana/Elastic, Grafana 등에서 연계 가능

---

## 성능 & 관측(사실 위주)
- **부하 테스트(k6)**: 좌석 다중 클릭/주문 흐름에서 **oversell 0건**을 목표로 반복 점검.
- DB 경합을 제거하고 Redis-only 흐름으로 단순화했을 때 성능이 개선됨을 확인.
- **p95**: 시나리오/환경에 따라 변동 (목표 300ms 이하).
- **Prometheus/Grafana**: 기본 JVM/HTTP 메트릭 수집 및 대시보드 구성.
- **Jaeger(분산추적)**: OpenTelemetry 연동 시도. 일부 스팬은 보이나,  
  “주문 1건 전체 체인”의 완전한 단일 Trace 연결은 미완(추후 보완 예정).

---

## 배포(EC2/Compose/Nginx/레지스트리)

### 1) EC2 + Docker Compose(프로덕션 구성 예)
- **데이터/브로커**: `mysql:8.0`, `redis:7`, `cp-zookeeper:7.6.1`, `cp-kafka:7.6.1`
- **관측**: `jaegertracing/all-in-one:1.57`, `prom/prometheus`, `grafana/grafana`
- **백엔드 서비스**: `ghcr.io/<user>/{catalog|reservation|order|payment}:latest`
- **Nginx**: 단일 진입점, `/ticketing/*` 라우팅

### 2) 컨테이너 이미지
- 각 서비스는 **Jib 또는 멀티스테이지 Dockerfile**로 빌드 → GHCR에 푸시.
- Compose는 `image: ghcr.io/...:latest`로 원클릭 기동.

### 3) Nginx 리버스 프록시(요약)

```Nginx
location /ticketing/catalog/ {
  proxy_pass http://catalog/ticketing/;
  proxy_http_version 1.1;             # SSE는 HTTP/1.1 keep-alive 필요
  proxy_set_header Connection '';
  proxy_set_header Cache-Control 'no-cache';  # 캐싱 방지

  proxy_buffering off;                # Nginx 응답 버퍼링 해제 (SSE 실시간성 보장)
  proxy_cache off;                    # 캐시 사용 금지
  chunked_transfer_encoding off;
  add_header X-Accel-Buffering no;    # Nginx가 응답 버퍼링하지 않도록 명시

  proxy_read_timeout 3600s;           # 스트림 연결 장시간 유지
  proxy_send_timeout 3600s;
}

// 📌 **왜 이렇게 설정했나?**  
// - Catalog 모듈은 `GET /events/{id}/seats/stream` 으로 **좌석 상태 SSE 스트림**을 제공함  
// - SSE 특성상 **연결을 장시간 유지**하고, 데이터가 오면 **바로바로 전달**되어야 함  
// - 기본 Nginx 설정은 버퍼링/캐싱 때문에 메시지가 지연되거나 잘려 나갈 수 있음  
// - 따라서 `proxy_buffering off`, `X-Accel-Buffering no`, `no-cache` 등을 적용해  
//  **좌석 선점/해제 이벤트가 FE에 실시간 도착**하도록 보장한 것

location /ticketing/reservation/ {
  proxy_pass http://reservation/ticketing/;
}

location /ticketing/order/ {
  proxy_pass http://order/ticketing/;
}

location /ticketing/payment/ {
  proxy_pass http://payment/ticketing/;
}

# Health check
location /healthz {
  return 200 'ok';
  add_header Content-Type text/plain;
}
```
## CI/CD (GitHub Actions)

- **CI**: PR마다 **빌드 → 테스트 → 이미지 빌드**까지 수행.  
- **CD**: main 머지 시 이미지 푸시(GHCR) 후,  
  EC2에서 `docker compose pull && docker compose up -d`로 롤링.  
- **실패 대비**: 롤백은 이전 태그로 `docker compose up -d` 재기동.

---

## 로컬 실행

```bash
# 인프라 기동
docker compose -f docker-compose.prod.yml up -d mysql redis zookeeper kafka jaeger prometheus grafana

# 백엔드 서비스 기동
docker compose -f docker-compose.prod.yml up -d catalog reservation order payment nginx

# 프론트엔드 실행
cd frontend && pnpm install && pnpm dev
```
## 폴더 구조

```bash
/frontend
  ├─ src/ (pages, components, features/{events|seats|cart|orders}, api, stores, libs)
  ├─ e2e/ (Playwright)
  └─ vite.config.ts, tailwind.config, tsconfig, .eslintrc.cjs

/ticketing
  ├─ catalog/
  ├─ reservation/
  ├─ order/
  ├─ payment/
  ├─ common/
  ├─ infra/           # docker-compose, k6, grafana, prometheus.yml
  └─ docs/            # 일지(Daily Logs), 아키텍처, API.md
```
## 고찰 & 배운 점

- **동시성 제어**: DB 트랜잭션만으로는 좌석 경쟁을 커버하기 어렵다.  
  Redis를 진실 소스로 두고 TTL/Lua로 원자적으로 제어해야 한다.  

- **사가 오케스트레이션**: Payment는 “응답만”, 보상은 Order가 책임진다는 역할 구분을 체득.  

- **관측성**: Jaeger는 “붙여보니 더 할 일이 보였다”.  
  스팬 전파·샘플링·로그 상관관계 설계가 중요.  

- **배포 현실성**: Kubernetes까지는 못 갔지만, EC2+Compose만으로도 충분히 학습/데모 가능.  

- **계약 우선**: Idempotency-Key, 표준 오류, Trace-Id 등  
  “계약”이 흔들리면 프론트/백 동시 개발이 어렵다.  

---

## 한계 & 이후 계획

- **Jaeger**: “주문 1건 전체 Trace” 완전 연결 미완 →  
  전 서비스 OTel agent 표준화, B3/W3C 전파 점검 필요.  

- **Resilience**: 서킷브레이커/재시도/타임아웃은 기본값 수준 →  
  장애 주입(k6/chaos)로 톤업 예정.  

- **보안**: 간단 JWT/토큰 검증만 →  
  권한/스코프·비밀 관리(SSM/Secrets Manager) 적용 필요.  

- **운영**: 단일 EC2 →  
  멀티 AZ, 헬스체크/오토 리커버리까지 확장 검토.  

---

## SLO & DoD

- **SLO**: Oversell 0, p95 < 300ms, Error < 0.5%  
- **DoD**: 좌석 클릭 → 홀드 → 결제 → 확정 플로우가 데모 환경에서 일관 재현.  
  오류 응답은 표준 바디와 Trace-Id 제공, CI 성공.  

---

📌 **프로젝트 링크:** [tpdus751/ticketing-project](https://github.com/tpdus751/ticketing-project)
