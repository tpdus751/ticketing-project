// src/features/events/hooks.ts
import { useQuery } from "@tanstack/react-query";
import { catalogApi } from "@/api/client";
import type { EventSummary } from "./types";

type EventDTO = {
  id: number;
  title: string;
  description?: string | null;
  date?: string;
  dateTime?: string;
};

export function useEvents() {
  return useQuery({
    queryKey: ["events"],
    queryFn: async (): Promise<EventSummary[]> => {
      const raw = await catalogApi.get<EventDTO[]>("/api/events");
      // BE가 date / dateTime 둘 중 하나를 줄 수 있어 둘 다 수용 → dateTime으로 통일
      return raw.map((r): EventSummary => ({
        id: r.id,
        title: r.title,
        description: r.description ?? null,
        dateTime: r.dateTime ?? r.date ?? "",
      }));
    },
    staleTime: 10_000,
  });
}

export function useEvent(id?: string) {
  return useQuery({
    queryKey: ["event", id],
    enabled: !!id,
    queryFn: async (): Promise<EventSummary> => {
      const r = await catalogApi.get<EventDTO>(`/api/events/${id}`);
      return {
        id: r.id,
        title: r.title,
        description: r.description ?? null,
        dateTime: r.dateTime ?? r.date ?? "",
      };
    },
    staleTime: 10_000,
  });
}
