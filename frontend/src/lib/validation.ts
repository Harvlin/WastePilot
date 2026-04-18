export const APP_TIMEZONES = [
  "UTC",
  "Asia/Jakarta",
  "Asia/Singapore",
  "Asia/Bangkok",
  "Asia/Tokyo",
  "Europe/London",
  "America/New_York",
  "America/Los_Angeles",
] as const;

const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function isValidEmail(email: string): boolean {
  return emailRegex.test(email.trim());
}

export function isAllowedTimezone(timezone: string): boolean {
  return APP_TIMEZONES.includes(timezone as (typeof APP_TIMEZONES)[number]);
}

export function clampNumber(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}
