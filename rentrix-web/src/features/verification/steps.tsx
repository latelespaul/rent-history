import { useEffect, useState, type FormEvent, type ReactNode } from "react"
import { useMutation } from "@tanstack/react-query"
import { Link, useNavigate } from "react-router-dom"
import { updateMe } from "@/api/auth"
import { isApiError } from "@/api/errors"
import {
  confirmPhoneCheck,
  removeLinkedin,
  requestVerification,
  saveLinkedin,
  startPhoneCheck,
} from "@/api/verification"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { CheckIcon, DocumentIcon, LinkIcon, MessageIcon } from "@/components/ui/icons.gen"
import { Input } from "@/components/ui/input"
import { NAME_NOT_SHOWN } from "@/features/auth/paths"
import { Label } from "@/components/ui/label"
import { Notice } from "@/components/ui/notice"
import { errorMessage } from "@/components/ui/states"
import { useAuth } from "@/features/auth/authContext"
import { CityPicker } from "@/features/city/CityPicker"
import { BRAND } from "@/lib/brand"
import type {
  IdentityMethodView,
  IdentityView,
  PhoneChallenge,
  VerificationEntry,
} from "@/types/api"
import { useNext, useWithNext } from "./next"
import { StepFrame } from "./StepFrame"
import { canUseTruecaller } from "./truecaller"
import { TruecallerCard } from "./TruecallerCard"
import { useRefreshVerification } from "./useVerification"

/** Seconds before "Resend code" opens again. The API also limits codes per hour (D-101). */
const RESEND_AFTER = 60

function method(identity: IdentityView | undefined, name: IdentityMethodView["method"]) {
  return identity?.methods.find((m) => m.method === name)
}

/** The account's phone: whichever of SMS code, Truecaller or Telegram verified it, else the SMS method. */
function phoneMethod(identity: IdentityView | undefined) {
  const verified = ["PHONE_OTP", "TRUECALLER", "TELEGRAM"]
    .map((name) => method(identity, name as IdentityMethodView["method"]))
    .find((m) => m?.status === "VERIFIED")
  return verified ?? method(identity, "PHONE_OTP")
}

/**
 * The verified method other than the phone (D-144): masked Aadhaar or LinkedIn, else null. No
 * method is needed to post (D-163); one leads to the green tick.
 */
function otherPostingMethod(identity: IdentityView | undefined): string | null {
  if (method(identity, "AADHAAR")?.status === "VERIFIED") return "Aadhaar"
  if (method(identity, "LINKEDIN")?.status === "VERIFIED") return "LinkedIn"
  return null
}

/**
 * Pick any one (D-144): the phone form is on this step; Aadhaar and LinkedIn on the next one. It is
 * optional: posting needs no verification (D-163), a verified method leads to the green tick.
 */
function AnyOneNotice({ identity }: { identity: IdentityView | undefined }) {
  const withNext = useWithNext()
  const other = otherPostingMethod(identity)
  if (other) {
    return (
      <Notice tone="teal" icon="shield-check">
        <b>{other} verified.</b> That is all you need for the green tick. A phone is an optional
        extra.
      </Notice>
    )
  }
  return (
    <p className="text-sm text-ink-700">
      Optional: you can post without it. Pick any one: phone, Aadhaar or LinkedIn, to get a green
      tick. Verify your phone here, or{" "}
      <Link to={withNext("/verify/id")}>use Aadhaar or LinkedIn</Link> instead. The others are
      optional extra badges.
    </p>
  )
}

/** "+91 98765 43210", "9876543210" → "+919876543210"; other input is sent as typed for the API to judge. */
function toE164(input: string): string {
  const digits = input.replace(/[\s()-]/g, "")
  if (/^[6-9]\d{9}$/.test(digits)) return `+91${digits}`
  if (/^91[6-9]\d{9}$/.test(digits)) return `+${digits}`
  return digits
}

function StepActions({
  back,
  skip,
  children,
}: {
  back?: string
  skip?: string
  children?: ReactNode
}) {
  const withNext = useWithNext()
  return (
    <div className="mt-6 flex flex-wrap items-center gap-2 border-t border-line pt-4">
      {back && (
        <Button asChild variant="ghost">
          <Link to={withNext(back)}>Back</Link>
        </Button>
      )}
      <span className="flex-1" />
      {skip && (
        <Button asChild variant="outline">
          <Link to={withNext(skip)}>Skip</Link>
        </Button>
      )}
      {children}
    </div>
  )
}

