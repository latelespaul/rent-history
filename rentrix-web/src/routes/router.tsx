import { createBrowserRouter } from "react-router-dom"
import HomePage from "@/pages/HomePage"
import NotFoundPage from "@/pages/NotFoundPage"
import PropertyPage from "@/pages/PropertyPage"
import SearchPage from "@/pages/SearchPage"
import { RouteError } from "@/routes/RouteError"
import { SessionShell } from "@/routes/SessionShell"

export const router = createBrowserRouter([
  {
    element: <SessionShell />,
    errorElement: <RouteError />,
    children: [
      { path: "/", element: <HomePage /> },
      { path: "/search", element: <SearchPage /> },
      { path: "/p/:id", element: <PropertyPage /> },
      { path: "*", element: <NotFoundPage /> },
    ],
  },
])
