// src/pages/checkout.tsx
import { useCart } from "@/stores/cart"
import { useEffect, useState } from "react"
import { createOrder, getOrder, type OrderResponse } from "@/api/orderApi"
import { toast } from "sonner"
import { useNavigate } from "react-router-dom"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { CheckCircle, Loader2, XCircle } from "lucide-react"

export default function CheckoutPage() {
  const cart = useCart()
  const navigate = useNavigate()
  const [order, setOrder] = useState<OrderResponse | null>(null)
  const [status, setStatus] = useState<string>("INIT")

  // 주문 생성 시점에만 새 키 생성
  const [idempotencyKey] = useState(() => crypto.randomUUID())
  const toastId = "order-progress"

  // 주문 생성
  useEffect(() => {
    const placeOrder = async () => {
      try {
        toast.loading("주문 생성 중입니다...", { id: toastId })

        const res = await createOrder(
          { eventId: cart.eventId!, seatIds: cart.items.map((i) => i.seatId) },
          idempotencyKey
        )

        setOrder(res)
        setStatus(res.status)

        if (res.status === "CONFIRMED") {
          toast.success("결제가 완료되었습니다!", { id: toastId })
        } else if (res.status === "CANCELLED") {
          toast.error("결제에 실패했습니다.", { id: toastId })
        } else {
          toast.loading("주문 생성 완료! 결제 진행 중…", { id: toastId })
        }

        cart.clear()
      } catch (e: any) {
        toast.error("주문 생성 실패: " + (e.message ?? ""), { id: toastId })
        setStatus("FAILED")
        navigate("/events")
      }
    }

    if (cart.items.length > 0) {
      placeOrder()
    } else {
      toast.error("장바구니가 비어 있습니다.", { id: toastId })
      navigate("/events")
    }
  }, [])

  // 주문 상태 Polling (1초마다)
// 주문 상태 Polling (1초마다)
useEffect(() => {
  if (!order?.orderId) return;

  const interval = setInterval(async () => {
    try {
      const latest = await getOrder(order.orderId);
      console.log("latest status =", latest.status);

      setOrder(latest);
      setStatus(latest.status);

      // 최종 상태면 polling 종료
      if (["CONFIRMED", "CANCELLED", "FAILED"].includes(latest.status)) {
        clearInterval(interval);
        if (latest.status === "CONFIRMED") {
          toast.success("결제가 완료되었습니다!", { id: toastId });
        }
        if (latest.status === "CANCELLED") {
          toast.error("결제에 실패했습니다. 좌석이 해제되었습니다.", { id: toastId });
        }
      }
    } catch (e) {
      console.error("주문 상태 조회 실패", e);
    }
  }, 1000);

  return () => clearInterval(interval);
}, [order?.orderId]); // ✅ order 전체가 아니라 orderId만 dependency

  return (
    <div className="p-8 flex flex-col items-center">
      <h1 className="text-3xl font-bold mb-6">체크아웃</h1>

      {!order ? (
        <Card className="w-full max-w-lg bg-neutral-800 border border-white/10">
          <CardContent className="p-6 flex justify-center items-center">
            <Loader2 className="animate-spin text-sky-400 w-6 h-6 mr-2" />
            <span>주문을 생성 중입니다…</span>
          </CardContent>
        </Card>
      ) : (
        <Card className="w-full max-w-lg bg-neutral-800 border border-white/10">
          <CardHeader>
            <CardTitle>주문번호 #{order.orderId}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <p>좌석: {order.seatIds?.join(", ") ?? "-"}</p>

            {/* 상태 Progress */}
            <div className="flex items-center gap-3">
              {status === "CONFIRMED" && (
                <>
                  <CheckCircle className="text-emerald-400 w-6 h-6" />
                  <span className="text-emerald-400 font-semibold">결제 완료</span>
                </>
              )}
              {status === "CANCELLED" && (
                <>
                  <XCircle className="text-red-400 w-6 h-6" />
                  <span className="text-red-400 font-semibold">결제 실패</span>
                </>
              )}
              {status === "CREATED" && (
                <>
                  <Loader2 className="animate-spin text-sky-400 w-6 h-6" />
                  <span className="text-sky-400 font-semibold">결제 진행 중…</span>
                </>
              )}
            </div>

            {/* 버튼 */}
            {status === "CONFIRMED" && (
              <button
                onClick={() => navigate("/my-tickets")}
                className="mt-4 w-full bg-emerald-500 hover:bg-emerald-400 py-2 rounded-xl font-semibold text-black"
              >
                내 예매 내역 보기
              </button>
            )}
            {status === "CANCELLED" && (
              <button
                onClick={() => navigate("/events/" + order.eventId)}
                className="mt-4 w-full bg-red-500 hover:bg-red-400 py-2 rounded-xl font-semibold text-white"
              >
                다시 시도하기
              </button>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  )
}