/** Step 1 · Profile (D-120): display name and an optional home city, saved with `PATCH /me`. */
export function ProfileStep({ entries }: { entries: VerificationEntry[] }) {
  const { state, acceptUpdate } = useAuth()
  const navigate = useNavigate()
  const withNext = useWithNext()
  const refresh = useRefreshVerification()
  const user = state.user
  const [displayName, setDisplayName] = useState(user?.displayName ?? "")
  const [homeCity, setHomeCity] = useState<string | null>(user?.homeCity ?? null)
  const [problem, setProblem] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: () => updateMe({ displayName: displayName.trim(), homeCity: homeCity ?? "" }),
    onSuccess: async (update) => {
      acceptUpdate(update)
      await refresh()
      navigate(withNext("/verify/phone"))
    },
  })
  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    const length = displayName.trim().length
    if (length < 2 || length > 60) return setProblem("Use 2 to 60 characters for your name.")
    setProblem(null)
    save.mutate()
  }
  return (
    <StepFrame step="profile" entries={entries}>
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        <div className="space-y-1.5">
          <Label htmlFor="verify-name">Your name</Label>
          <Input
            id="verify-name"
            value={displayName}
            maxLength={60}
            onChange={(event) => setDisplayName(event.target.value)}
          />
          <p className="text-xs text-ink-500">{NAME_NOT_SHOWN}</p>
        </div>
        <CityPicker
          value={homeCity}
          onChange={setHomeCity}
          label="Home city (optional)"
          className="max-w-[360px]"
        />
        {(problem || save.error) && (
          <Notice tone="danger">{problem ?? errorMessage(save.error)}</Notice>
        )}
        <StepActions>
          <Button type="submit" disabled={save.isPending}>
            {save.isPending ? "Saving…" : "Save and continue"}
          </Button>
        </StepActions>
      </form>
    </StepFrame>
  )
}

/** The phone card's countdown, "Resend code in 0:42". */
function useCountdown(from: number | null): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (from === null) return
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [from])
  if (from === null) return 0
  // `now` can trail `from` until the first tick: never show more than the full wait.
  return Math.min(RESEND_AFTER, Math.max(0, RESEND_AFTER - Math.floor((now - from) / 1000)))
}

