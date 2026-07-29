"use client";

import { useState } from "react";
import { Loader2, Phone } from "lucide-react";
import { Button } from "@/components/ui/button";
import { callOpeningApi } from "@/lib/api/call-opening";
import { handleCallOpeningInstruction } from "@/lib/call-opening/handleCallOpeningInstruction";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { usePermissions } from "@/lib/hooks/usePermissions";
import type { ClickToCallRequest } from "@/types/call-opening";

interface ClickToCallButtonProps {
  entityType: string;
  entityId: string;
  phoneNumber?: string | null;
  label?: string;
  size?: "sm" | "default" | "icon";
  variant?: "default" | "outline" | "secondary" | "ghost" | "destructive";
}

export function ClickToCallButton({
  entityType,
  entityId,
  phoneNumber,
  label = "Call",
  size = "sm",
  variant = "secondary",
}: ClickToCallButtonProps) {
  const router = useRouter();
  const { hasPermission } = usePermissions();
  const [isLoading, setIsLoading] = useState(false);

  const canCall = hasPermission("call", "write");

  const disabled = isLoading;
  const buttonLabel = isLoading ? "Calling…" : label;

  if (!canCall) {
    return null;
  }

  const handleClick = async () => {
    setIsLoading(true);

    const request: ClickToCallRequest = {
      entityType,
      entityId,
      phoneNumber,
    };

    try {
      const response =
        await callOpeningApi.clickToCall(request);

      let handled = false;

      if (response.instruction) {
        handled =
          await handleCallOpeningInstruction(
            router,
            response.instruction
          );
      }

      if (!handled && response.callId) {
        router.push(
          `/calls/active/${response.callId}`
        );
        handled = true;
      }

      if (!handled) {
        toast.error(
          "Call started, but the active workspace could not be opened."
        );
        return;
      }

      toast.success(
        response.message ?? "Call request sent."
      );
    } catch (error) {
      console.error("Click-to-call failed", error);
      toast.error(
        "Failed to initiate call. Please try again."
      );
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <Button
      variant={variant}
      size={size}
      onClick={handleClick}
      disabled={disabled}
      aria-label={`${label} ${entityType}`}
      className="inline-flex items-center gap-2"
    >
      {isLoading ? (
        <Loader2 className="h-4 w-4 animate-spin" />
      ) : (
        <Phone className="h-4 w-4" />
      )}
      {buttonLabel}
    </Button>
  );
}
