import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  createReservation,
  confirmReservation,
  type CreateReservationRequest,
  type CreateReservationResponse,
  type ConfirmReservationResponse,
} from "./api";

export function useCreateReservation() {
  const qc = useQueryClient();

  return useMutation<CreateReservationResponse, any, CreateReservationRequest>({
    mutationFn: createReservation,
    onSuccess: (_, variables) => {
      // 예약 성공 시 해당 이벤트 좌석맵 invalidate
      qc.invalidateQueries({ queryKey: ["event", variables.eventId, "seats"] });
    },
    onError: (_, variables) => {
      // 실패해도 seats 상태를 다시 불러와 stale 방지
      qc.invalidateQueries({ queryKey: ["event", variables.eventId, "seats"] });
    },
  });
}

export function useConfirmReservation() {
  const qc = useQueryClient();

  return useMutation<
    ConfirmReservationResponse,
    any,
    { eventId: number; seatId: number }
  >({
    mutationFn: ({ eventId, seatId }) => confirmReservation(eventId, seatId),
    onSuccess: (_, variables) => {
      qc.invalidateQueries({ queryKey: ["event", variables.eventId, "seats"] });
    },
    onError: (_, variables) => {
      qc.invalidateQueries({ queryKey: ["event", variables.eventId, "seats"] });
    },
  });
}
