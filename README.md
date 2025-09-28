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

## 성능 & 관측
- <img width="1913" height="1067" alt="W01 D03 image k6 result" src="https://github.com/user-attachments/assets/825ca4f0-9c74-4c25-90bb-524b643fb44e" />
- **부하 테스트(k6)**: 좌석 다중 클릭/주문 흐름에서 **oversell 0건**을 목표로 반복 점검.
- <img width="1918" height="554" alt="image" src="https://github.com/user-attachments/assets/c26c57bf-3a33-4de7-b585-b780b2c86f2d" />
- <img width="1916" height="557" alt="image" src="https://github.com/user-attachments/assets/40135de6-d74d-402a-9cc1-3ad5aba482b2" />
- **Jaeger(분산추적)**: OpenTelemetry 연동, DB 경합을 제거하고 Redis-only 흐름으로 단순화했을 때 성능이 개선됨을 확인.(이미지는 가장 오래걸린 요청/응답)
- <img width="1545" height="1032" alt="image" src="https://github.com/user-attachments/assets/762632d5-a032-4c1c-8fb2-106e3e8a05a1" />
- **Prometheus/Grafana**: 기본 JVM/HTTP 메트릭 수집 및 대시보드 구성.

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

// 📌 catalog : 왜 이렇게 설정했나?  
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

### 🔹 파이프라인 개요
- **CI**  
  - main 브랜치로 PR 생성 시 **빌드 → 테스트 → 이미지 빌드**까지 자동 수행  
- **CD**  
  - main 브랜치에 머지(push)되면  
    1. Jib으로 모듈별(Dockerfile 불필요) **이미지 빌드 후 GHCR 푸시**  
    2. EC2 접속 → 최신 이미지 pull → `docker compose up -d` 재기동 (롤링 배포)  

- **실패 대비**  
  - 배포 실패 시, 이전 태그 이미지로 `docker compose up -d` 실행해 롤백 가능  

---

### 🔹 GitHub Actions 워크플로우 예시 (`.github/workflows/deploy.yml`)

```yaml
name: Deploy Ticketing Project (BE only)

on:
  push:
    branches: [ "main" ]   # main 브랜치 push 시 자동 배포

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    steps:
      # 1. 코드 체크아웃
      - name: Checkout repository
        uses: actions/checkout@v3

      # 2. JDK 17 세팅
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'

      # 3. Reservation 모듈 빌드 & 푸시
      - name: Build & Push Reservation Image
        run: |
          cd ticketing
          ./gradlew :reservation:jib \
            -Djib.to.image=ghcr.io/tpdus751/reservation:latest \
            -Djib.to.auth.username=tpdus751 \
            -Djib.to.auth.password=${{ secrets.GHCR_TOKEN }} \
            --no-configuration-cache

      # 4. Order 모듈 빌드 & 푸시
      - name: Build & Push Order Image
        run: |
          cd ticketing
          ./gradlew :order:jib \
            -Djib.to.image=ghcr.io/tpdus751/order:latest \
            -Djib.to.auth.username=tpdus751 \
            -Djib.to.auth.password=${{ secrets.GHCR_TOKEN }} \
            --no-configuration-cache

      # 5. Payment 모듈 빌드 & 푸시
      - name: Build & Push Payment Image
        run: |
          cd ticketing
          ./gradlew :payment:jib \
            -Djib.to.image=ghcr.io/tpdus751/payment:latest \
            -Djib.to.auth.username=tpdus751 \
            -Djib.to.auth.password=${{ secrets.GHCR_TOKEN }} \
            --no-configuration-cache

      # 6. Catalog 모듈 빌드 & 푸시
      - name: Build & Push Catalog Image
        run: |
          cd ticketing
          ./gradlew :catalog:jib \
            -Djib.to.image=ghcr.io/tpdus751/catalog:latest \
            -Djib.to.auth.username=tpdus751 \
            -Djib.to.auth.password=${{ secrets.GHCR_TOKEN }} \
            --no-configuration-cache

      # 7. EC2 접속 & 배포
      - name: Deploy to EC2
        uses: appleboy/ssh-action@v0.1.10
        with:
          host: ${{ secrets.EC2_HOST }}      # EC2 퍼블릭 IP
          username: ubuntu
          key: ${{ secrets.EC2_SSH_KEY }}    # pem 파일 내용
          script: |
            cd /srv/ticketing/ticketing-project
            git fetch origin
            git checkout main
            git pull origin main
            cd ticketing/infra
            sudo docker compose -f docker-compose.prod.yml pull
            sudo docker compose -f docker-compose.prod.yml up -d
```

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

