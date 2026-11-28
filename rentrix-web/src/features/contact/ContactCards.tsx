import type { ReactNode } from "react"
import { ArrowRightIcon, LinkIcon, MailIcon } from "@/components/ui/icons.gen"
import { BRAND } from "@/lib/brand"
import { CONTACT } from "@/lib/contact"

function Card({
  href,
  external,
  icon,
  label,
  value,
  testId,
}: {
  href: string
  external?: boolean
  icon: ReactNode
  label: string
  value: string
  testId: string
}) {
  return (
    <a
      href={href}
      data-testid={testId}
      {...(external ? { target: "_blank", rel: "noopener noreferrer" } : {})}
      className="group flex items-center gap-4 rounded-3xl border border-line bg-surface p-5 no-underline transition-colors hover:border-teal-600 hover:no-underline"
    >
      <span className="grid size-12 shrink-0 place-items-center rounded-2xl bg-teal-50 text-teal-700">
        {icon}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block text-xs font-semibold tracking-[0.1em] text-ink-500 uppercase">
          {label}
        </span>
        <span className="block truncate text-[17px] font-semibold text-ink-900">{value}</span>
      </span>
      <ArrowRightIcon
        size={20}
        className="shrink-0 text-ink-500 transition-transform group-hover:translate-x-0.5 group-hover:text-teal-700"
      />
    </a>
  )
}

/** The two ways to reach the team, as icon cards: LinkedIn and email, from `lib/contact.ts`. */
export function ContactCards() {
  return (
    <div className="grid gap-3">
      <Card
        href={CONTACT.linkedinUrl}
        external
        icon={<LinkIcon size={24} />}
        label="LinkedIn"
        value={`${BRAND.name} on LinkedIn`}
        testId="contact-linkedin"
      />
      <Card
        href={`mailto:${CONTACT.email}`}
        icon={<MailIcon size={24} />}
        label="Email"
        value={CONTACT.email}
        testId="contact-mail"
      />
    </div>
  )
}
