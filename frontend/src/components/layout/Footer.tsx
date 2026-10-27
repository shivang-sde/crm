"use client";

export interface FooterProps {
  version?: string;
}

export function Footer({ version = "v1.0" }: FooterProps) {
  return (
    <footer className="border-t bg-white px-4 pb-[max(0.75rem,env(safe-area-inset-bottom))] pt-3 text-xs text-gray-500 md:px-6">
      <div className="mx-auto flex max-w-screen-2xl flex-col gap-1 md:flex-row md:justify-between">
        <span>© {new Date().getFullYear()} Sellspark CRM.</span>
        <span>Version {version}</span>
      </div>
    </footer>
  );
}