이번 프로젝트는 Day01 ~ Day05를 포함해 총 15일치 작업 로그를 남기며, 초고동시성 티켓 예매 시스템의 FE/BE를 동시에 발전시킨 과정이었다. 각 단계에서의 핵심 교훈을 정리하면 다음과 같다.

---

### 1. 프로젝트 초기 세팅 (W1 D01~D02)
- **BE**: Spring Boot + MySQL + Redis + Jaeger 환경을 docker-compose로 올리고, Flyway 마이그레이션으로 DB 스키마와 시드 데이터를 고정.  
- **FE**: Vite+React+TS 기반 UI 뼈대와 라우팅 구축, React Query로 API 연동.  
- **교훈**: FE/BE 계약이 조금만 어긋나도(`date` vs `dateTime`) 즉시 장애로 이어짐. **계약 동기화와 초기 라우팅 고정**이 얼마나 중요한지 체감했다.

---

### 2. 좌석 예약/정합성 확보 (W1 D03~D04)
- **Redis + Lua**로 좌석 선점(hold) 구현 → oversell 방지.  
- **표준 오류 바디 + Trace-Id 노출**로 FE/BE/로그 추적 일관성 확보.  
- **Confirm API** 도입으로 Redis 상태를 DB SOLD로 반영, 사용자에게 카운트다운과 상태 동기화 제공.  
- **교훈**:  
  - Redis는 단순 캐시가 아니라 **실시간 상태 관리** 도구로 쓸 수 있다.  
  - 409/410/422 같은 에러 코드도 도메인 로직에선 정상 시나리오가 될 수 있다.  
  - FE-Backend 동기화 없이는 데이터 정합성이 깨진다.

---

### 3. 관측성과 성능 가시화 (W1 D05)
- **Micrometer Observation**으로 DB/Redis 호출까지 자식 span으로 기록.  
- **MDC TraceId 로깅**으로 Jaeger trace와 로그를 매칭.  
- **Grafana 대시보드**에 Hold/Confirm 성공률, p95 레이턴시, 에러율 패널 추가.  
- **교훈**: 추상적 메트릭(Grafana)과 구체적 실행 흐름(Jaeger)을 함께 보면 시스템을 **하나의 생명체처럼** 이해할 수 있다.

---

### 4. MSA 전환 & FE 데이터 캐싱 (W2 D06)
- Monolith → **Catalog/Reservation 모듈 분리**, API_BASE 이원화.  
- FE에 **TanStack Query** 캐시 키 설계 적용 → invalidateQueries로 좌석맵만 갱신.  
- Redis TTL 기반 동시성 제어를 MSA 구조로도 유지.  
- **교훈**: MSA 전환은 기능 추가보다 **동등 기능 유지**가 관건. 작은 CORS/BASE 설정 차이도 대장애가 된다.

---

