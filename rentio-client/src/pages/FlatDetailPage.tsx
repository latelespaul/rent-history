import { useParams } from "react-router-dom"

export default function FlatDetailPage() {
  const { id } = useParams<{ id: string }>()
  return <div className="container mx-auto p-6">Flat #{id} — coming in Step 8</div>
}
