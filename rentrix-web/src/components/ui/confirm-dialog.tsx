import type { ReactNode } from "react"
import { Dialog } from "radix-ui"
import { Button } from "@/components/ui/button"

/**
 * A small in-page confirm (never the browser's `confirm()`): a title, what will happen, and the
 * confirm and cancel buttons. Used before a save or delete that needs the user's yes.
 */
export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  children,
  confirmLabel,
  danger = false,
  busy = false,
  onConfirm,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  children: ReactNode
  confirmLabel: string
  danger?: boolean
  busy?: boolean
  onConfirm: () => void
}) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-50 bg-black/30" />
        <Dialog.Content
          data-testid="confirm-dialog"
          className="fixed top-1/2 left-1/2 z-50 w-[calc(100%-2rem)] max-w-md -translate-x-1/2 -translate-y-1/2 space-y-4 rounded-xl bg-surface p-5 shadow-lg"
        >
          <Dialog.Title className="text-lg font-semibold text-ink-900">{title}</Dialog.Title>
          <Dialog.Description asChild>
            <div className="space-y-2 text-sm text-ink-700">{children}</div>
          </Dialog.Description>
          <div className="flex flex-wrap justify-end gap-2">
            <Dialog.Close asChild>
              <Button variant="outline" disabled={busy}>
                Cancel
              </Button>
            </Dialog.Close>
            <Button
              data-testid="confirm-dialog-ok"
              className={danger ? "bg-danger-600 text-white hover:bg-danger-600/90" : undefined}
              disabled={busy}
              onClick={onConfirm}
            >
              {confirmLabel}
            </Button>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
