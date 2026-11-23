import { StrictMode } from "react"
import { createRoot } from "react-dom/client"
import HostRoot from "./HostRoot.tsx"
import "./index.css"

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <HostRoot />
  </StrictMode>,
)
