import { Link } from "react-router-dom"

export default function HomePage() {
  return (
    <div className="grid gap-3">
      <h1 className="text-2xl font-bold">초고동시성 티켓 예매 시스템</h1>
      <p className="text-sm text-neutral-400">W1~W2에 좌석맵/실시간 상태가 붙습니다.</p>
      <Link to="/events" className="inline-block rounded-lg bg-neutral-800 px-3 py-2 text-sm">
        이벤트 둘러보기
      </Link>
    </div>
  )
}
