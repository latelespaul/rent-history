import { Controller, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { createReviewSchema, type CreateReviewFormValues } from "../schemas"
import { useCreateReview } from "../hooks/useCreateReview"
import { RatingInput } from "./RatingInput"

interface Props {
  flatId: number
  onSuccess?: () => void
}

export function ReviewForm({ flatId, onSuccess }: Props) {
  const createReview = useCreateReview(flatId)

  const {
    register,
    handleSubmit,
    control,
    reset,
    formState: { errors },
  } = useForm<CreateReviewFormValues>({
    resolver: zodResolver(createReviewSchema),
    defaultValues: { title: "", content: "", rating: undefined as unknown as number },
  })

  const onSubmit = (values: CreateReviewFormValues) => {
    createReview.mutate(values, {
      onSuccess: () => {
        reset()
        onSuccess?.()
      },
    })
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
      <div className="space-y-2">
        <Label>Your rating</Label>
        <Controller
          control={control}
          name="rating"
          render={({ field }) => (
            <RatingInput
              value={field.value}
              onChange={field.onChange}
              disabled={createReview.isPending}
            />
          )}
        />
        {errors.rating && <p className="text-sm text-destructive">{errors.rating.message}</p>}
      </div>

      <div className="space-y-2">
        <Label htmlFor="title">Title</Label>
        <Input
          id="title"
          placeholder="Summarise your experience"
          {...register("title")}
          disabled={createReview.isPending}
        />
        {errors.title && <p className="text-sm text-destructive">{errors.title.message}</p>}
      </div>

      <div className="space-y-2">
        <Label htmlFor="content">Review</Label>
        <Textarea
          id="content"
          rows={5}
          placeholder="What did you like or dislike? Rent, maintenance, landlord, area…"
          {...register("content")}
          disabled={createReview.isPending}
        />
        {errors.content && <p className="text-sm text-destructive">{errors.content.message}</p>}
      </div>

      <Button type="submit" disabled={createReview.isPending}>
        {createReview.isPending ? "Posting…" : "Post review"}
      </Button>
    </form>
  )
}
