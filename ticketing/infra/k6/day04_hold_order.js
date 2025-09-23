import http from "k6/http";
import { check, sleep } from "k6";
import { randomItem } from "https://jslib.k6.io/k6-utils/1.4.0/index.js";
import { Counter } from "k6/metrics";

// Custom metrics
export const conflicts = new Counter("seat_conflicts");       // 409, 410 충돌/만료
export const cancels = new Counter("reservation_cancels");    // 예약 취소
export const orders = new Counter("orders_created");          // 주문 성공

export const options = {
  // VU/Duration은 CLI로 제어 (--vus --duration)
  thresholds: {
    // SLA 기준: 5xx 에러만 실패로 인정
    "http_req_failed{status:500}": ["rate<0.005"],
    "http_req_failed{status:502}": ["rate<0.005"],
    "http_req_failed{status:503}": ["rate<0.005"],
    "http_req_failed{status:504}": ["rate<0.005"],
    http_req_duration: ["p(95)<300"], // p95 < 300ms
  },
};

const BASE_URL = "http://15.164.212.171/ticketing/reservation";
const ORDER_URL = "http://15.164.212.171/ticketing/order";
const eventId = 3;

// 100석 샘플
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
  const traceId = crypto.randomUUID();

  // 1) 좌석 HOLD
  const holdRes = http.post(
    `${BASE_URL}/api/reservations`,
    JSON.stringify({
      eventId,
      seatId,
      holdSeconds: 30,
    }),
    {
      headers: {
        "Content-Type": "application/json",
        "Trace-Id": traceId,
      },
    }
  );

  // 좌석 충돌/만료는 정상 시나리오 → Custom metric에 기록
  if (holdRes.status === 409 || holdRes.status === 410) {
    conflicts.add(1);
    return;
  }

  if (
    !check(holdRes, {
      "hold success": (r) => r.status === 201,
    })
  ) {
    return; // 5xx 등 진짜 실패만 여기로 걸림
  }

  // 2) Hold 성공 → 30초 안에 주문 OR 취소
  const actionDelay = Math.floor(Math.random() * 30); // 0~29초 랜덤
  sleep(actionDelay);

  if (Math.random() < 0.7) {
    // 70% 확률: 주문 생성
    const idemKey = crypto.randomUUID();
    const orderRes = http.post(
      `${ORDER_URL}/api/orders`,
      JSON.stringify({
        eventId,
        seatIds: [seatId],
      }),
      {
        headers: {
          "Content-Type": "application/json",
          "Idempotency-Key": idemKey,
          "Trace-Id": traceId,
        },
      }
    );

    if (orderRes.status === 200 || orderRes.status === 201) {
      orders.add(1);
    }

    check(orderRes, {
      "order created": (r) => [200, 201].includes(r.status),
    });
  } else {
    // 30% 확률: 예약 취소
    const cancelRes = http.del(
      `${BASE_URL}/api/reservations/${eventId}/${seatId}`,
      null,
      {
        headers: { "Trace-Id": traceId },
      }
    );

    if (cancelRes.status === 204) {
      cancels.add(1);
    }

    check(cancelRes, {
      "reservation cancelled": (r) => r.status === 204,
    });
  }
}
