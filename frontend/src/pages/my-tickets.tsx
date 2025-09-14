import { useEffect, useState } from "react";
import { toast } from "sonner";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";

// 예매 내역 타입
type Ticket = {
  orderId: number;
  eventTitle: string;
  seatIds: number[];
  status: string; // CONFIRMED | CANCELLED | CREATED 등
  createdAt: string;
};

// TODO: 백엔드 API 연동
async function fetchMyTickets(): Promise<Ticket[]> {
  // 샘플 더미 데이터 (나중에 /api/orders/me 같은 엔드포인트 붙이면 됨)
  return Promise.resolve([
    {
      orderId: 50,
      eventTitle: "Indie Fest",
      seatIds: [170],
      status: "CONFIRMED",
      createdAt: "2025-09-10T01:20:00",
    },
    {
      orderId: 49,
      eventTitle: "K-Pop Concert",
      seatIds: [88, 89],
      status: "CANCELLED",
      createdAt: "2025-09-09T20:00:00",
    },
  ]);
}

export default function MyTicketsPage() {
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchMyTickets()
      .then((data) => {
        setTickets(data);
      })
      .catch(() => {
        toast.error("예매 내역을 불러오지 못했습니다.");
      })
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <p className="p-6">불러오는 중...</p>;
  if (tickets.length === 0) return <p className="p-6">예매 내역이 없습니다.</p>;

  return (
    <div className="p-6 space-y-6">
      <h1 className="text-2xl font-semibold">내 예매 내역</h1>

      <div className="grid gap-4 md:grid-cols-2">
        {tickets.map((t) => (
          <Card key={t.orderId} className="bg-surface text-text">
            <CardHeader>
              <CardTitle>주문번호 #{t.orderId}</CardTitle>
              <p className="text-sm text-muted">{t.eventTitle}</p>
            </CardHeader>
            <CardContent className="space-y-2">
              <p>좌석: {t.seatIds.join(", ")}</p>
              <p>
                상태:{" "}
                <span
                  className={
                    t.status === "CONFIRMED"
                      ? "text-emerald-400 font-bold"
                      : t.status === "CANCELLED"
                      ? "text-red-400 font-bold"
                      : "text-yellow-400 font-bold"
                  }
                >
                  {t.status}
                </span>
              </p>
              <p className="text-sm text-muted">
                예매일: {new Date(t.createdAt).toLocaleString()}
              </p>
              <Button className="w-full mt-2">상세보기</Button>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}
