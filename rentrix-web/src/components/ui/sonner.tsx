import { Toaster as Sonner, type ToasterProps } from "sonner"
import { AlertIcon, CheckIcon, ClockIcon, InfoIcon } from "@/components/ui/icons.gen"

const Toaster = ({ ...props }: ToasterProps) => {
  return (
    <Sonner
      theme="light"
      className="toaster group"
      icons={{
        success: <CheckIcon size={16} />,
        info: <InfoIcon size={16} />,
        warning: <AlertIcon size={16} />,
        error: <AlertIcon size={16} />,
        loading: <ClockIcon size={16} />,
      }}
      style={
        {
          "--normal-bg": "var(--popover)",
          "--normal-text": "var(--popover-foreground)",
          "--normal-border": "var(--border)",
          "--border-radius": "var(--radius)",
        } as React.CSSProperties
      }
      toastOptions={{
        classNames: {
          toast: "cn-toast",
        },
      }}
      {...props}
    />
  )
}

export { Toaster }
