import { reservationApi  } from "@/api/client";

/** 예약 생성 요청 DTO */
export type CreateReservationRequest = {
  eventId: number;
  seatId: number;
  holdSeconds: number;
};

/** 예약 생성 응답 DTO */
export type CreateReservationResponse = {
  eventId: number;
  seatId: number;
  holdSeconds: number;
  expiresAt: string; // ISO string
  traceId: string;
};

/** 예약 생성 */
export function createReservation(req: CreateReservationRequest) {
  return reservationApi.post<CreateReservationResponse>("/api/reservations", req);
}

/** 예약 확정 응답 DTO */
export type ConfirmReservationResponse = {
  eventId: number;
  seatId: number;
  confirmedAt: string; // ISO string
  traceId: string;
};

/** 예약 확정 (바디 없음) */
export function confirmReservation(eventId: number, seatId: number) {
  // client.post가 body 파라미터를 요구하므로 undefined를 전달
  return reservationApi.post<ConfirmReservationResponse>(
    `/api/reservations/${eventId}/${seatId}/confirm`,
    undefined as any
  );
}
