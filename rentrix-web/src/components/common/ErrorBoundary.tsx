import { Component, type ErrorInfo, type ReactNode } from "react"
import { Button } from "@/components/ui/button"
import { ErrorState } from "@/components/ui/states"

interface Props {
  children: ReactNode
  fallback?: ReactNode
}

interface State {
  hasError: boolean
  error?: Error
}

export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("ErrorBoundary caught:", error, info)
    // Hook a real logger here later (Sentry, etc.)
  }

  handleReset = () => {
    this.setState({ hasError: false, error: undefined })
  }

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) return this.props.fallback
      return (
        <main className="mx-auto max-w-page px-4 py-16">
          <ErrorState
            error={this.state.error}
            onRetry={this.handleReset}
            action={<Button onClick={() => (window.location.href = "/")}>Go home</Button>}
          />
        </main>
      )
    }
    return this.props.children
  }
}
