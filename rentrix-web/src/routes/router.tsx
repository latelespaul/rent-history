import { createBrowserRouter } from "react-router-dom"
import { RequireAuth } from "@/features/auth/RequireAuth"
import { ShellRoot } from "@/features/auth/ShellRoot"
import ComingSoonPage from "@/pages/ComingSoonPage"
import HomePage from "@/pages/HomePage"
import LoginPage from "@/pages/LoginPage"
import NotFoundPage from "@/pages/NotFoundPage"
import OnboardingPage from "@/pages/OnboardingPage"
import SearchPage from "@/pages/SearchPage"
import SettingsPage from "@/pages/SettingsPage"
import { RouteError } from "@/routes/RouteError"

export const routes = [
  {
    element: <ShellRoot />,
    errorElement: <RouteError />,
    children: [
      { path: "/", element: <HomePage /> },
      { path: "/search", element: <SearchPage /> },
      { path: "/login", element: <LoginPage /> },
      {
        element: <RequireAuth allowOnboarding />,
        children: [{ path: "/welcome", element: <OnboardingPage /> }],
      },
      {
        element: <RequireAuth />,
        children: [
          { path: "/settings", element: <SettingsPage /> },
          { path: "/me/dashboard", element: <ComingSoonPage title="Landlord dashboard" /> },
          { path: "/me/tenancies", element: <ComingSoonPage title="My tenancies" /> },
          { path: "/me/*", element: <NotFoundPage /> },
        ],
      },
      {
        element: <RequireAuth role="ADMIN" />,
        children: [{ path: "/admin/*", element: <ComingSoonPage title="Admin console" /> }],
      },
      { path: "*", element: <NotFoundPage /> },
    ],
  },
]

export const router = createBrowserRouter(routes)
