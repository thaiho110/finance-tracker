import * as React from "react"
import { cn } from "cn"

const INPUT_CLASS = "input-base placeholder:text-text-muted focus-visible:focus-ring disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50 aria-invalid:error-ring"

function Input({ className, type, ...props }: React.ComponentProps<"input">) {
  return (
    <input
      type={type}
      data-slot="input"
      className={cn(INPUT_CLASS, className)}
      {...props}
    />
  )
}

export { Input }
