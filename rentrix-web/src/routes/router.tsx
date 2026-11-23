import { createBrowserRouter } from "react-router-dom"
import { RequireAuth } from "@/features/auth/RequireAuth"
import { ShellRoot } from "@/features/auth/ShellRoot"
import AddHomePage from "@/pages/AddHomePage"
import DeleteAccountPage from "@/pages/DeleteAccountPage"
import EditHomePage from "@/pages/EditHomePage"
import EditTenancyPage from "@/pages/EditTenancyPage"
import HomePage from "@/pages/HomePage"
import LandlordDashboardPage from "@/pages/LandlordDashboardPage"
import LandlordPage from "@/pages/LandlordPage"
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
import WriteReviewPage from "@/pages/WriteReviewPage"
import { RouteError } from "@/routes/RouteError"

export const routes = [
  // The landing draws its own minimal page, without the app shell.
  { path: "/", element: <HomePage />, errorElement: <RouteError /> },
  {
    element: <ShellRoot />,
    errorElement: <RouteError />,
    children: [
      { path: "/p/:id", element: <PropertyPage /> },
      // Search is open to everyone, signed in or not (captain 2026-11-24 00:31, D-162).
      { path: "/search", element: <SearchPage /> },
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
          { path: "/welcome/created", element: <WelcomeCreatedPage /> },
          { path: "/verify/*", element: <VerifyPage /> },
          { path: "/settings", element: <SettingsPage /> },
          { path: "/settings/delete-account", element: <DeleteAccountPage /> },
          { path: "/me/tenancies", element: <MyTenanciesPage /> },
          { path: "/homes/new", element: <AddHomePage /> },
          { path: "/homes/:id/edit", element: <EditHomePage /> },
          { path: "/tenancies/new", element: <TenancyRequestPage /> },
          { path: "/tenancies/:id/review", element: <WriteReviewPage /> },
          { path: "/tenancies/:id/edit", element: <EditTenancyPage /> },
          { path: "/u/:id", element: <LandlordPage /> },
          { path: "/tenancies/:id", element: <TenancyPage /> },
          { path: "/tenancy-invite", element: <TenancyInvitePage /> },
          // Owner mode's home (PLAN R10): open to every signed-in user; without the landlord
          // role it shows how to add a home you own. The listing pages stay landlord-only.
          { path: "/me/dashboard", element: <LandlordDashboardPage /> },
          { path: "/me/*", element: <NotFoundPage /> },
        ],
      },
      {
        element: <RequireAuth role="LANDLORD" />,
        children: [
          { path: "/me/properties", element: <LandlordDashboardPage /> },
          { path: "/me/properties/new", element: <PropertyFormPage /> },
          { path: "/me/properties/:id/edit", element: <PropertyFormPage /> },
        ],
      },
      { path: "*", element: <NotFoundPage /> },
    ],
  },
]

export const router = createBrowserRouter(routes)
