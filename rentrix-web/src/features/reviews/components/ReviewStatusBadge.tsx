import { CheckCircle2, Clock, XCircle } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import type { ReviewStatus } from "@/types"

const config: Record<ReviewStatus, { label: string; className: string; Icon: typeof Clock }> = {
  PENDING: {
    label: "Pending",
    className: "bg-yellow-100 text-yellow-800 dark:bg-yellow-950/40 dark:text-yellow-400",
    Icon: Clock,
  },
  APPROVED: {
    label: "Approved",
    className: "bg-green-100 text-green-800 dark:bg-green-950/40 dark:text-green-400",
    Icon: CheckCircle2,
  },
  REJECTED: {
    label: "Rejected",
    className: "bg-red-100 text-red-800 dark:bg-red-950/40 dark:text-red-400",
    Icon: XCircle,
  },
}

export function ReviewStatusBadge({ status }: { status: ReviewStatus }) {
  const { label, className, Icon } = config[status]
  return (
    <Badge className={className}>
      <Icon className="mr-1 h-3 w-3" />
      {label}
    </Badge>
  )
}