function clock(seconds: number): string {
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, "0")}`
}

/**
 * Step 2 · Verify (any one), required (D-144, mockup `verify-v4-phone.html`): any one of phone,
 * masked Aadhaar or LinkedIn lets the user post; this step holds the phone. "Verify with Truecaller" first
 * (Android with a Truecaller key on the API), else or after it an SMS code. With `?next=` the user
 * goes straight back to what they were doing (D-119).
 */
export function PhoneStep({
  entries,
  identity,
}: {
  entries: VerificationEntry[]
  identity: IdentityView | undefined
}) {
  const navigate = useNavigate()
  const next = useNext()
  const withNext = useWithNext()
  const refresh = useRefreshVerification()
  const phone = phoneMethod(identity)
  const sms = method(identity, "PHONE_OTP")
  const verified = phone?.status === "VERIFIED"
  const truecaller = method(identity, "TRUECALLER")?.available === true && canUseTruecaller()
  // Telegram (D-146) is the default way to get the code when it is on; SMS is the fallback.
  const telegram = method(identity, "TELEGRAM")?.available === true
  const smsAvailable = sms?.available === true
  // Null until the user picks: the default follows the identity data once it has loaded.
  const [channel, setChannel] = useState<"SMS" | null>(null)
  const viaTelegram = telegram && channel !== "SMS"
  const [changing, setChanging] = useState(false)
  const [number, setNumber] = useState("")
  const [challenge, setChallenge] = useState<PhoneChallenge | null>(null)
  const [sentAt, setSentAt] = useState<number | null>(null)
  const [code, setCode] = useState("")
  const wait = useCountdown(sentAt)

  const confirm = useMutation({
    mutationFn: () => confirmPhoneCheck(challenge?.challengeId ?? "", code.trim()),
    onSuccess: async () => {
      await refresh()
      setChanging(false)
      setChallenge(null)
      navigate(next ?? withNext("/verify/id"))
    },
  })
  const start = useMutation({
    mutationFn: () => startPhoneCheck(toE164(number), viaTelegram ? "TELEGRAM" : "SMS"),
    onSuccess: (sent) => {
      setChallenge(sent)
      setSentAt(Date.now())
      setCode("")
      confirm.reset()
    },
  })

  const truecallerVerified = async () => {
    await refresh()
    setChanging(false)
    setChallenge(null)
    navigate(next ?? withNext("/verify/id"))
  }

  if (sms && !sms.available && !verified && !truecaller && !telegram) {
    return (
      <StepFrame step="phone" entries={entries}>
        <AnyOneNotice identity={identity} />
        <Notice tone="warn" className="mt-4">
          <b>Phone verification is not available yet.</b> Use{" "}
          <Link to={withNext("/verify/id")}>Aadhaar or LinkedIn</Link> instead, or come back later.
        </Notice>
        <StepActions back="/verify/profile" />
      </StepFrame>
    )
  }

  if (verified && !changing) {
    return (
      <StepFrame step="phone" entries={entries}>
        <Notice tone="teal" icon="shield-check">
          <b>Phone verified · {phone.masked}.</b> That is all you need for the green tick. Your
          number is private: it is never shown on your profile.
        </Notice>
        <StepActions back="/verify/profile">
          <Button variant="outline" onClick={() => setChanging(true)}>
            Change number
          </Button>
          <Button asChild>
            <Link to={next ?? withNext("/verify/id")}>
              {next ? "Back to where you were" : "Continue"}
            </Link>
          </Button>
        </StepActions>
      </StepFrame>
    )
  }

  const expired = confirm.error && isApiError(confirm.error) && confirm.error.code !== "otp_invalid"

  return (
    <StepFrame step="phone" entries={entries}>
      <div className="space-y-6">
        <AnyOneNotice identity={identity} />
        {truecaller && <TruecallerCard onVerified={truecallerVerified} />}
        {truecaller && sms && !sms.available && !telegram && (
          <Notice tone="warn">SMS codes are not available yet. Use Truecaller for now.</Notice>
        )}
        {(!truecaller || sms?.available !== false || telegram) && (
          <>
            {truecaller && (
              <p className="text-sm font-medium text-ink-700">
                {viaTelegram ? "Or get a code on Telegram" : "Or verify with an SMS code"}
              </p>
            )}
            {viaTelegram && (
              <p className="text-sm text-ink-700">We send the code to your Telegram.</p>
            )}
            <form
              noValidate
              onSubmit={(event) => {
                event.preventDefault()
                start.mutate()
              }}
              className="space-y-2"
            >
              <Label htmlFor="verify-phone">1 · Your mobile number</Label>
              <div className="flex flex-wrap gap-2">
                <Input
                  id="verify-phone"
                  type="tel"
                  inputMode="tel"
                  autoComplete="tel"
                  placeholder="+91 98765 43210"
                  value={number}
                  disabled={challenge !== null}
                  onChange={(event) => setNumber(event.target.value)}
                  className="max-w-[260px]"
                />
                {challenge ? (
                  <>
                    <Badge variant="success" className="self-center">
                      {challenge.delivery === "TEAM" ? "Code requested" : "Code sent"}
                    </Badge>
                    <Button
                      type="button"
                      variant="ghost"
                      onClick={() => {
                        setChallenge(null)
                        setSentAt(null)
                      }}
                    >
                      Change number
                    </Button>
                  </>
                ) : (
                  <Button type="submit" disabled={start.isPending || number.trim() === ""}>
                    <MessageIcon />
                    {start.isPending
                      ? "Sending…"
                      : viaTelegram
                        ? "Send code to Telegram"
                        : "Send code"}
                  </Button>
                )}
              </div>
              <p className="text-xs text-ink-500">
                Indian mobile numbers only. Private: never shown on your profile.
                {viaTelegram && " The number needs a Telegram account."}
              </p>
              {start.error && (
                <Notice tone="danger">
                  {isApiError(start.error) && start.error.code === "telegram_send_failed"
                    ? start.error.message
                    : errorMessage(start.error)}
                </Notice>
              )}
              {viaTelegram && smsAvailable && !challenge && (
                <Button
                  type="button"
                  variant="link"
                  size="xs"
                  onClick={() => {
                    setChannel("SMS")
                    start.reset()
                  }}
                >
                  No Telegram? Get the code by SMS
                </Button>
              )}
            </form>
          </>
        )}

        {challenge && (
          <form
            noValidate
            onSubmit={(event) => {
              event.preventDefault()
              confirm.mutate()
            }}
            className="space-y-2"
          >
            <Label htmlFor="verify-code">2 · Enter the 6-digit code</Label>
            <p className="text-sm text-ink-500">
              {challenge.delivery === "TELEGRAM"
                ? "We sent it to your Telegram: open Telegram to see it. It works for 10 minutes."
                : challenge.delivery === "TEAM"
                  ? `We'll message you the code shortly (WhatsApp/SMS from the ${BRAND.name} team). It works for 10 minutes.`
                  : "We sent it by SMS. It works for 10 minutes."}
            </p>
            <Input
              id="verify-code"
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              value={code}
              onChange={(event) => setCode(event.target.value.replace(/\D/g, ""))}
              className="max-w-[160px] text-center font-mono text-lg tracking-[0.4em]"
            />
            <p className="flex flex-wrap items-center gap-3 text-xs text-ink-500">
              {wait > 0 ? (
                <span>Resend code in {clock(wait)}</span>
              ) : (
                <Button
                  type="button"
                  variant="link"
                  size="xs"
                  disabled={start.isPending}
                  onClick={() => start.mutate()}
                >
                  Resend code
                </Button>
              )}
            </p>
            {confirm.error && (
              <Notice tone={expired ? "warn" : "danger"}>
                {errorMessage(confirm.error)}
                {expired && " Send a new code."}
              </Notice>
            )}
            <StepActions back="/verify/profile">
              <Button type="submit" disabled={confirm.isPending || code.length !== 6}>
                <CheckIcon />
                {confirm.isPending ? "Checking…" : "Verify and continue"}
              </Button>
            </StepActions>
          </form>
        )}
        {!challenge && <StepActions back="/verify/profile" />}
      </div>
    </StepFrame>
  )
}

