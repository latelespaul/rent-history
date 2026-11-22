import { createBrowserRouter } from "react-router-dom"
import { RequireAuth } from "@/features/auth/RequireAuth"
import { ShellRoot } from "@/features/auth/ShellRoot"
import ComingSoonPage from "@/pages/ComingSoonPage"
import HomePage from "@/pages/HomePage"
import LandlordDashboardPage from "@/pages/LandlordDashboardPage"
import LegalPage from "@/pages/LegalPage"
import LoginPage from "@/pages/LoginPage"
import MyTenanciesPage from "@/pages/MyTenanciesPage"
import NotFoundPage from "@/pages/NotFoundPage"
import PropertyFormPage from "@/pages/PropertyFormPage"
import PropertyPage from "@/pages/PropertyPage"
import OnboardingPage from "@/pages/OnboardingPage"
import SearchPage from "@/pages/SearchPage"
import SettingsPage from "@/pages/SettingsPage"
import TenancyInvitePage from "@/pages/TenancyInvitePage"
import TenancyPage from "@/pages/TenancyPage"
import TenancyRequestPage from "@/pages/TenancyRequestPage"
import VerifyPage from "@/pages/VerifyPage"
import WelcomeCreatedPage from "@/pages/WelcomeCreatedPage"
import { RouteError } from "@/routes/RouteError"

export const routes = [
  // The landing draws its own minimal page, without the app shell.
  { path: "/", element: <HomePage />, errorElement: <RouteError /> },
  {
    element: <ShellRoot />,
    errorElement: <RouteError />,
    children: [
      { path: "/p/:id", element: <PropertyPage /> },
      { path: "/login", element: <LoginPage /> },
      { path: "/terms", element: <LegalPage title="Terms" /> },
      { path: "/privacy", element: <LegalPage title="Privacy" /> },
      {
        element: <RequireAuth allowOnboarding />,
        children: [{ path: "/welcome", element: <OnboardingPage /> }],
      },
      {
        element: <RequireAuth />,
        children: [
          { path: "/search", element: <SearchPage /> },
          { path: "/welcome/created", element: <WelcomeCreatedPage /> },
          { path: "/verify/*", element: <VerifyPage /> },
          { path: "/settings", element: <SettingsPage /> },
          { path: "/me/tenancies", element: <MyTenanciesPage /> },
          { path: "/tenancies/new", element: <TenancyRequestPage /> },
          { path: "/tenancies/:id", element: <TenancyPage /> },
          { path: "/tenancy-invite", element: <TenancyInvitePage /> },
          { path: "/me/*", element: <NotFoundPage /> },
        ],
      },
      {
        element: <RequireAuth role="LANDLORD" />,
        children: [
          { path: "/me/dashboard", element: <LandlordDashboardPage /> },
          { path: "/me/properties", element: <LandlordDashboardPage /> },
          { path: "/me/properties/new", element: <PropertyFormPage /> },
          { path: "/me/properties/:id/edit", element: <PropertyFormPage /> },
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
