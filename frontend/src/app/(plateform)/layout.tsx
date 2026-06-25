"use client";

import AppLayout from "@/components/layout/AppLayout";

export default function TenantGroupLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <AppLayout>{children}</AppLayout>;
}