function ComingSoonCard({ title, text }: { title: string; text: string }) {
  return (
    <div className="min-w-0 rounded-lg border border-line p-4">
      <p className="flex flex-wrap items-center gap-2">
        <DocumentIcon size={18} className="text-ink-500" />
        <b className="text-ink-900">{title}</b>
        <Badge>Coming soon</Badge>
      </p>
      <p className="mt-1 text-sm text-ink-500">{text}</p>
      <p className="mt-1 text-xs text-ink-500">Gives &ldquo;ID verified&rdquo;</p>
    </div>
  )
}

/**
 * Step 3 · Aadhaar, LinkedIn, PAN (D-120, D-122, D-144, mockup `verify-v4-id.html`): masked Aadhaar
 * or LinkedIn, once checked, is the one method that lets the user post when they skip the phone;
 * otherwise they are optional extra badges. PAN and masked Aadhaar wait for the document upload
 * (PLAN B12); LinkedIn works now (D-93).
 */
export function IdStep({
  entries,
  identity,
}: {
  entries: VerificationEntry[]
  identity: IdentityView | undefined
}) {
  const withNext = useWithNext()
  const refresh = useRefreshVerification()
  const phone = phoneMethod(identity)
  const linkedin = method(identity, "LINKEDIN")
  const [url, setUrl] = useState("")
  const save = useMutation({
    mutationFn: () => saveLinkedin(url.trim()),
    onSuccess: async () => {
      setUrl("")
      await refresh()
    },
  })
  const remove = useMutation({ mutationFn: removeLinkedin, onSuccess: () => refresh() })
  const linkedinStatus = linkedin?.status

  return (
    <StepFrame step="id" entries={entries}>
      {phone?.status === "VERIFIED" || otherPostingMethod(identity) ? (
        <Notice tone="teal" icon="shield-check">
          <b>
            {phone?.status === "VERIFIED"
              ? `Phone verified · ${phone.masked}.`
              : `${otherPostingMethod(identity)} verified.`}
          </b>{" "}
          That is all you need for the green tick. The others below are optional extra badges.
        </Notice>
      ) : (
        <Notice tone="info">
          Optional: you can post without it. Any one gets you the green tick: Aadhaar or LinkedIn
          below once our team checks it, or <Link to={withNext("/verify/phone")}>your phone</Link>{" "}
          at once.
        </Notice>
      )}
      <div className="mt-4 grid grid-cols-1 gap-3 nav:grid-cols-3">
        <ComingSoonCard
          title="PAN card"
          text="Type your PAN and upload the card. An admin checks it; the file is deleted after the check."
        />
        <ComingSoonCard
          title="Masked Aadhaar"
          text="Upload the masked copy from UIDAI and type its last 4 digits only. Never the full number."
        />
        <div className="min-w-0 rounded-lg border border-line p-4">
          <p className="flex flex-wrap items-center gap-2">
            <LinkIcon size={18} className="text-ink-500" />
            <b className="text-ink-900">LinkedIn</b>
            <Badge
              variant={linkedinStatus === "VERIFIED" ? "success" : linkedinStatus ? "warn" : "info"}
            >
              {linkedinStatus === "VERIFIED"
                ? "Verified"
                : linkedinStatus === "PENDING"
                  ? "Waiting for a check"
                  : linkedinStatus === "REJECTED"
                    ? "Not accepted"
                    : "Available"}
            </Badge>
          </p>
          <p className="mt-1 text-sm text-ink-500">Never fetched; an admin opens it.</p>
          {linkedin?.masked ? (
            <div className="mt-2 space-y-2">
              <p className="truncate text-sm text-ink-700">{linkedin.masked}</p>
              <Button
                variant="ghost"
                size="sm"
                disabled={remove.isPending}
                onClick={() => remove.mutate()}
              >
                Remove
              </Button>
            </div>
          ) : (
            <form
              noValidate
              className="mt-2 space-y-2"
              onSubmit={(event) => {
                event.preventDefault()
                save.mutate()
              }}
            >
              <Label htmlFor="verify-linkedin" className="sr-only">
                LinkedIn profile URL
              </Label>
              <Input
                id="verify-linkedin"
                type="url"
                placeholder="linkedin.com/in/your-name"
                value={url}
                onChange={(event) => setUrl(event.target.value)}
              />
              <Button
                type="submit"
                size="sm"
                variant="outline"
                disabled={save.isPending || !url.trim()}
              >
                Save link
              </Button>
            </form>
          )}
          <p className="mt-1 text-xs text-ink-500">Counts for &ldquo;ID verified&rdquo;</p>
          {(save.error || remove.error) && (
            <Notice tone="danger" className="mt-2">
              {errorMessage(save.error ?? remove.error)}
            </Notice>
          )}
        </div>
      </div>
      <p className="mt-4 text-xs text-ink-500">
        Others see only the badges (&ldquo;Google verified&rdquo;, &ldquo;ID verified&rdquo;) and a
        label such as &ldquo;Verified tenant&rdquo;, never your name or your stay dates. The method,
        the last 4 digits and your phone stay with you and admins.
      </p>
      <StepActions back="/verify/phone" skip="/verify/rental">
        <Button asChild>
          <Link to={withNext("/verify/rental")}>Continue</Link>
        </Button>
      </StepActions>
    </StepFrame>
  )
}

