import { http, HttpResponse } from "msw"

const events = [
  { id: "ev-1", title: "Ultra Concert 2025", date: "2025-09-10T19:00:00Z", description: "메가 콘서트" },
  { id: "ev-2", title: "Indie Fest",         date: "2025-09-15T18:00:00Z", description: "인디 뮤직 페스티벌" },
  { id: "ev-3", title: "Classic Night",      date: "2025-09-20T20:00:00Z", description: "오케스트라" },
]

export const handlers = [
  http.get("/api/events", () => HttpResponse.json(events)),
  http.get("/api/events/:id", ({ params }) => {
    const ev = events.find(e => e.id === params.id)
    return ev ? HttpResponse.json(ev) : new HttpResponse("Not Found", { status: 404 })
  }),
  http.get("/ping", () => HttpResponse.text("pong")),
]
