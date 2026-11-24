// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { cleanup, render, screen, within } from "@testing-library/react"
import { createMemoryRouter, RouterProvider } from "react-router-dom"
import { AppLayout } from "@/components/layout/AppLayout"
import { CONTACT, SOCIAL_LINKS, visibleSocialLinks } from "@/lib/contact"
import { LAUNCH_CITIES } from "@/test/cities"
import ContactPage from "./ContactPage"

vi.mock("@/api/cities", () => ({
  cityKeys: { all: ["cities"] },
  fetchCities: vi.fn(async () => LAUNCH_CITIES),
}))

afterEach(cleanup)

function renderContact() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter(
    [{ element: <AppLayout />, children: [{ path: "/contact", element: <ContactPage /> }] }],
    { initialEntries: ["/contact"] },
  )
  render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}

describe("contact config", () => {
  it("keeps the public email and the LinkedIn page in one place", () => {
    expect(CONTACT.email).toBe("hello@doorwayliving.in")
    expect(CONTACT.linkedinUrl).toBe("https://www.linkedin.com/company/doorwayliving/")
    expect(SOCIAL_LINKS.find((l) => l.name === "LinkedIn")?.url).toBe(CONTACT.linkedinUrl)
  })

  it("hides social entries that have no URL yet", () => {
    expect(
      visibleSocialLinks([
        { name: "LinkedIn", url: "https://example.com/in", icon: "link" },
        { name: "Instagram", url: "", icon: "link" },
        { name: "X (Twitter)", url: "  ", icon: "link" },
      ]).map((l) => l.name),
    ).toEqual(["LinkedIn"])
  })
})

describe("contact page", () => {
  it("shows the mailto link, only the filled social links and the About block", async () => {
    renderContact()
    const main = screen.getByRole("main")
    expect(within(main).getByRole("heading", { level: 1, name: "Contact us" })).toBeTruthy()
    expect(screen.getByTestId("contact-email").getAttribute("href")).toBe(
      "mailto:hello@doorwayliving.in",
    )
    const social = screen.getAllByTestId("contact-social")
    expect(social.map((a) => a.getAttribute("href"))).toEqual([CONTACT.linkedinUrl])
    expect(within(main).queryByText(/Instagram|Twitter/)).toBeNull()
    expect((await screen.findByTestId("contact-cities")).textContent).toContain(
      "Live in Bangalore and Pune",
    )
    expect(within(main).getByText("Not verified yet")).toBeTruthy()
    expect(within(main).getByText("Verified by Doorway Living")).toBeTruthy()
    expect(within(main).queryByRole("textbox")).toBeNull()
  })

  it("is linked as Contact us from the footer", () => {
    renderContact()
    expect(
      within(screen.getByRole("contentinfo"))
        .getByRole("link", { name: "Contact us" })
        .getAttribute("href"),
    ).toBe("/contact")
  })
})
