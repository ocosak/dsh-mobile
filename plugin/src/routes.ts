/**
 * Pairing + gated-channel route contract.
 *
 * This file is deliberately framework-free: dsh's host router surface
 * changes between preview releases, so the host adapter (index.ts)
 * mounts these handlers. The contract the Android app expects:
 *
 *   GET  /api/pair/status   -> { ok, paired, requirePairingForLan, phase, lanAvailable }
 *        (public, minimal payload only — no tokens, counts or tunnel URLs)
 *   POST /api/pair/accept   { token, deviceName } -> { grant } | 403
 *        (loopback-only in the reference design; the host adapter must
 *         enforce that, see index.ts)
 *   POST /api/pair/revoke   (x-dsh-remote-device) -> revoke that device
 *   POST /api/pair/revoke-all (loopback-only) -> revoke everything
 *   *    /remote/*           gated: requires a valid device credential
 *        in x-dsh-remote-device; on failure 403 { error: { code: "unpaired" } }.
 *        The gate consumes the header and must NOT forward it upstream.
 *
 * The /remote handler is a proxy concept, not a reimplementation of
 * dsh's session API: it forwards the caller's request (minus the
 * device header, host, origin/referer, sec-* and x-forwarded-* headers)
 * to the loopback dsh web server, using the process's own internal
 * credential — exactly the reference design's model. The session/chat
 * routes themselves are served by dsh; this plugin only gates them.
 */
import type { PairingStore } from './pairing.js';

export interface PairRequest {
  method: string;
  path: string;
  body?: Record<string, unknown>;
  headers: Record<string, string | undefined>;
  /** True only for a genuine loopback socket + loopback Host, no forwarded headers. */
  isLoopbackClient: boolean;
}

export interface PairResponse {
  status: number;
  body: unknown;
}

export function isProxiedRequest(headers: Record<string, string | undefined>): boolean {
  return Boolean(
    headers['x-forwarded-for'] ||
      headers['cf-connecting-ip'] ||
      headers['forwarded'] ||
      headers['x-real-ip'],
  );
}

/** Headers the /remote proxy must strip before re-issuing upstream. */
export const STRIPPED_PROXY_HEADERS = [
  'x-dsh-remote-device',
  'origin',
  'referer',
  'connection',
  'x-forwarded-for',
  'x-forwarded-host',
  'x-forwarded-proto',
  'cf-connecting-ip',
  'forwarded',
  'x-real-ip',
] as const;

export function handlePairStatus(store: PairingStore): PairResponse {
  return {
    status: 200,
    body: {
      ok: true,
      paired: store.pairingActive(),
      requirePairingForLan: true,
      phase: store.pairingActive() ? 'waiting' : 'idle',
      lanAvailable: null,
    },
  };
}

export function handlePairAccept(store: PairingStore, req: PairRequest): PairResponse {
  if (!req.isLoopbackClient) {
    // Pairing-token mint/accept control plane stays physically local
    // in the reference design. Remote callers get the guided block page.
    return { status: 403, body: { error: { code: 'loopback-only' } } };
  }
  const token = String(req.body?.token ?? '');
  const deviceName = String(req.body?.deviceName ?? '');
  const accepted = store.acceptPairing(token, deviceName);
  if (!accepted) {
    return { status: 403, body: { error: { code: 'unpaired' } } };
  }
  // Return the one-time grant; the app exchanges it for its device
  // credential through the landing flow. Never return the device id here.
  return { status: 200, body: { grant: accepted.grant } };
}

/** Gate check for /remote: returns the device, or null (caller sends 403). */
export function gateRemote(store: PairingStore, req: PairRequest) {
  const credential = req.headers['x-dsh-remote-device'];
  if (!credential) return null;
  return store.validateDevice(credential);
}
