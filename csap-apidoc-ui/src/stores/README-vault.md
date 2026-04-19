# Vault — storage layout & threat model

This note documents the M6 vault implementation as shipped on
`feat/environment-auth-headers`. It is intended as a starting point for
the M8 docs PR; feel free to absorb / reword in the user-facing CORS &
security guide.

## Files

| File | Role |
|---|---|
| `vault.ts` | Public API used by `authStore`, `authResolver`, `AuthSchemesDrawer`. Delegates to a swappable driver. |
| `vaultDriver.ts` | `PlaintextDriver` + `EncryptedDriver` implementations + storage-layout helpers. |
| `vaultCrypto.ts` | Pure Web Crypto helpers (PBKDF2 / AES-GCM / base64). |
| `settingsStore.ts` | Persists vault mode, idle timeout, and try-it-out preferences. |
| `../contexts/VaultContext.tsx` | React state machine + idle auto-lock + export/import/reset. |
| `../components/SettingsDrawer/` | Settings UI (toggle, password ops, data ops). |
| `../components/MasterPasswordModal/` | Set / change / unlock prompts. |
| `../components/VaultLockBanner/` | Inline banner shown while the vault is locked. |

## Storage layout

All keys are namespaced `csap-apidoc:`.

### Plaintext mode (default for first-time users)

```
csap-apidoc:vault            { version, encrypted: false, items: { tok_x: "raw secret" } }
```

### Encrypted mode

```
csap-apidoc:vault.encrypted  { version, salt: <base64>, iterations,
                               items: { tok_x: { iv: <base64>, ct: <base64> } } }
```

The plaintext key is removed once migration succeeds. The encrypted file
**never** contains the master password or the derived key.

### Settings

```
csap-apidoc:settings         { version, vaultMode, vaultLockTimeoutMin,
                               tryItOutProxyUrl, tryItOutTimeoutMs, language }
```

## Crypto parameters (locked in §9 D-2 of the design doc)

- KDF: **PBKDF2-SHA256**, **200 000 iterations**, **16-byte random salt** per browser.
- Cipher: **AES-GCM 256**, **12-byte random IV** per item.
- The derived `CryptoKey` lives only inside `EncryptedDriver` and is
  forgotten on `lock()`. It is not extractable.

## Threat model — what this protects against

| Threat | Mitigated? | Notes |
|---|---|---|
| Casual screen-share / shoulder-surfing of localStorage values | Yes | Encrypted blobs are unintelligible without the password. |
| Stolen device with no master-password lock screen | Yes (best-effort) | Attacker still has localStorage access but cannot decrypt without the password. |
| Forensic memory dump while unlocked | **No** | The derived key is in JS heap; we cannot prevent this. Idle auto-lock minimises the window. |
| Malicious browser extension reading `window.crypto.subtle` calls | **No** | Extensions with broad permissions can intercept anything inside the page; out of scope. |
| Server-side compromise | N/A | There is no server. All data is local. |
| Brute-force of the encrypted file | Slow but possible | 200k PBKDF2 iters slows offline attacks; users should pick strong passwords. |

## What this does **not** do

- No password recovery. Forgetting the master password = the encrypted
  blob is irrecoverable. The "重置全部" button in Settings wipes
  everything as the only way out.
- No per-item access control. All items unlock together once the master
  password is entered.
- No telemetry. Nothing leaves the device.

## Migration semantics

- `enableEncryption(pwd)`: snapshot every plaintext item → encrypt with a
  fresh salt → write the encrypted file → wipe the plaintext file →
  flip `settings.vaultMode = 'encrypted'`. The whole flow is wrapped in
  try/catch; on error the plaintext file is left intact.
- `disableEncryption(pwd)`: verify password by decrypting → write each
  item back to the plaintext file → wipe the encrypted file →
  `vaultMode = 'plaintext'`.
- `changePassword(old, new)`: verify old → re-encrypt every item under a
  freshly-derived key (with a new salt). The old salt is rotated even if
  the password change is "trivial" so the on-disk ciphertexts are fully
  refreshed.
- `importConfig(json)`: dropped to plaintext mode by design — the export
  file does not contain the password, so we cannot rebuild encrypted
  blobs without prompting the user. They can re-enable encryption with a
  fresh password after import.

## Idle auto-lock

`VaultContext` listens to `mousemove` / `keydown` / `click` on `window`
with a 5-second debounce, and runs a 30-second `setInterval` that
locks if `Date.now() - lastActivity >= settings.vaultLockTimeoutMin * 60_000`.
Setting `vaultLockTimeoutMin = 0` disables auto-lock. The interval is
never started in `plaintext` or `encrypted-locked` states.

## Locked-state behaviour for consumers

- `vault.get(ref)` returns `null` while locked and dispatches a
  `csap-apidoc:vault-locked-access` `CustomEvent` on `window` so the
  banner can flash.
- `vault.put(...)` returns either the supplied `existingRef` (no-op) or
  a sentinel `vault:tok_locked_<n>` ref whose `get()` always returns
  `null`. UI should disable Save buttons while locked; this is just the
  safety net so accidental calls don't corrupt other data.
- `vault.remove(...)` works on the on-disk blobs even while locked
  (consumers may need to clean orphans during scheme deletion).
- `vault.retainOnly(...)` likewise GCs the encrypted blob list.
