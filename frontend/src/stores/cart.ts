// src/stores/cart.ts

import { create } from "zustand";

export type HeldSeat = { seatId: number; price: number; expiresAt: string };

type CartState = {
  eventId: number | null;
  items: HeldSeat[];
  setEvent: (eventId: number) => void;
  upsert: (seat: HeldSeat) => void;
  remove: (seatId: number) => void;
  clear: () => void;
  totalPrice: () => number;
  removeExpired: () => void; // ✅ 새로운 액션 타입 추가
};

export const useCart = create<CartState>((set, get) => ({
  eventId: null,
  items: [],
  setEvent: (eventId) => set((state) => (state.eventId === eventId ? state : { eventId, items: [] })),
  upsert: (seat) => {
    const items = get().items.slice();
    const i = items.findIndex((s) => s.seatId === seat.seatId);
    if (i >= 0) items[i] = seat;
    else items.push(seat);
    set({ items });
  },
  remove: (seatId) => set({ items: get().items.filter((s) => s.seatId !== seatId) }),
  clear: () => set({ items: [] }),
  totalPrice: () => get().items.reduce((sum, s) => sum + s.price, 0),

  // ✅ 새로운 액션 구현
  // 컴포넌트로부터 'items'를 받을 필요 없이 get()으로 최신 상태를 가져옵니다.
  removeExpired: () => {
    const { items } = get();
    const now = Date.now();
    const expiredIds = items
      .filter((s) => new Date(s.expiresAt).getTime() - now <= 0)
      .map((s) => s.seatId);

    if (expiredIds.length > 0) {
      set({ items: items.filter((item) => !expiredIds.includes(item.seatId)) });
    }
  },
}));