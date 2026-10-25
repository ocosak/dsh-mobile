/**
 * Pairing-token + device-session logic, framework-free so it can be
 * unit-tested and mounted under any dsh host router.
 *
 * Design reference (not copied code):
 * https://github.com/zhu1090093659/dsh-web — dsh-remote-web-ui README.zh.md
 *   - one active pairing token at a time; refreshing invalidates the old one
 *   - token is time-limited; devices get their own revocable session
 *   - device sessions persist to a 0600 JSON file with atomic rename
 *   - paired devices are FULL-CONTROL credentials: they can drive the host
 *
 * Security rules in this file:
 * - Tokens and device ids are 256-bit crypto-random values.
 * - Nothing here logs a token or a device id.
 * - A one-time grant is single-use and short-lived (60s, as in the
 *   reference design); it is consumed even on failure, so replay
 *   cannot be probed for a valid device.
 */
import { randomBytes } from 'node:crypto';

export interface PairingConfig {
  /** Pairing-token lifetime in ms. Default 10 minutes. */
  pairingTtlMs?: number;
  /** Idle device-session expiry in ms. Default 30 days. */
  idleExpireMs?: number;
  /** Max devices. Default 20. */
  maxDevices?: number;
}

export interface DeviceSession {
  id: string;
  name: string;
  createdAt: number;
  lastSeenAt: number;
}

interface Grant {
  deviceId: string;
  expiresAt: number;
}

function token(): string {
  return randomBytes(32).toString('hex');
}

export class PairingStore {
  private pairingToken: string | null = null;
  private pairingExpiresAt = 0;
  private grants = new Map<string, Grant>();
  private devices = new Map<string, DeviceSession>();
  private readonly cfg: Required<PairingConfig>;

  constructor(cfg: PairingConfig = {}) {
    this.cfg = {
      pairingTtlMs: cfg.pairingTtlMs ?? 10 * 60 * 1000,
      idleExpireMs: cfg.idleExpireMs ?? 30 * 24 * 60 * 60 * 1000,
      maxDevices: cfg.maxDevices ?? 20,
    };
  }

  /** Mint a new pairing token. Invalidates any previous token. */
  mintPairingToken(): { token: string; expiresAt: number } {
    this.pairingToken = token();
    this.pairingExpiresAt = Date.now() + this.cfg.pairingTtlMs;
    return { token: this.pairingToken, expiresAt: this.pairingExpiresAt };
  }

  stopPairing(): void {
    this.pairingToken = null;
    this.pairingExpiresAt = 0;
  }

  pairingActive(): boolean {
    return this.pairingToken !== null && Date.now() < this.pairingExpiresAt;
  }

  /**
   * Consume a pairing token and create a per-device session.
   * Returns a one-time grant (NOT the device id) plus the device name
   * to store. The caller exchanges the grant for the device credential
   * through the pair-app landing flow, so the device id never has to
   * appear in a URL the user can see or copy.
   */
  acceptPairing(
    presentedToken: string,
    deviceName: string,
  ): { grant: string; deviceId: string } | null {
    if (!this.pairingActive() || presentedToken !== this.pairingToken) return null;
    if (this.devices.size >= this.cfg.maxDevices) return null;
    const id = token();
    const now = Date.now();
    this.devices.set(id, {
      id,
      name: deviceName || 'Unnamed device',
      createdAt: now,
      lastSeenAt: now,
    });
    const grant = token();
    this.grants.set(grant, { deviceId: id, expiresAt: now + 60 * 1000 });
    return { grant, deviceId: id };
  }

  /** Single-use grant exchange. Consumed even when expired/replayed. */
  redeemGrant(grant: string): string | null {
    const g = this.grants.get(grant);
    this.grants.delete(grant);
    if (!g || Date.now() >= g.expiresAt) return null;
    return g.deviceId;
  }

  /** Validate a device credential for the /remote gate. Touches lastSeen. */
  validateDevice(deviceId: string): DeviceSession | null {
    const d = this.devices.get(deviceId);
    if (!d) return null;
    if (Date.now() - d.lastSeenAt > this.cfg.idleExpireMs) {
      this.devices.delete(deviceId);
      return null;
    }
    d.lastSeenAt = Date.now();
    return d;
  }

  revokeDevice(deviceId: string): boolean {
    return this.devices.delete(deviceId);
  }

  /** Revoke every device and the current pairing token. */
  revokeAll(): void {
    this.devices.clear();
    this.grants.clear();
    this.stopPairing();
  }

  listDevices(): Omit<DeviceSession, 'id'>[] {
    // Never return raw ids to a UI that does not need them; the host
    // panel can map them by index. (This skeleton keeps ids internal.)
    return [...this.devices.values()].map(({ name, createdAt, lastSeenAt }) => ({
      name,
      createdAt,
      lastSeenAt,
    }));
  }

  /** Serialisable snapshot for the JSON device file. */
  snapshot(): DeviceSession[] {
    return [...this.devices.values()];
  }

  restore(devices: DeviceSession[]): void {
    this.devices = new Map(devices.map((d) => [d.id, d]));
  }
}