### 5. 주문/결제 플로우 & SSE 안정화 (W2 D07~D08)
- **Outbox 패턴**으로 Kafka 발행 보장, 실패 시 재처리 가능.  
- **Idempotency-Key**로 중복 주문 방지.  
- **다좌석 주문** 지원, FE CartDrawer와 연동.  
- **SSE Last-Event-ID**로 재연결 시 이벤트 누락 방지.  
- **Saga + Payment 연계** → Order → Payment → Reservation → Catalog → FE SSE까지 end-to-end 확인.  
- **교훈**:  
  - 분산 환경에서 **멱등성과 이벤트 재처리**가 핵심이다.  
  - 작은 버그(Emitter completed)가 실시간성 신뢰도 전체를 무너뜨린다.  
  - Polling도 가능하지만, 실시간 업데이트는 결국 SSE/WS가 정석이다.

---

### 6. 트레이스 전파와 한계 (W2 D08 중간점검)
- Kafka Headers 기반 traceparent 추출/전파 시도, Micrometer/OTel 연계 실험.  
- Scheduler 기반 Worker는 trace context가 끊겨 backlog 발생.  
- **교훈**: 완벽한 end-to-end trace는 쉽지 않다. **TraceId 로그 기반 모니터링**이 현실적 대안일 수 있다.

---

### 7. API 계약 고정 (API.md, Day01 9/13)
- **오류 표준화**, **Idempotency-Key 헤더**, **Trace-Id 응답 헤더**를 계약 수준에서 문서화.  
- **교훈**: 계약이 흔들리면 FE/BE 동시 개발이 불가능하다. **계약 우선 원칙**이 프로젝트 성공의 전제였다.

---

### 8. EC2 배포 및 CI/CD (Day02~Day03)
- **EC2 t3.medium**에 Docker Compose 배포.  
- **GHCR 이미지 빌드 & pull** 파이프라인 구축.  
- **GitHub Actions CI/CD** → main push 시 자동 빌드/배포.  
- **교훈**:  
  - `localhost` 대신 **서비스명:포트**로 컨테이너 간 통신해야 한다.  
  - SSE 프록시는 단순 버퍼링 해제만으로는 부족, Nginx에 세부 옵션이 필요하다.  
  - CI/CD는 단순 자동화가 아니라 **Fail-safe(알림/롤백)**까지 포함해야 실용적이다.

---

### 9. 부하 테스트 & 병목 분석 (Day04~Day05)
- **k6 50~1000 VU 테스트** → DB is-sold 쿼리 직렬화가 병목.  
- max_connections 조정에도 한계, p95 9s 이상, 실패율 90% 이상.  
- **DB → Redis-only 전환**, SSE publish 비동기화로 API 응답 단축.  
- 최종적으로 **~1.4s 안정화** 달성.  
- **교훈**:  
  - 단순 커넥션 풀 확대는 한계가 있다. **DB 의존 제거**가 핵심.  
  - Redis Lua도 단일 병목이 될 수 있으므로 튜닝이 필요하다.  
  - 응답성 개선은 단순 속도 문제가 아니라 **UX 신뢰성** 문제다.  
  - 부하테스트는 항상 **가장 오래 걸린 구간**을 기준으로 병목을 잡아야 한다.

---

## 최종 총괄
- **동시성 제어**: DB 트랜잭션만으로는 부족, Redis TTL/Lua가 유일한 답.  
- **사가 오케스트레이션**: Payment는 응답만, Order가 보상 책임을 갖는 분리 설계.  
- **관측성**: Jaeger+Grafana+로그 TraceId로 “보이는 서비스”를 구현.  
- **배포 현실성**: Kubernetes까지는 못 갔지만, EC2+Compose+CI/CD로도 충분히 학습/데모 가능.  
- **계약 우선**: Idempotency-Key, Trace-Id, 표준 오류 바디 같은 계약이 프로젝트 품질을 지탱.  
- **성능 튜닝**: 병목은 항상 존재. DB → Redis → SSE 최적화로 점진적으로 해결.  

👉 결론적으로, 이 프로젝트는 단순 기능 구현을 넘어서 **실제 대규모 동시성 서비스가 직면하는 문제와 그 해결 방법**을 몸으로 체득한 경험이었다.

---
