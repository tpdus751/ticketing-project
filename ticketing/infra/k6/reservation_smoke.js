import http from "k6/http";
import { check, sleep } from "k6";
import { Trend, Rate, Counter } from "k6/metrics";

/* =========================
   환경 변수 (기본값 포함)
   ========================= */
const API_BASE     = __ENV.API_BASE   || "http://localhost:8080";
const EVENT_ID     = parseInt(__ENV.EVENT_ID || "1", 10);
const DO_RESET     = (__ENV.RESET   || "true") === "true";   // 시작 전에 초기화 API 호출
const DO_CONFIRM   = (__ENV.CONFIRM || "true") === "true";   // 예약 후 확정까지 수행
const HOLD_SECONDS = parseInt(__ENV.HOLD_SECONDS || "10", 10); // 좌석 홀드 TTL(기본 10초)
const MAX_RETRY    = parseInt(__ENV.MAX_RETRY || "10", 10);   // 좌석 픽 재시도 횟수
const VU_MAX       = parseInt(__ENV.VU_MAX || "100", 10);     // 최대 VU
const DURATION     = __ENV.DURATION || "30s";                 // 실행 시간

/* =========================
   시나리오 설정
   ========================= */
export const options = {
  scenarios: {
    smoke: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "5s",  target: Math.min(10, VU_MAX) },
        { duration: "10s", target: Math.min(50, VU_MAX) },
        { duration: DURATION, target: VU_MAX },
        { duration: "5s",  target: 0 },
      ],
      gracefulRampDown: "5s",
    },
  },
  thresholds: {
    // 전역 실패율은 약간 완화
    http_req_failed: ["rate<0.2"],
    "reservation_success_rate": ["rate>0.5"],  // 경쟁 많으면 90%는 비현실적 → 스모크 기준 낮춤
    "confirm_success_rate": ["rate>0.5"],
  },
};

/* =========================
   커스텀 메트릭
   ========================= */
const pickSeatLatency   = new Trend("latency_pick_seat", true);
const holdLatency       = new Trend("latency_hold", true);
const confirmLatency    = new Trend("latency_confirm", true);

const reservationSuccess = new Rate("reservation_success_rate");
const confirmSuccess     = new Rate("confirm_success_rate");
const noSeatCounter      = new Counter("no_available_seat");
const conflictCounter    = new Counter("conflict_or_gone");
const errorCounter       = new Counter("unexpected_error");

/* =========================
   헬퍼 함수
   ========================= */
function safeJson(res) {
  try { return res.json(); } catch (_) { return null; }
}
function jget(path) {
  const res = http.get(`${API_BASE}${path}`);
  return { res, json: safeJson(res) };
}
function jpost(path, body) {
  const res = http.post(`${API_BASE}${path}`, JSON.stringify(body), {
    headers: { "Content-Type": "application/json" },
  });
  return { res, json: safeJson(res) };
}
function tinyBackoff() {
  sleep(0.1 + Math.random() * 0.3);
}

/* =========================
   초기화 (선택)
   ========================= */
export function setup() {
  if (!DO_RESET) {
    console.log("[setup] RESET=false → 초기화 생략");
    return;
  }
  const resetUrl = `${API_BASE}/admin/test-reset?eventId=${EVENT_ID}`;
  const res = http.post(resetUrl, null);
  if (!check(res, { "reset ok (2xx)": (r) => r.status >= 200 && r.status < 300 })) {
    console.log(`[setup] reset failed: ${res.status} ${res.body}`);
  } else {
    console.log(`[setup] reset done for eventId=${EVENT_ID}`);
  }
}

/* =========================
   좌석 선택 (분산 + 재시도)
   ========================= */
function pickAvailableSeatWithRetry(eventId, maxTry = MAX_RETRY) {
  let chosen = null;
  const t0 = Date.now();

  for (let i = 0; i < maxTry; i++) {
    const { res, json } = jget(`/api/events/${eventId}/seats`);
    if (res.status !== 200) {
      tinyBackoff();
      continue;
    }
    const seats = json?.seats || [];
    const len = seats.length;
    if (len === 0) break;

    // VU와 ITER 기반 오프셋으로 분산 선택
    const offset = ((__VU * 7) + (__ITER * 13)) % len;
    for (let k = 0; k < len; k++) {
      const idx = (offset + k) % len;
      const s = seats[idx];
      if (s.status === "AVAILABLE") {
        chosen = s;
        break;
      }
    }
    if (chosen) break;
    tinyBackoff();
  }

  pickSeatLatency.add(Date.now() - t0);
  if (!chosen) noSeatCounter.add(1);
  return chosen;
}

/* =========================
   예약 (홀드)
   ========================= */
function holdSeat(eventId, seat) {
  const t0 = Date.now();
  const { res } = jpost(`/api/reservations`, {
    eventId: Number(eventId),
    seatId: seat.id,
    holdSeconds: HOLD_SECONDS,
  });

  const ok = res.status === 200 || res.status === 201 || res.status === 204;
  if (!ok && (res.status === 409 || res.status === 410 || res.status === 422)) {
    conflictCounter.add(1); // 경쟁으로 실패 → 정상적 현상
  } else if (!ok) {
    errorCounter.add(1);
  }

  holdLatency.add(Date.now() - t0);
  reservationSuccess.add(ok);
  return ok;
}

/* =========================
   확정 (옵션)
   ========================= */
function confirmSeat(eventId, seatId) {
  const t0 = Date.now();
  const { res } = jpost(`/api/reservations/${eventId}/${seatId}/confirm`, {});
  const ok = res.status === 200 || res.status === 201 || res.status === 204;

  if (!ok && (res.status === 409 || res.status === 410 || res.status === 422)) {
    conflictCounter.add(1);
  } else if (!ok) {
    errorCounter.add(1);
  }

  confirmLatency.add(Date.now() - t0);
  confirmSuccess.add(ok);
  return ok;
}

/* =========================
   메인 VU 함수
   ========================= */
export default function reservationFlow() {
  const seat = pickAvailableSeatWithRetry(EVENT_ID);
  if (!seat) {
    // AVAILABLE 없을 땐 경고 대신 카운터만 올리고 종료
    sleep(0.2);
    return;
  }

  const held = holdSeat(EVENT_ID, seat);
  if (!held) {
    tinyBackoff();
    return;
  }

  if (DO_CONFIRM) {
    sleep(0.2 + Math.random() * 0.5); // 사용자 행동 지연
    confirmSeat(EVENT_ID, seat.id);
  } else {
    // HOLD만 검증 모드일 때 TTL 만료 기다림
    sleep(0.5 + Math.random() * 0.5);
  }

  tinyBackoff();
}
