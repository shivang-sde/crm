"use client";

interface SettingsSectionProps {
  title: string;
}

export function SettingsSection({ title }: SettingsSectionProps) {
  return (
    <div className="px-3 py-1.5 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
      {title}
    </div>
  );
}