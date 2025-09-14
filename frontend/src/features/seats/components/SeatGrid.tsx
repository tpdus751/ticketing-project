import { useMemo } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { catalogApi, reservationApi  } from "@/api/client";
import type { Seat, SeatMap } from "@/features/seats/types";
import { useCreateReservation } from "@/features/reservations/hooks";
import type { CreateReservationRequest, CreateReservationResponse } from "@/features/reservations/api";
import { toast } from "sonner";
import { mapErrorToMessage } from "@/components/error-map";
import { useCart } from "@/stores/cart";

const CELL = 24;
const GAP = 4;

export default function SeatGrid({
  eventId,
  onHeld,
}: {
  eventId: string;
  onHeld?: () => void; // ← payload 제거(장바구니만 열기)
}) {
  const qc = useQueryClient();
  const reserve = useCreateReservation();

  const setEvent = useCart((s) => s.setEvent);
  const upsert = useCart((s) => s.upsert);

  const { data, isLoading, error } = useQuery<SeatMap>({
    queryKey: ["seats", eventId],
    queryFn: () => catalogApi.get<SeatMap>(`/api/events/${eventId}/seats`),
    staleTime: 3_000,
    refetchInterval: 10000, // ← 탭 간 HELD 동기화
  });

  const grid = useMemo(() => {
    const m: Record<string, Seat> = {};
    if (data) data.seats.forEach((s) => (m[`${s.r}-${s.c}`] = s));
    return m;
  }, [data]);

  const handleClick = (seat?: Seat) => {
    if (!seat) return;
    if (seat.status !== "AVAILABLE") {
      toast.warning("이미 선택할 수 없는 좌석입니다.");
      return;
    }

    reserve.mutate(
      { eventId: Number(eventId), seatId: seat.id, holdSeconds: 30 } as CreateReservationRequest,
      {
        onSuccess: (res: CreateReservationResponse) => {
          setEvent(Number(eventId));
          upsert({ seatId: seat.id, price: seat.price, expiresAt: res.expiresAt });
          toast.success(`좌석 #${seat.id} 선점 완료 (30s).`);
          onHeld?.(); // ← 장바구니 열기만
          qc.invalidateQueries({ queryKey: ["event", eventId, "seats"] });
        },
        onError: (err: any) => {
          toast.error(mapErrorToMessage(err));
          qc.invalidateQueries({ queryKey: ["event", eventId, "seats"] });
        },
      }
    );
  };

  if (isLoading) return <div className="text-sm text-neutral-400">좌석 불러오는 중…</div>;
  if (error || !data) return <div className="text-red-500">좌석을 불러오지 못했습니다.</div>;

  return (
    <div className="overflow-auto">
      <div
        className="grid"
        style={{
          gridTemplateColumns: `repeat(${data.cols}, ${CELL}px)`,
          gridAutoRows: `${CELL}px`,
          gap: GAP,
          justifyItems: "start",
          alignItems: "start",
        }}
        role="grid"
        aria-rowcount={data.rows}
        aria-colcount={data.cols}
      >
        {Array.from({ length: data.rows * data.cols }).map((_, idx) => {
          const r = Math.floor(idx / data.cols) + 1;
          const c = (idx % data.cols) + 1;
          const key = `${r}-${c}`;
          const seat = grid[key];
          const isSelectable = !!seat && seat.status === "AVAILABLE";

          return (
            <div key={key} style={{ width: CELL, height: CELL }}>
              <button
                className={[
                  "block h-full w-full rounded",
                  "transition-transform focus:outline-none focus:ring-2 focus:ring-violet-500 disabled:opacity-60",
                  seat?.status === "AVAILABLE"
                    ? "bg-emerald-500 hover:bg-emerald-600 hover:scale-105"
                    : seat?.status === "HELD"
                    ? "bg-amber-500/80"
                    : seat?.status === "SOLD"
                    ? "bg-neutral-600"
                    : "bg-neutral-700/70",
                  !isSelectable ? "cursor-not-allowed" : "cursor-pointer",
                ].join(" ")}
                role="gridcell"
                aria-label={`R${r} C${c}`}
                title={`R${r} C${c}${seat?.price ? ` · ₩${seat.price.toLocaleString()}` : ""}${seat ? ` · ${seat.status}` : ""}`}
                disabled={!isSelectable || reserve.isPending}
                onClick={() => handleClick(seat)}
              />
            </div>
          );
        })}
      </div>
    </div>
  );
}
