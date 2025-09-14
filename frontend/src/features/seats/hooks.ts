// src/features/seats/hooks.ts
import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { SSEClient } from "@/libs/sse";
import type { Seat, SeatMap } from "./types"; // ✅ SeatMap도 같이 import

export function useSeatsStream(eventId: string) {
  const qc = useQueryClient();

  useEffect(() => {
    if (!eventId) return;

    const url = `${import.meta.env.VITE_CATALOG_API}/api/events/${eventId}/seats/stream`;
    const client = new SSEClient(url);

    client.connect((msg) => {
      if (msg.event === "SEAT_UPDATE") {
        const update = msg.data as { seatId: number; status: Seat["status"]; version: number };

        // ✅ SeatMap 타입을 제네릭으로 지정
        qc.setQueryData<SeatMap>(
          ["seats", eventId],
          (old) => {
            if (!old) return old; // 캐시에 데이터가 없으면 그대로 반환
            return {
              ...old,
              seats: old.seats.map((s) =>
                s.id === update.seatId ? { ...s, status: update.status } : s
              ),
            };
          }
        );
      }
    });

    return () => client.disconnect();
  }, [eventId, qc]);
}
