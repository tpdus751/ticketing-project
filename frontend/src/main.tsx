// src/main.tsx
import React from "react"
import ReactDOM from "react-dom/client"
import "./index.css"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { RouterProvider, createBrowserRouter } from "react-router-dom"
import { Toaster } from "sonner";
import App from "./App"
import HomePage from "./pages/home"
import EventsPage from "./pages/events"
import EventDetailPage from "./pages/event-detail"
import CheckoutPage from "./pages/checkout";
import MyTicketsPage from "./pages/my-tickets";


const qc = new QueryClient()
const router = createBrowserRouter([
  { path: "/", element: <App />, children: [
    { index: true, element: <HomePage /> },
    { path: "events", element: <EventsPage /> },
    { path: "events/:id", element: <EventDetailPage /> },
    { path: "checkout", element: <CheckoutPage /> },
    { path: "my-tickets", element: <MyTicketsPage /> },
  ]},
])

async function bootstrap() {
  if (import.meta.env.DEV) {
    /*
    const { worker } = await import("./mocks/browser")
    await worker.start({ onUnhandledRequest: "bypass" })
    */
  }

  ReactDOM.createRoot(document.getElementById("root")!).render(
  import.meta.env.DEV ? (
    <QueryClientProvider client={qc}>
      <Toaster richColors position="top-right" />
      <RouterProvider router={router} />
    </QueryClientProvider>
  ) : (
    <React.StrictMode>
      <QueryClientProvider client={qc}>
        <Toaster richColors position="top-right" />
        <RouterProvider router={router} />
      </QueryClientProvider>
    </React.StrictMode>
  )
)
}

bootstrap()