/**
 * Step 4 · Rental details, optional (D-120): the rental agreement upload and its checks arrive with
 * PLAN B18; until then the step says so and can be skipped.
 */
export function RentalStep({ entries }: { entries: VerificationEntry[] }) {
  const withNext = useWithNext()
  return (
    <StepFrame step="rental" entries={entries}>
      <p className="font-semibold text-ink-900">The home you rent now or rented last</p>
      <p className="mt-1 text-sm text-ink-500">
        Soon you can upload its rental agreement and type the key details from it. It helps the
        admin give you the green tick, and a tenancy you record here can then earn an
        &ldquo;Agreement verified&rdquo; badge.
      </p>
      <Notice tone="info" className="mt-4">
        <b>Coming soon.</b> Rental agreement upload is not open yet. Skip this step for now; you can
        add it later from this page.
      </Notice>
      <StepActions back="/verify/id" skip="/verify/submit">
        <Button asChild>
          <Link to={withNext("/verify/submit")}>Continue</Link>
        </Button>
      </StepActions>
    </StepFrame>
  )
}

const MISSING_STEP: Record<string, { label: string; path: string }> = {
  profile: { label: "Profile: your name", path: "/verify/profile" },
  method: { label: "Verify with any one: phone, Aadhaar or LinkedIn", path: "/verify/phone" },
}

