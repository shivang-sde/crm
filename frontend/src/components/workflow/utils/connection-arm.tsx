"use client";

import React, { createContext, useContext } from "react";

export type ArmedHandle = { nodeId: string; handleId: string | null; handleType: "source" | "target" } | null;

interface ArmContextValue {
  armed: ArmedHandle;
  arm: (handle: { nodeId: string; handleId: string | null; handleType: "source" | "target" }) => void;
  clear: () => void;
  activateTarget: (nodeId: string, handleId: string | null) => void;
  readOnly?: boolean;
}

const ConnectionArmContext = createContext<ArmContextValue | null>(null);

export function useConnectionArm() {
  return useContext(ConnectionArmContext);
}

export function ConnectionArmProvider({
  children,
  value,
}: {
  children: React.ReactNode;
  value: ArmContextValue;
}) {
  return <ConnectionArmContext.Provider value={value}>{children}</ConnectionArmContext.Provider>;
}
