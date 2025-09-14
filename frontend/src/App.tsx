import { Outlet, NavLink } from "react-router-dom"
import { Ticket } from "lucide-react"

export default function App() {
  return (
    <div className="min-h-screen bg-neutral-950 text-neutral-100">
      <header className="border-b border-neutral-800">
        <div className="mx-auto max-w-5xl px-4 py-4 flex items-center gap-3">
          <Ticket className="size-6" />
          <span className="font-semibold">Ultra Ticketing</span>
          <nav className="ml-auto flex gap-4 text-sm">
            <NavLink to="/" className={({isActive})=>isActive?"underline":""}>Home</NavLink>
            <NavLink to="/events" className={({isActive})=>isActive?"underline":""}>Events</NavLink>
            <NavLink to="/my-tickets" className={({isActive})=>isActive?"underline":""}>MyTicket</NavLink>
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  )
}