/**
 * Submit · "Request verification" (D-133): optional, because posting is open to every signed-in
 * user (D-163). Sends every held role still in `DRAFT` or `REJECTED`.
 */
export function SubmitStep({
  entries,
  identity,
}: {
  entries: VerificationEntry[]
  identity: IdentityView | undefined
}) {
  const navigate = useNavigate()
  const next = useNext()
  const withNext = useWithNext()
  const refresh = useRefreshVerification()
  const open = entries.filter((e) => e.status === "DRAFT" || e.status === "REJECTED")
  const phone = phoneMethod(identity)
  const steps = entries[0]?.steps
  const send = useMutation({
    mutationFn: async () => {
      for (const entry of open) await requestVerification(entry.role)
    },
    // Refetch before leaving: `/verify` decides from the new status, so a stale DRAFT would send the
    // user back into the steps instead of showing "Waiting for review".
    onSuccess: async () => {
      await refresh()
      navigate(withNext("/verify"))
    },
    onError: () => refresh(),
  })
  const missing =
    isApiError(send.error) && send.error.code === "verification_incomplete"
      ? (send.error.problem.missing ?? [])
      : []

  return (
    <section className="rounded-xl border border-line bg-surface p-6 shadow-sm nav:p-8">
      <p className="text-xs font-semibold tracking-wide text-teal-700 uppercase">
        Verify your account
      </p>
      <h1 className="mt-1 font-display text-2xl font-semibold text-ink-900">
        Request verification
      </h1>
      <p className="mt-1.5 text-ink-500">
        Optional. You can already post without it. Our team checks your ID and gives your profile
        the green tick &ldquo;Verified by {BRAND.name}&rdquo;, usually within 48 hours.
      </p>
      <ul className="mt-4 space-y-2 text-sm">
        <li>
          <b>Profile</b> · {steps?.profile === "DONE" ? "done" : "missing"}
        </li>
        <li>
          <b>Verified method</b> ·{" "}
          {phone?.status === "VERIFIED"
            ? `phone ${phone.masked}`
            : (otherPostingMethod(identity) ??
              (steps?.method === "PENDING" ? "waiting for a check" : "missing"))}
        </li>
        <li>
          <b>ID (optional)</b> ·{" "}
          {steps?.identity === "DONE"
            ? "verified"
            : steps?.identity === "PENDING"
              ? "waiting for a check"
              : steps?.linkedin === "DONE" || steps?.linkedin === "PENDING"
                ? "LinkedIn added"
                : "skipped · you can add PAN or Aadhaar later"}
        </li>
        <li>
          <b>Rental details (optional)</b> ·{" "}
          {steps?.rentalAgreement === "DONE" ? "added" : "skipped"}
        </li>
      </ul>
      {missing.length > 0 && (
        <Notice tone="warn" className="mt-4">
          <b>Finish these first:</b>
          <ul className="mt-1 list-disc pl-5">
            {missing.map((key) => (
              <li key={key}>
                {MISSING_STEP[key] ? (
                  <Link to={withNext(MISSING_STEP[key].path)}>{MISSING_STEP[key].label}</Link>
                ) : (
                  key
                )}
              </li>
            ))}
          </ul>
        </Notice>
      )}
      {send.error && missing.length === 0 && (
        <Notice tone="danger" className="mt-4">
          {errorMessage(send.error)}
        </Notice>
      )}
      <div className="mt-6 flex flex-wrap items-center gap-2 border-t border-line pt-4">
        <Button asChild variant="ghost">
          <Link to={withNext("/verify/rental")}>Back</Link>
        </Button>
        <span className="flex-1" />
        {next && (
          <Button asChild variant="outline">
            <Link to={next}>Not now</Link>
          </Button>
        )}
        {open.length > 0 ? (
          <Button disabled={send.isPending} onClick={() => send.mutate()}>
            {send.isPending ? "Sending…" : "Request verification"}
          </Button>
        ) : (
          <Button asChild>
            <Link to={withNext("/verify")}>See status</Link>
          </Button>
        )}
      </div>
    </section>
  )
}
