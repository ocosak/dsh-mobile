# dsh-mobile-remote (plugin skeleton, v1)

Native-mobile companion plugin for DeepSeek Harness (dsh). It provides
the host side of the pairing + gated-channel contract that the
DSH Mobile Android app in `../android/` talks to.

## Design reference (not copied code)

Security model follows
https://github.com/zhu1090093659/dsh-web — `dsh-remote-web-ui`
(`README.zh.md`, dev branch):

- One active pairing token at a time; refreshing invalidates the old one.
- Pairing token is time-limited; each device gets its own session.
- Device sessions persist to a 0600 JSON file, written atomically.
- All traffic from a remote device goes through a gated `/remote`
  channel; the device credential is sent as `x-dsh-remote-device`
  and is consumed by the gate, never forwarded upstream.
- A paired device is a **full-control credential** — it can drive
  the host agent. Only pair devices you control; revocation takes
  effect on the next request.

Differences from that reference plugin, deliberately:

- This plugin does **not** serve the official Web GUI, does **not**
  inject a portrait touch layer, and does **not** run a Cloudflare
  tunnel or a fixed relay. The Android app is native (option B).
- Cloudflare / fixed-relay tunnelling is out of scope for v1. If you
  want one-click tunnelling, use `@linxin666/dsh-remote-web-ui`
  instead, or run a manual tunnel you control (see root README).

## Layout

- `package.json` — declares `dsh.bundle` (`./cordis.patch.yml`) and
  `dsh.plugin` metadata, as dsh plugins do.
- `cordis.patch.yml` — single insert row for this plugin.
- `src/index.ts` — exports `name`, `inject`, `apply(ctx, config)`;
  registers a settings card and two tools (mint token, revoke all);
  returns a disposer.
- `src/pairing.ts` — framework-free pairing-token + device logic.
- `src/devices.ts` — atomic 0600 device-file persistence.
- `src/routes.ts` — pairing/gate route contract + stripped headers.
- `src/settings.ts` — settings schema (tunnel tokens write-only).

## Install on a dsh host (verify first)

dsh is in developer preview and its plugin surface can break
between releases. Do not install this skeleton on a production host
until you have verified the adapter:

```bash
# 1. Check the composed plugin tree without booting:
dsh --profile web --dump-config

# 2. Install (after verifying the cordis patch shape for your dsh version):
dsh plugin --profile web add dsh-mobile-remote
# ... or, from a local checkout during development:
# dsh plugin --profile web add link:$(pwd)

# 3. Restart the profile:
dsh web
```

Then open the host's Remote access / Mobile remote settings card,
mint a pairing token, and paste it into the Android app's Pair screen.

## What is NOT verified yet (adapter-needed)

- The exact host router/settings service names in `index.ts`.
- The exact `/remote` proxy hook and internal-credential exchange
  on the host (the reference design uses the host's own browser
  auth exchange; this skeleton documents the contract, it does not
  reimplement dsh's session API).
- The Android app's `/remote/api/sessions|.../chat` paths are
  adapter placeholders — map them in `android/.../ApiEndpoints.kt`
  to the host's real routes, without touching UI code.
