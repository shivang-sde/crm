"use client";

import React from "react";
import { NoAccessNotice } from "@/components/shared/NoAccessNotice";

/**
 * Deterministic safe state for authenticated users whose permission map
 * grants no CRM area. Reachable, renderable, and never redirects — this is
 * what breaks any potential redirect loop.
 */
export default function NoAccessPage() {
  return <NoAccessNotice />;
}
