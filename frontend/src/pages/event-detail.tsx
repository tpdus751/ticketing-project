import { useState } from "react";
import { useParams } from "react-router-dom";
import { useEvent } from "@/features/events/hooks";
import Skeleton from "@/components/Skeleton";
import SeatGrid from "@/features/seats/components/SeatGrid";
import CartDrawer from "@/features/cart/CartDrawer";
import { useCart } from "@/stores/cart";
import { useSeatsStream } from "@/features/seats/hooks"; // ✅ 추가

export default function EventDetailPage() {
  const { id = "" } = useParams<{ id: string }>();
  const { data: event, isLoading, error } = useEvent(id);

  // ✅ 좌석 SSE 구독 추가
  useSeatsStream(id);

  const [openCart, setOpenCart] = useState(false);
  const cartCount = useCart((s) => s.items.length);

  if (isLoading) {
    return (
      <div className="p-6 space-y-4">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-4 w-40" />
        <Skeleton className="h-24 w-full" />
      </div>
    );
  }
  if (error || !event) {
    return <div className="p-6 text-red-400">이벤트를 불러오지 못했습니다.</div>;
  }

  const dt = new Date(event.dateTime).toLocaleString(undefined, {
    year: "numeric",
    month: "short",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });

  return (
    <div className="p-6 space-y-6">
      <header className="flex items-center justify-between">
        <div className="space-y-1">
          <h1 className="text-2xl font-semibold">{event.title}</h1>
          <p className="text-sm text-neutral-400">{dt}</p>
          {event.description && <p className="text-neutral-300">{event.description}</p>}
        </div>
      <button
          className="relative rounded-xl bg-white/10 px-3 py-1.5 text-sm hover:bg-white/15"
          onClick={() => setOpenCart((v) => !v)}
        >
          장바구니
          {cartCount > 0 && (
            <span className="ml-2 inline-flex h-5 min-w-[20px] items-center justify-center rounded-full bg-emerald-500/90 px-1.5 text-xs text-black font-semibold">
              {cartCount}
            </span>
          )}
        </button>
        
      </header>

      <section className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="font-medium">좌석</h2>
        </div>

        {/* 좌석 잡히면 장바구니만 연다 */}
        <SeatGrid
          eventId={id}
          onHeld={() => {
            setOpenCart(true);
          }}
        />

        <div className="flex gap-4 pt-2 text-sm">
          <Legend color="bg-emerald-500" label="AVAILABLE" />
          <Legend color="bg-amber-500" label="HELD" />
          <Legend color="bg-neutral-500" label="SOLD" />
        </div>
      </section>

      <CartDrawer open={openCart} />
    </div>
  );
}

function Legend({ color, label }: { color: string; label: string }) {
  return (
    <div className="flex items-center gap-2">
      <span className={`inline-block h-4 w-4 rounded ${color}`} />
      <span className="text-neutral-300">{label}</span>
    </div>
  );
}
