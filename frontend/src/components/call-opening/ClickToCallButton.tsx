"use client";

import { useEffect, useState } from "react";
import { Loader2, Phone } from "lucide-react";
import { Button } from "@/components/ui/button";
import { callOpeningApi } from "@/lib/api/call-opening";
import { handleCallOpeningInstruction } from "@/lib/call-opening/handleCallOpeningInstruction";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { usePermissions } from "@/lib/hooks/usePermissions";
import type { ClickToCallRequest, CallingProviderOption } from "@/types/call-opening";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

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
  const [providers, setProviders] = useState<CallingProviderOption[]>([]);
  const [providersLoading, setProvidersLoading] = useState(true);
  const [selectedProviderKey, setSelectedProviderKey] = useState<string>("");

  const canCall = hasPermission("call", "write");

  useEffect(() => {
    if (!canCall) return;
    let cancelled = false;
    const load = async () => {
      try {
        const list = await callOpeningApi.getCallingProviders();
        if (cancelled) return;
        setProviders(list);
        if (list.length === 1) setSelectedProviderKey(list[0].providerKey);
        else if (list.length > 1 && !selectedProviderKey) setSelectedProviderKey(list[0].providerKey);
      } catch {
        if (!cancelled) setProviders([]);
      } finally {
        if (!cancelled) setProvidersLoading(false);
      }
    };
    void load();
    return () => { cancelled = true; };
  }, [canCall]);

  const hasNoProvider = !providersLoading && providers.length === 0;
  const disabled = isLoading || providersLoading || hasNoProvider;
  const buttonLabel = isLoading ? "Calling…" : hasNoProvider ? "Calling unavailable" : label;

  if (!canCall) {
    return null;
  }

  if (hasNoProvider) {
    return (
      <div className="inline-flex items-center gap-2">
        <Button variant={variant} size={size} disabled aria-label={`${label} ${entityType}`} className="inline-flex items-center gap-2 opacity-50">
          <Phone className="h-4 w-4" />
          {buttonLabel}
        </Button>
        <span className="text-xs text-muted-foreground">Configure a calling provider to use Click to Call</span>
      </div>
    );
  }

  const handleClick = async () => {
    if (!selectedProviderKey && providers.length > 0) {
      toast.error("Select a calling provider");
      return;
    }
    setIsLoading(true);

    const request: ClickToCallRequest = {
      entityType,
      entityId,
      phoneNumber,
      providerKey: selectedProviderKey || undefined,
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
    } catch (error: unknown) {
      console.error("Click-to-call failed", error);
      const msg = (error as { response?: { data?: { error?: { message?: string }; message?: string } } })?.response?.data?.error?.message
        || (error as { message?: string })?.message
        || "Failed to initiate call. Please try again.";
      // Improve message for provider configuration states
      if (String(msg).includes("PROVIDER_NOT_CONFIGURED") || String(msg).includes("Configure a calling provider")) {
        toast.error("Configure a calling provider to use Click to Call");
      } else if (String(msg).includes("Multiple calling providers")) {
        toast.error("Multiple calling providers configured — select a provider");
      } else {
        toast.error(msg);
      }
    } finally {
      setIsLoading(false);
    }
  };

  if (providers.length > 1) {
    return (
      <div className="inline-flex items-center gap-2">
        <Select value={selectedProviderKey} onValueChange={setSelectedProviderKey}>
          <SelectTrigger className="w-[160px] h-8 text-xs">
            <SelectValue placeholder="Provider" />
          </SelectTrigger>
          <SelectContent>
            {providers.map((p) => (
              <SelectItem key={p.providerKey} value={p.providerKey}>{p.providerName}</SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Button
          variant={variant}
          size={size}
          onClick={handleClick}
          disabled={disabled || !selectedProviderKey}
          aria-label={`${label} ${entityType}`}
          className="inline-flex items-center gap-2"
        >
          {isLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Phone className="h-4 w-4" />}
          {buttonLabel}
        </Button>
      </div>
    );
  }

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
