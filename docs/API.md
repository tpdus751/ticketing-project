# 📄 API Contracts (v1.0 – 2025-09-13)

## 🔹 공통 규약
- **오류 응답 바디 (표준)**
```json
{
  "code": "RESERVATION_CONFLICT",
  "message": "Seat already held",
  "traceId": "f1a2b3c4-5678-90ab-cdef-1234567890ab"
}
```
- **헤더**
  - 요청: `Idempotency-Key` → `POST /ticketing/api/orders` 필수
  - 응답: `Trace-Id` → 모든 API 응답 헤더에 포함 (TraceIdFilter)

---

## 🔹 Catalog 모듈
### 이벤트
- `GET /ticketing/api/events`  
  이벤트 목록 조회  
  응답: `[ { id, title, dateTime, description } ]`

- `GET /ticketing/api/events/{id}`  
  단일 이벤트 조회  
  응답: `{ id, title, dateTime, description }`

### 좌석
- `GET /ticketing/api/events/{id}/seats`  
  좌석 맵 조회  
  응답:
```json
{
  "rows": 10,
  "cols": 10,
  "seats": [
    { "id": 1, "r": 1, "c": 1, "price": 50000, "status": "AVAILABLE" }
  ]
}
```

### 실시간 스트림
- `GET /ticketing/api/events/{eventId}/seats/stream` (SSE)  
  좌석 상태 스트리밍  
  이벤트:
```json
{ "seatId": 123, "status": "HELD", "version": 5 }
```

### 내부 좌석 상태 업데이트
- `POST /ticketing/api/internal/seat-update`  
  Body:
```json
{ "eventId": 1, "seatId": 123, "status": "SOLD", "version": 6 }
```
  설명: Reservation/Order 모듈이 호출 → Catalog SSE에 반영

---

## 🔹 Reservation 모듈
- `POST /ticketing/api/reservations`  
  Body: `{ eventId, seatId, holdSeconds }`  
  응답:
  - `201 Created`: `{ eventId, seatId, holdSeconds, expiresAt, traceId }`  
  - `409 Conflict`: `{ code, message, traceId }`

- `POST /ticketing/api/reservations/{eventId}/{seatId}/extend`  
  Body: `{ "seconds": 30 }`  
  응답: `{ "expiresAt": "2025-09-13T12:34:56Z" }`

- `DELETE /ticketing/api/reservations/{eventId}/{seatId}`  
  응답: `204 No Content`

---

## 🔹 Order 모듈
- `POST /ticketing/api/orders`  
  Header: `Idempotency-Key` 필수  
  Body: `{ eventId, seatIds[] }`  
  응답:
```json
{
  "orderId": 10,
  "status": "CREATED",
  "eventId": 1,
  "seatIds": [123,124],
  "createdAt": "2025-09-13T12:34:56"
}
```

- `GET /ticketing/api/orders/{id}`  
  응답:
```json
{
  "orderId": 10,
  "status": "CONFIRMED",
  "eventId": 1,
  "seatIds": [123,124],
  "createdAt": "2025-09-13T12:34:56"
}
```

---

## 🔹 Payment 모듈
- `POST /ticketing/api/payments/authorize`  
  Body: `{ orderId: 10 }`  
  응답 (랜덤 지연 + 성공/실패 80:20):
```json
{ "orderId": 10, "status": "success", "traceId": "..." }
```
  또는
```json
{ "orderId": 10, "status": "fail", "traceId": "..." }
```

---

## 🔹 Health Check
- `GET /actuator/health`  
  모든 모듈 공통  
  응답:
```json
{ "status": "UP" }
```

---

## ✅ Definition of Done (Day1)
- 모든 모듈의 엔드포인트/오류/헤더 규약을 문서화
- FE/BE/테스트(k6)에서 동일 문서를 기준으로 검증 가능
- `/actuator/health`로 모듈 상태 확인 가능
- oversell=0, Trace-Id end-to-end 추적 가능
