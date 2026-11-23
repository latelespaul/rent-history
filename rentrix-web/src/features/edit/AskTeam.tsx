import { useState } from "react"
import { useMutation } from "@tanstack/react-query"
import { createChangeRequest } from "@/api/homes"
import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { Notice } from "@/components/ui/notice"
import { errorMessage } from "@/components/ui/states"
import { Textarea } from "@/components/ui/textarea"
import type { ChangeTarget } from "./blocked"

/** Longest note the API accepts (`ChangeRequest.note`). */
const NOTE_MAX = 1000

/**
 * The reason a rule gave, then a small note that files a request in the admin queue. No form of its
 * own: it shows inside the edit pages' forms.
 */
export function AskTeam({ error, target }: { error: unknown; target: ChangeTarget }) {
  const [open, setOpen] = useState(false)
  const [note, setNote] = useState("")
  const send = useMutation({
    mutationFn: () => createChangeRequest({ ...target, note: note.trim() }),
  })
  const fieldId = `ask-team-${target.targetId}`
  return (
    <Notice tone="warn" data-testid="ask-team">
      <p>{errorMessage(error)}</p>
      {send.isSuccess ? (
        <p className="mt-2 font-semibold" data-testid="ask-team-sent">
          Request sent. Our team will look at it and get back to you.
        </p>
      ) : open ? (
        <div className="mt-2 space-y-2">
          <Label htmlFor={fieldId}>What should our team change?</Label>
          <Textarea
            id={fieldId}
            value={note}
            maxLength={NOTE_MAX}
            onChange={(e) => setNote(e.target.value)}
          />
          {send.isError && <p className="text-sm text-danger-600">{errorMessage(send.error)}</p>}
          <div className="flex gap-2">
            <Button
              type="button"
              size="sm"
              disabled={send.isPending || !note.trim()}
              data-testid="ask-team-send"
              onClick={() => send.mutate()}
            >
              Send request
            </Button>
            <Button type="button" size="sm" variant="ghost" onClick={() => setOpen(false)}>
              Cancel
            </Button>
          </div>
        </div>
      ) : (
        <Button
          type="button"
          size="sm"
          variant="outline"
          className="mt-2"
          data-testid="ask-team-open"
          onClick={() => setOpen(true)}
        >
          Ask our team to change this
        </Button>
      )}
    </Notice>
  )
}
