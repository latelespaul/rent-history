import type { ReactNode } from "react"
import { Container } from "@/components/layout/Container"
import { Badge, VerifiedBadge } from "@/components/ui/badge"
import { Icon } from "@/components/ui/icon"
import { MailIcon } from "@/components/ui/icons.gen"
import { launchCopy } from "@/features/city/cityText"
import { useCities } from "@/features/city/useCities"
import { FeedbackForm } from "@/features/contact/FeedbackForm"
import { BRAND } from "@/lib/brand"
import { CONTACT, visibleSocialLinks } from "@/lib/contact"

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="rounded-xl border border-line bg-surface p-5 shadow-sm sm:p-6">
      <h2 className="text-xl font-semibold text-ink-900">{title}</h2>
      <div className="mt-4">{children}</div>
    </section>
  )
}

/** One contact row: an icon tile, a small label and the link, with a 44 px tap target. */
function ContactRow({
  icon,
  label,
  children,
}: {
  icon: ReactNode
  label: string
  children: ReactNode
}) {
  return (
    <li className="flex min-h-11 items-center gap-3">
      <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-teal-50 text-teal-700">
        {icon}
      </span>
      <span className="min-w-0">
        <span className="block text-xs font-semibold text-ink-500">{label}</span>
        {children}
      </span>
    </li>
  )
}

/**
 * `/contact`, open to everyone: the team email, the social accounts that have a URL, the same
 * short message form as the landing's "Contact us", and an "About" of what the site is and what
 * the review ticks mean.
 */
export default function ContactPage() {
  const cities = useCities()
  const linkClass = "font-medium break-all text-teal-700"
  return (
    <main>
      <Container className="max-w-[720px] space-y-6 py-10 nav:py-14">
        <div>
          <h1 className="font-display text-3xl font-semibold text-ink-900">Contact us</h1>
          <p className="mt-2 text-ink-500">Write to us any time. We reply by email.</p>
        </div>

        <Section title="Contact">
          <ul className="space-y-4" data-testid="contact-links">
            <ContactRow icon={<MailIcon />} label="Email">
              <a href={`mailto:${CONTACT.email}`} className={linkClass} data-testid="contact-email">
                {CONTACT.email}
              </a>
            </ContactRow>
            {visibleSocialLinks().map((link) => (
              <ContactRow key={link.name} icon={<Icon name={link.icon} />} label={link.name}>
                <a
                  href={link.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className={linkClass}
                  data-testid="contact-social"
                >
                  {BRAND.name} on {link.name}
                </a>
              </ContactRow>
            ))}
          </ul>
        </Section>

        <section aria-labelledby="contact-form-title" className="space-y-4">
          <h2 id="contact-form-title" className="text-xl font-semibold text-ink-900">
            Send us a message
          </h2>
          <FeedbackForm />
        </section>

        <Section title={`About ${BRAND.name}`}>
          <div className="space-y-3 text-ink-700">
            <p>
              {BRAND.name} shows real rents and reviews of rented homes, from the tenants and owners
              who lived there.
            </p>
            {cities.isSuccess && <p data-testid="contact-cities">{launchCopy(cities.data)}.</p>}
            <ul className="space-y-3">
              <li className="flex flex-wrap items-center gap-x-2 gap-y-1">
                <VerifiedBadge>Verified by {BRAND.name}</VerifiedBadge>
                <span>Our team checked the tenancy. Only these count in the rating.</span>
              </li>
              <li className="flex flex-wrap items-center gap-x-2 gap-y-1">
                <Badge>Not verified yet</Badge>
                <span>Posted at once and shown with this label until it is verified.</span>
              </li>
            </ul>
          </div>
        </Section>
      </Container>
    </main>
  )
}
