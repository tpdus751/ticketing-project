import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  vus: 3,
  duration: "10s",
  thresholds: {
    http_req_duration: ["p(95)<300"],
  },
};

const BASE = "http://localhost:8080/api";
const EVENT_ID = 1;

// ✅ event_id=1의 좌석 id (DB 쿼리 결과 그대로 붙여넣기)
const SEAT_IDS = [
  28,58,88,118,148,178,208,238,268,298,
  25,55,85,115,145,175,205,235,265,295,
  22,52,82,112,142,172,202,232,262,292,
  19,49,79,109,139,169,199,229,259,289,
  16,46,76,106,136,166,196,226,256,286,
  13,43,73,103,133,163,193,223,253,283,
  10,40,70,100,130,160,190,220,250,280,
  7,37,67,97,127,157,187,217,247,277,
  4,34,64,94,124,154,184,214,244,274,
  1,31,61,91,121,151,181,211,241,271,
];

function pickSeatId() {
  return SEAT_IDS[Math.floor(Math.random() * SEAT_IDS.length)];
}

export default function () {
  const seatId = pickSeatId();

  // 1) hold
  const holdRes = http.post(`${BASE}/reservations`, JSON.stringify({
    eventId: EVENT_ID,
    seatId,
    holdSeconds: 5
  }), { headers: { "Content-Type": "application/json" } });

  check(holdRes, {
    "hold 201": r => r.status === 201,
    "hold 409 (conflict)": r => r.status === 409,
    "hold 422 (sold)": r => r.status === 422,
  });

  // 2) confirm (hold 성공시에만)
  if (holdRes.status === 201) {
    const confirmRes = http.post(`${BASE}/reservations/${EVENT_ID}/${seatId}/confirm`);
    check(confirmRes, {
      "confirm 200": r => r.status === 200,
    });
  }

  sleep(1);
}
