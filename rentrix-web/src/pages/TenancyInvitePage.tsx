import { useEffect, useRef } from "react"
import { useMutation } from "@tanstack/react-query"
import { Link, useNavigate, useSearchParams } from "react-router-dom"
import { claimInvite } from "@/api/tenancies"
import { Container } from "@/components/layout/Container"
import { Button } from "@/components/ui/button"
import { EmptyState, ErrorState } from "@/components/ui/states"
import { useRefreshTenancyData } from "@/features/tenancy/useTenancies"

/**
 * `/tenancy-invite?token=` (the link in the invite email, D-34): binds the invite to the
 * signed-in account, then opens the tenancy to confirm or decline.
 */
export default function TenancyInvitePage() {
  const [params] = useSearchParams()
  const token = params.get("token") ?? ""
  const navigate = useNavigate()
  const refresh = useRefreshTenancyData()
  const claim = useMutation({
    mutationFn: () => claimInvite(token),
    onSuccess: async (tenancy) => {
      await refresh()
      navigate(`/tenancies/${tenancy.id}`, { replace: true })
    },
  })
  const started = useRef(false)
  useEffect(() => {
    if (token && !started.current) {
      started.current = true
      claim.mutate()
    }
  }, [token, claim])

  return (
    <main>
      <Container className="py-12">
        {!token ? (
          <EmptyState icon="mail" title="This invite link is incomplete" />
        ) : claim.isError ? (
          <ErrorState
            title="The invite could not be opened"
            error={claim.error}
            action={
              <Button asChild variant="outline">
                <Link to="/me/tenancies">My tenancies</Link>
              </Button>
            }
          />
        ) : (
          <p role="status" className="text-center text-sm text-ink-500">
            Opening your tenancy invite…
          </p>
        )}
      </Container>
    </main>
  )
}
