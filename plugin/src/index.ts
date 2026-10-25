/**
 * dsh-mobile-remote — dsh plugin skeleton (v1).
 *
 * Minimal Cordis shape, following the official first-plugin pattern:
 * a module exporting `name`, an `inject` list of hard dependencies,
 * and `apply(ctx, config)`. Everything contributed must hand back a
 * disposer so unloading the plugin really stops it.
 *
 * TODO (adapter-needed): the exact ctx service names (router, settings,
 * slots, tools) and the /remote proxy hook must be verified against
 * the dsh version on the host — dsh is in developer preview and this
 * surface can break. This skeleton therefore keeps the security logic
 * (pairing.ts, devices.ts, routes.ts) framework-free and wires it in
 * `apply` through a small adapter surface. Do NOT install this on a
 * production host until the adapter has been verified with
 * `dsh --profile web --dump-config` and a local pairing round-trip.
 *
 * LAN-forward note: this plugin does NOT rebind the dsh web server.
 * LAN reachability is a runtime forwarder (same model as the reference
 * dsh-remote-web-ui plugin): forward LAN:port -> 127.0.0.1:port, or
 * start `dsh web --host <lan-address>`. The gated /remote channel is
 * the only remote path; bare /api from a remote host stays fenced by
 * dsh's own Host rules.
 */

import { PairingStore } from './pairing.js';

// Minimal local Context shape — intentionally `unknown`-tolerant so this
// skeleton typechecks without a dsh checkout. The adapter below only
// touches services if the host actually provides them.
export interface DshContext {
  settings?: {
    register: (def: unknown) => () => void;
  };
  tools?: {
    register: (tool: unknown) => () => void;
  };
  slots?: {
    inject: (slot: string, render: unknown) => () => void;
  };
  router?: {
    get: (path: string, handler: unknown) => () => void;
    post: (path: string, handler: unknown) => () => void;
    use: (path: string, handler: unknown) => () => void;
  };
  on?: (event: string, handler: () => void) => () => void;
}

export const name = 'dsh-mobile-remote';
// Hard dependencies: none in v1 beyond the host itself. If the host
// adapter needs a router/settings service, add it here and the loader
// will wait for it instead of silently starting half-wired.
export const inject: string[] = [];

export function apply(ctx: DshContext, config?: Record<string, unknown>) {
  const disposers: Array<() => void> = [];
  const store = new PairingStore({
    pairingTtlMs: typeof config?.pairingTtlMs === 'number' ? config.pairingTtlMs : undefined,
    idleExpireMs: typeof config?.idleExpireMs === 'number' ? config.idleExpireMs : undefined,
  });

  // Settings card (host UI). Write-only secrets are never read back.
  if (ctx.settings) {
    disposers.push(
      ctx.settings.register({
        id: 'dsh-mobile-remote',
        title: 'Mobile remote access',
        schema: {
          pairingTtlMs: { type: 'number', default: 10 * 60 * 1000 },
          idleExpireMs: { type: 'number', default: 30 * 24 * 60 * 60 * 1000 },
          devicesFile: { type: 'string', default: '' },
          requirePairingForLan: { type: 'boolean', default: true },
          publicBaseUrl: { type: 'string', default: '' },
          tunnelMode: { type: 'string', default: 'off', enum: ['off', 'manual', 'cloudflare'] },
        },
        note: 'Cloudflare / fixed-relay tunnelling is out of scope for v1. Use manual tunnel or @linxin666/dsh-remote-web-ui for one-click tunnelling.',
      }),
    );
  }

  // Tool: mint a fresh pairing token (host-side, loopback-only in use).
  // The host panel calls this when the user opens Remote access.
  if (ctx.tools) {
    disposers.push(
      ctx.tools.register({
        name: 'dsh-mobile-remote.mint-pairing-token',
        description: 'Mint a time-limited pairing token for the DSH Mobile app. Invalidates the previous token.',
        execute: () => store.mintPairingToken(),
      }),
    );
    disposers.push(
      ctx.tools.register({
        name: 'dsh-mobile-remote.revoke-all-devices',
        description: 'Revoke all paired mobile devices and the current pairing token.',
        execute: () => {
          store.revokeAll();
          return { revoked: true };
        },
      }),
    );
  }

  // Router mounting is adapter-only: the real host router API is
  // verified at install time (see README). The handlers live in
  // routes.ts and are intentionally not bound to a framework here.
  void ctx.router;
  void store;

  return () => {
    for (const dispose of disposers.reverse()) {
      try {
        dispose();
      } catch {
        // Unload must not throw.
      }
    }
    store.revokeAll();
  };
}
