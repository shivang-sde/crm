import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export  function toIsoString(value?: string): string | undefined {
  if (!value) {
    return undefined;
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return undefined;
  }

  return date.toISOString();
}

export  function emptyToUndefined(value?: string): string | undefined {
  const normalized = value?.trim();
  return normalized ? normalized : undefined;
}


export function toInstant(value: string): string {
  return new Date(value).toISOString();
}


export function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return 'Not available';
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return 'Not available';
  }

  return new Intl.DateTimeFormat('en-IN', {
    dateStyle: 'medium',
    timeStyle: 'medium',
  }).format(date);
}


export function formatLabel(value?: string | null) {
  if (!value) {
    return 'Not available';
  }

  return value
    .toLowerCase()
    .split('_')
    .map(
      (part) =>
        part.charAt(0).toUpperCase() +
        part.slice(1)
    )
    .join(' ');
}


export function formatDuration(
  durationSeconds?: number | null,
  durationMinutes?: number | null
) {
  if (
    durationSeconds !== null &&
    durationSeconds !== undefined
  ) {
    if (durationSeconds <= 0) {
      return '0 sec';
    }

    const hours = Math.floor(durationSeconds / 3600);
    const minutes = Math.floor(
      (durationSeconds % 3600) / 60
    );
    const seconds = durationSeconds % 60;

    const parts: string[] = [];

    if (hours > 0) {
      parts.push(`${hours}h`);
    }

    if (minutes > 0) {
      parts.push(`${minutes}m`);
    }

    if (seconds > 0 || parts.length === 0) {
      parts.push(`${seconds}s`);
    }

    return parts.join(' ');
  }

  if (
    durationMinutes !== null &&
    durationMinutes !== undefined
  ) {
    return `${durationMinutes} min`;
  }

  return '—';
}

export function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

export function csvExportFileName(kind: "summary" | "trends" | string, to?: string): string {
  const date = to ? to.slice(0, 10) : new Date().toISOString().slice(0, 10);
  return `analytics-${kind}-${date}.csv`;
}
