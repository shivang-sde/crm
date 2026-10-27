/**
 * VOICE-03 public popup contracts. SIP.js-independent.
 */
export type {
  PopupCommandType,
  PopupEventType,
  PopupCommandPayloads,
  SoftphoneCommandMessage,
  SoftphoneMessage,
  SoftphoneSnapshot,
  SoftphonePresence,
  SoftphoneLinkState,
  CommandResultPayload,
  VoiceForwardPayload,
  ReadyPayload,
  SoftphoneErrorCode,
} from './protocol';
export {
  SOFTPHONE_CHANNEL,
  SOFTPHONE_PROTOCOL_VERSION,
  SOFTPHONE_WINDOW_NAME,
  SOFTPHONE_ROUTE,
  SOFTPHONE_POPUP_URL,
  SOFTPHONE_COMMAND_TIMEOUT_MS,
  SOFTPHONE_HANDSHAKE_TIMEOUT_MS,
  SOFTPHONE_PRESENCE_KEY,
  SOFTPHONE_HEARTBEAT_INTERVAL_MS,
  SOFTPHONE_PRESENCE_TTL_MS,
  SUPPORTED_POPUP_COMMANDS,
  SoftphoneError,
  isSoftphoneMessage,
  isSupportedProtocolVersion,
  isPreReadyCommand,
  isWrongVersionMessage,
  readSoftphonePresence,
  softphoneTabId,
  softphoneOrigin,
  isTrustedSoftphoneEvent,
  toVoiceCommand,
} from './protocol';
export type {
  SoftphoneControllerOptions,
  SoftphoneEventListener,
  SoftphoneSnapshotListener,
} from './controller';
export { SoftphoneController, openSoftphonePopup } from './controller';
export type { HostUiListener } from './host';
export { SoftphoneHost } from './host';
export { useSoftphoneController } from './use-softphone';
