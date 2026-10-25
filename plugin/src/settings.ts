/**
 * Settings schema for the plugin.
 *
 * This is a plain, documented schema — the host adapter registers it
 * through ctx.settings (see index.ts) so the host UI renders the card.
 * No secret is ever rendered back: tunnel tokens are write-only.
 */
export interface MobileRemoteSettings {
  /** Pairing-token lifetime in ms (default 600000). */
  pairingTtlMs: number;
  /** Idle device expiry in ms (default 30 days). */
  idleExpireMs: number;
  /** Absolute path to the devices JSON file (default $DSH_HOME/...). */
  devicesFile: string;
  /** Require pairing for LAN callers of /remote (always true in v1). */
  requirePairingForLan: boolean;
  /** Public base URL advertised in pairing links, if any. */
  publicBaseUrl: string;
  /**
   * Tunnel mode. v1 supports "off" and "manual" only:
   * - manual: operator runs their own tunnel (e.g. cloudflared --url)
   *   and sets publicBaseUrl to its URL.
   * - cloudflare / fixed-relay: OUT OF SCOPE for v1. Users who want
   *   the reference plugin's one-click tunnel should use
   *   @linxin666/dsh-remote-web-ui instead (see root README).
   */
  tunnelMode: 'off' | 'manual' | 'cloudflare';
  /** LAN forwarding note shown in the host card (informational). */
  lanForwardNote: string;
}

export const defaultSettings: MobileRemoteSettings = {
  pairingTtlMs: 10 * 60 * 1000,
  idleExpireMs: 30 * 24 * 60 * 60 * 1000,
  devicesFile: '',
  requirePairingForLan: true,
  publicBaseUrl: '',
  tunnelMode: 'off',
  lanForwardNote:
    'LAN access: keep the dsh web server on loopback and use a runtime LAN forwarder, or start dsh web with --host <lan-address>. Never use --host 0.0.0.0 without pairing + revocation.',
};
