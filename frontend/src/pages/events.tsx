// src/pages/events.tsx
import { Link } from "react-router-dom";
import { useEvents } from "@/features/events/hooks";
import Skeleton from "@/components/Skeleton";
import type { EventSummary } from "@/features/events/types";

export default function EventsPage() {
  const { data, isLoading, error } = useEvents();

  if (isLoading) {
    return (
      <div className="p-6 space-y-3">
        <Skeleton className="h-6 w-32" />
        {[...Array(6)].map((_, i) => (
          <Skeleton key={i} className="h-20 w-full rounded-xl" />
        ))}
      </div>
    );
  }

  if (error) {
    const msg = error instanceof Error ? error.message : "알 수 없는 오류";
    return <div className="p-6 text-red-400">이벤트 목록을 불러오지 못했습니다. ({msg})</div>;
  }

  const items = (data ?? []) as EventSummary[];

  return (
    <div className="p-6 space-y-4">
      <h1 className="text-2xl font-bold">이벤트</h1>
      <ul className="grid gap-3">
        {items.map((ev) => (
          <li key={ev.id}>
            <Link
              to={`/events/${ev.id}`}
              className="block rounded-xl border border-neutral-800 hover:border-neutral-600 hover:bg-neutral-900/40 p-4 transition"
            >
              <div className="text-lg font-semibold">{ev.title}</div>
              <div className="text-sm opacity-70">
                {new Date(ev.dateTime).toLocaleString()}
              </div>
              {ev.description && <p className="mt-1">{ev.description}</p>}
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
