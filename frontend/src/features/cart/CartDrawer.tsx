import { useCart } from "@/stores/cart";
import { useEffect, useRef, useState } from "react";
import { reservationApi } from "@/api/client"; // ✅ orderApi는 제거
import { useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom"; // ✅ CheckoutPage 이동용

export type CartDrawerProps = { open: boolean };
type ExtendRes = { expiresAt: string };

export default function CartDrawer({ open }: CartDrawerProps) {
  const navigate = useNavigate();
  const { items, remove, totalPrice, eventId, upsert, removeExpired } = useCart(); // ✅ clear 제거 (CheckoutPage에서만 비우도록)
  const [busy, setBusy] = useState<Record<number, boolean>>({});
  const [now, setNow] = useState(() => Date.now());
  const prevLenRef = useRef(items.length);
  const qc = useQueryClient();

  // ⏱ 표시용 타이머 + 만료 정리
  useEffect(() => {
    const t = setInterval(() => {
      setNow(Date.now());
      removeExpired();
      const curLen = useCart.getState().items.length;
      if (curLen !== prevLenRef.current && eventId) {
        prevLenRef.current = curLen;
        qc.invalidateQueries({ queryKey: ["seats", String(eventId)] });
      }
    }, 1000);
    return () => clearInterval(t);
  }, [eventId, qc, removeExpired]);

  const secLeft = (expiresAt: string) => {
    const remainMs = Math.max(0, new Date(expiresAt).getTime() - now);
    return Math.ceil(remainMs / 1000);
  };

  const extend = async (seatId: number) => {
    if (!eventId) return;
    try {
      setBusy((b) => ({ ...b, [seatId]: true }));
      const res = await reservationApi.post<ExtendRes>(
        `/api/reservations/${eventId}/${seatId}/extend`,
        { seconds: 30 }
      );
      const current = useCart.getState().items.find((x) => x.seatId === seatId);
      if (current) upsert({ seatId, price: current.price, expiresAt: res.expiresAt });
      qc.invalidateQueries({ queryKey: ["seats", String(eventId)] });
    } finally {
      setBusy((b) => ({ ...b, [seatId]: false }));
    }
  };

  const release = async (seatId: number) => {
    if (!eventId) return;
    try {
      setBusy((b) => ({ ...b, [seatId]: true }));
      await reservationApi.delete<void>(`/api/reservations/${eventId}/${seatId}`);
      remove(seatId);
      qc.invalidateQueries({ queryKey: ["seats", String(eventId)] });
    } finally {
      setBusy((b) => ({ ...b, [seatId]: false }));
    }
  };

  return (
    <div
      className={`fixed right-0 top-0 h-full w-[360px] bg-neutral-900 border-l border-white/10 transition-transform ${
        open ? "translate-x-0" : "translate-x-full"
      }`}
    >
      <div className="p-4 text-lg font-semibold">내 선점 좌석</div>

      <div className="px-4 space-y-3">
        {items.length === 0 && (
          <div className="text-sm text-slate-400">선점한 좌석이 없습니다.</div>
        )}

        {items.map((s) => {
          const sec = secLeft(s.expiresAt);
          const disabled = busy[s.seatId] || sec === 0;

          return (
            <div
              key={s.seatId}
              className="flex items-center justify-between rounded-xl border border-white/10 p-3"
            >
              <div>
                <div className="text-sm">좌석 #{s.seatId}</div>
                <div
                  className={`text-xs ${
                    sec <= 10 ? "text-amber-400" : "text-slate-400"
                  }`}
                >
                  남은시간 {sec}s
                </div>
                <div className="text-sm mt-1">₩{s.price.toLocaleString()}</div>
              </div>

              <div className="flex gap-2">
                <button
                  className="px-2 py-1 text-xs rounded bg-white/10 disabled:opacity-50"
                  disabled={disabled}
                  onClick={() => extend(s.seatId)}
                >
                  {busy[s.seatId] ? "연장..." : "연장"}
                </button>
                <button
                  className="px-2 py-1 text-xs rounded bg-white/10 disabled:opacity-50"
                  disabled={busy[s.seatId]}
                  onClick={() => release(s.seatId)}
                >
                  {busy[s.seatId] ? "제거..." : "제거"}
                </button>
              </div>
            </div>
          );
        })}
      </div>

      <div className="absolute bottom-0 left-0 right-0 p-4 border-t border-white/10">
        <div className="flex items-center justify-between">
          <div className="text-slate-300 text-sm">합계</div>
          <div className="font-semibold">₩{totalPrice().toLocaleString()}</div>
        </div>
        <button
          className="mt-3 w-full rounded-xl bg-emerald-500/90 hover:bg-emerald-400 py-2 text-black font-semibold disabled:opacity-60"
          disabled={items.length === 0}
          onClick={() => navigate("/checkout")} // ✅ 주문 API 호출 대신 CheckoutPage 이동
        >
          결제하기
        </button>
      </div>
    </div>
  );
}
