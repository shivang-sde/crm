import { useRouter } from "next/navigation";
import { toast } from "sonner";
import type { CallOpeningInstruction } from "@/types/call-opening";

export type AppRouter = ReturnType<typeof useRouter>;

export async function handleCallOpeningInstruction(
  router: AppRouter,
  instruction: CallOpeningInstruction
): Promise<boolean> {
  if (!instruction) {
    return false;
  }

  const actionType = instruction.actionType || "NO_ACTION";
  const route = instruction.route || undefined;

  try {
    switch (actionType) {
      case "OPEN_PAGE":
        return openPage(router, instruction, route);
      case "OPEN_CALL_LAYOUT":
        return openCallLayout(router, instruction, route);
      case "OPEN_MODAL":
        return openDialog(instruction);
      case "OPEN_SIDEBAR":
        return openSheet(instruction);
      case "NO_ACTION":
        return true;
      default:
        console.warn("Unknown call opening action type", actionType, instruction);
        toast("Received unsupported call action. Please check your call panel.");
        return true;
    }
  } catch (error) {
    console.error("Failed to handle opening instruction", error, instruction);
    toast.error("Could not handle incoming call instruction.");
    return false;
  }
}

function getEntityRoute(entityType?: string | null, entityId?: string | null): string | undefined {
  if (!entityType || !entityId) {
    return undefined;
  }

  const lowerType = entityType.toLowerCase();

  switch (lowerType) {
    case "lead":
      return `/leads/${entityId}`;
    case "contact":
      return `/contacts/${entityId}`;
    case "account":
      return `/accounts/${entityId}`;
    case "deal":
      return `/deals/${entityId}`;
    default:
      return undefined;
  }
}

function openPage(router: AppRouter, instruction: CallOpeningInstruction, route?: string | null): boolean {
  const normalizedRoute = route ?? undefined;
  const destination = normalizedRoute ?? getEntityRoute(instruction.entityType, instruction.entityId) ?? undefined;
  if (destination) {
    router.push(destination);
    return true;
  }
  return openUnknownCallerFallback(instruction);
}

function openCallLayout(router: AppRouter, instruction: CallOpeningInstruction, route?: string | null): boolean {
  const normalizedRoute = route ?? undefined;
  const destination = normalizedRoute ?? getEntityRoute(instruction.entityType, instruction.entityId) ?? undefined;
  if (destination) {
    router.push(destination);
    return true;
  }
  return openUnknownCallerFallback(instruction);
}

function openDialog(instruction: CallOpeningInstruction): boolean {
  toast(
    `Incoming call: ${instruction.title || "call"}. Open the call panel or check the agent console.`
  );
  return true;
}

function openSheet(instruction: CallOpeningInstruction): boolean {
  toast(
    `Incoming call: ${instruction.title || "call"}. Please open your sidebar or call workspace.`
  );
  return true;
}

function openUnknownCallerFallback(instruction: CallOpeningInstruction): boolean {
  const callerNumber = instruction.metadata?.callerNumber || instruction.metadata?.phone || "Unknown";

  toast.error(
    `Incoming call from unknown caller ${callerNumber}. ${instruction.reason || ""}`
  );
  console.info("Unknown caller instruction", {
    externalCallId: instruction.externalCallId,
    reason: instruction.reason,
    metadata: instruction.metadata,
  });
  return true;
}
