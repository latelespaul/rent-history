import { Outlet } from "react-router-dom"
import { Footer } from "./Footer"

/** Minimal shell until W2 adds the top bar: page content, then the footer. */
export function AppLayout() {
  return (
    <div className="flex min-h-screen flex-col">
      <div className="flex-1">
        <Outlet />
      </div>
      <Footer />
    </div>
  )
}
