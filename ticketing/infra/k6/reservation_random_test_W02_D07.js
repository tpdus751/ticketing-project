import http from "k6/http";
import { check, sleep } from "k6";

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

export const options = {
  vus: 200,          // 가상 사용자 수
  duration: "10s",   // 실행 시간
};

export default function () {
  const url = "http://localhost:8081/ticketing/api/reservations";

  // seatIds 배열에서 무작위 선택
  const seatId = seatIds[Math.floor(Math.random() * seatIds.length)];

  const payload = JSON.stringify({
    eventId: 3, // 이벤트 ID 고정
    seatId: seatId,
    holdSeconds: 30,
  });

  const headers = { "Content-Type": "application/json" };

  const res = http.post(url, payload, { headers });

  check(res, {
    "201 Created (성공)": (r) => r.status === 201,
    "409 Conflict (좌석 이미 선점됨)": (r) => r.status === 409,
  });

  sleep(1);
}
