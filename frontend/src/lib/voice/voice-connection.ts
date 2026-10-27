/**
 * VOICE-05 — pure Generic SIP connection form model + validation.
 *
 * Framework-free so the rules can be exercised outside React.
 * Mirrors the backend validation in CallingAdminController
 * (Voice connections Generic SIP endpoints). Secrets are never logged,
 * cached, or embedded in messages here — only presence is tracked.
 */

export interface VoiceIceServerForm {
  urls: string;
  username: string;
  /** Blank means "keep the stored credential" when one is configured. */
  credential: string;
  credentialConfigured: boolean;
}

export interface VoiceConnectionForm {
  endpoint: string;
  sipDomain: string;
  username: string;
  /** Blank means "keep the stored secret" when one is configured. */
  secret: string;
  secretConfigured: boolean;
  iceServers: VoiceIceServerForm[];
}

export type VoiceConnectionErrors = Partial<
  Record<'endpoint' | 'sipDomain' | 'username' | 'secret' | 'iceServers', string>
>;

const WSS_URL_PATTERN = /^wss:\/\/[^\s/]+(:\d+)?(\/\S*)?$/;
const ICE_URL_PATTERN = /^(stun|turn|turns):\S+$/;

export function isValidWssUrl(value: string): boolean {
  const trimmed = value.trim();
  return trimmed.length > 0 && trimmed.length <= 500 && WSS_URL_PATTERN.test(trimmed);
}

export function isValidSipDomain(value: string): boolean {
  const trimmed = value.trim();
  return (
    trimmed.length > 0 &&
    trimmed.length <= 255 &&
    !trimmed.includes('://') &&
    !trimmed.includes(' ')
  );
}

export function isValidUsername(value: string): boolean {
  const trimmed = value.trim();
  return trimmed.length > 0 && trimmed.length <= 128;
}

export function isValidIceUrl(value: string): boolean {
  const trimmed = value.trim();
  return trimmed.length > 0 && trimmed.length <= 500 && ICE_URL_PATTERN.test(trimmed);
}

export function validateVoiceConnectionForm(form: VoiceConnectionForm): VoiceConnectionErrors {
  const errors: VoiceConnectionErrors = {};

  if (!isValidWssUrl(form.endpoint)) {
    errors.endpoint = 'WSS URL must start with wss:// and include a host.';
  }
  if (!isValidSipDomain(form.sipDomain)) {
    errors.sipDomain = 'SIP domain must be a plain host value.';
  }
  if (!isValidUsername(form.username)) {
    errors.username = 'Username / extension is required.';
  }
  if (!form.secretConfigured && form.secret.trim().length === 0) {
    errors.secret = 'Secret is required when none is configured.';
  }

  const seen = new Set<string>();
  for (const server of form.iceServers) {
    const urls = server.urls.trim();
    if (!isValidIceUrl(urls)) {
      errors.iceServers = 'Each ICE server URL must start with stun:, turn: or turns:.';
      break;
    }
    if (seen.has(urls.toLowerCase())) {
      errors.iceServers = 'Duplicate ICE server URL.';
      break;
    }
    seen.add(urls.toLowerCase());
    if (server.username.trim().length > 128) {
      errors.iceServers = 'ICE username is too long.';
      break;
    }
    if (server.credential.length > 512) {
      errors.iceServers = 'ICE credential is too long.';
      break;
    }
  }
  if (form.iceServers.length > 10) {
    errors.iceServers = 'Too many ICE servers (maximum 10).';
  }

  return errors;
}

export function emptyVoiceConnectionForm(): VoiceConnectionForm {
  return {
    endpoint: '',
    sipDomain: '',
    username: '',
    secret: '',
    secretConfigured: false,
    iceServers: [],
  };
}

export function emptyIceServerForm(): VoiceIceServerForm {
  return { urls: '', username: '', credential: '', credentialConfigured: false };
}
