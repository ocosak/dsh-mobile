/**
 * Atomic, 0600 device-session persistence.
 *
 * Mirrors the reference design: devices live in a JSON file under
 * $DSH_HOME (default: dsh-mobile-remote-devices.json), written via
 * temp file + atomic rename. If the file cannot be written, the
 * caller should fail closed — never leave a revoked session on disk
 * that could be restored on next start.
 */
import { chmod, rename, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import type { DeviceSession } from './pairing.js';

export async function saveDevicesAtomic(
  devicesFile: string,
  devices: DeviceSession[],
): Promise<void> {
  const tmp = join(tmpdir(), `dsh-mobile-devices-${process.pid}.tmp`);
  await writeFile(tmp, JSON.stringify(devices, null, 2), { mode: 0o600 });
  try {
    await chmod(tmp, 0o600);
  } catch {
    // Some filesystems ignore chmod; the rename still applies.
  }
  await rename(tmp, devicesFile);
}

export function defaultDevicesFile(dshHome: string | undefined): string {
  return join(dshHome ?? process.cwd(), 'dsh-mobile-remote-devices.json');
}
