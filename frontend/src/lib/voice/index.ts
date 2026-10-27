/**
 * VOICE-01 public contracts barrel.
 *
 * React components and (future) popup code consume these — never SIP.js,
 * JsSIP, RTCPeerConnection, or MediaStream.
 */
export type {
  VoiceProviderKey,
  VoiceRegistrationState,
  VoiceRegistrationSnapshot,
  VoiceCallState,
  VoiceCallDirection,
  VoiceEntityType,
  VoiceCall,
  VoiceNetworkState,
  IceServerConfig,
  GenericSipConnectionConfig,
  GenericSipResolvedSecret,
  VoiceConnectOptions,
  VoiceCallMetadata,
  VoiceErrorCode,
} from './types';
export { GENERIC_SIP_PROVIDER_KEY, VoiceError } from './types';
export type {
  VoiceEventType,
  VoiceEventMap,
  VoiceEvent,
  VoiceEventListener,
} from './events';
export { createVoiceEvent } from './events';
export type {
  VoiceCommandType,
  VoiceCommandMap,
  VoiceCommand,
  AnyVoiceCommand,
} from './commands';
export { createVoiceCommand } from './commands';
export type { VoiceProviderAdapter } from './adapter';
export type { VoiceStateSnapshot } from './runtime';
export { VoiceRuntime } from './runtime';
export {
  buildSafeSipConfig,
  GENERIC_SIP_SECRET_CREDENTIAL_NAME,
  GENERIC_SIP_CONNECTOR_PROVIDER_KEY,
  resolveGenericSipConnectorId,
} from './sip-config';
export type { GenericSipConnectorConfigShape, GenericSipConnectorRef } from './sip-config';
export { GenericSipAdapter } from './sip/GenericSipAdapter';
