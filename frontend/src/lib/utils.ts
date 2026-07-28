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

export function formatDuration(
  durationSeconds: number | null,
  durationMinutes: number | null
) {
  if (
    durationSeconds !== null &&
    durationSeconds !== undefined
  ) {
    if (durationSeconds <= 0) {
      return '0 seconds';
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
    return `${durationMinutes} minute${
      durationMinutes === 1 ? '' : 's'
    }`;
  }

  return 'Not available';
}