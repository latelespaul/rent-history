import { createBrowserRouter } from "react-router-dom"
import HomePage from "@/pages/HomePage"
import NotFoundPage from "@/pages/NotFoundPage"
import { RouteError } from "@/routes/RouteError"

export const router = createBrowserRouter([
  { path: "/", element: <HomePage />, errorElement: <RouteError /> },
  { path: "*", element: <NotFoundPage /> },
])
