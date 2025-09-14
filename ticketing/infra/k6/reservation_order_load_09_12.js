import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomItem } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
  stages: [
    { duration: "10s", target: 50 },   // 10초 동안 100명까지 ramp-up
    { duration: "20s", target: 100 },   // 20초 동안 500명까지
    { duration: "30s", target: 200 },  // 30초 동안 1000명 유지
    { duration: "10s", target: 400 },     // 종료
  ],
};


const BASE_URL = 'http://localhost:8081/ticketing'; // Reservation 모듈
const ORDER_URL = 'http://localhost:8082/ticketing'; // Order 모듈 (포트 환경에 맞게 수정)

const eventId = 3;

// 이벤트 3번의 좌석 ID 목록
const seatIds = [
  30, 60, 90, 120, 150, 180, 210, 240, 270, 300,
  27, 57, 87, 117, 147, 177, 207, 237, 267, 297,
  24, 54, 84, 114, 144, 174, 204, 234, 264, 294,
  21, 51, 81, 111, 141, 171, 201, 231, 261, 291,
  18, 48, 78, 108, 138, 168, 198, 228, 258, 288,
  15, 45, 75, 105, 135, 165, 195, 225, 255, 285,
  12, 42, 72, 102, 132, 162, 192, 222, 252, 282,
  9, 39, 69, 99, 129, 159, 189, 219, 249, 279,
  6, 36, 66, 96, 126, 156, 186, 216, 246, 276,
  3, 33, 63, 93, 123, 153, 183, 213, 243, 273,
];

export default function () {
  const seatId = randomItem(seatIds);
  const traceId = crypto.randomUUID(); // 각 요청 trace

  // 1) 좌석 HOLD
  const holdRes = http.post(`${BASE_URL}/api/reservations`, JSON.stringify({
    eventId: eventId,
    seatId: seatId,
    holdSeconds: 30,
  }), {
    headers: { 'Content-Type': 'application/json', 'Trace-Id': traceId },
  });

  if (!check(holdRes, {
    'hold success or conflict': (r) => [201, 409, 410].includes(r.status),
  })) {
    return;
  }

  if (holdRes.status !== 201) {
    // 이미 선점됨 or 만료
    return;
  }

  // 2) 주문 생성
  const idemKey = crypto.randomUUID();
  const orderRes = http.post(`${ORDER_URL}/api/orders`, JSON.stringify({
    eventId: eventId,
    seatIds: [seatId],
  }), {
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': idemKey,
      'Trace-Id': traceId,
    },
  });

  check(orderRes, {
    'order created': (r) => r.status === 201 || r.status === 200,
  });

  sleep(0.1); // 짧게 쉬고 다음 사용자
}
