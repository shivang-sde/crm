export function apiUrl(path: string): string {
  const base = process.env.PLAYWRIGHT_API_BASE_URL || 'http://localhost:8080/api/v1';
  return `${base.replace(/\/$/, '')}/${path.replace(/^\//, '')}`;
}
