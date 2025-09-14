import { orderApi } from "@/api/client";

// 주문 응답 타입
export type OrderResponse = {
  orderId: number;
  status: string;      // CREATED / CONFIRMED / CANCELLED
  eventId: number;
  seatIds: number[];
  createdAt: string;
};

// 주문 생성
export async function createOrder(req: { eventId: number; seatIds: number[] }, idempotencyKey: string) {
  return orderApi.post<OrderResponse>("/api/orders", req, {
    headers: { "Idempotency-Key": idempotencyKey },
  });
}

// 주문 조회
export async function getOrder(orderId: number) {
  return orderApi.get<OrderResponse>(`/api/orders/${orderId}`);
}
