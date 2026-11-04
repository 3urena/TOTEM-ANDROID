# Security notes and known boundaries

## WebView boundary

- Main-frame HTTP navigation is accepted only for HTTPS hosts exactly matching `beta.totemai.xyz`, `totemai.xyz` or `www.totemai.xyz`.
- Untrusted main-frame HTTP(S) destinations are opened externally; external subframes and unknown schemes are blocked.
- The JavaScript message listener is registered only for `https://beta.totemai.xyz`. Messages are checked again for exact scheme/host/port and main-frame status.
- No `addJavascriptInterface` bridge is used. Web message listener support is checked before registration; on older WebView implementations where it is unavailable, native session mirroring is disabled rather than replaced with an origin-unsafe bridge.
- JavaScript and DOM storage remain enabled because the hosted application requires them. Automatic JavaScript pop-ups, multiple windows, geolocation, file-system access and mixed content are disabled.
- `allowContentAccess` remains enabled only to support content URIs returned by the system file picker. Direct file access remains disabled.
- Third-party cookies are disabled. If the hosted wallet provider proves to require them, review the exact provider domains and implications before changing this policy.
- TLS certificate errors are cancelled; there is no user-facing proceed-anyway path.

## Session handling

- Session values are mirrored using AndroidX `EncryptedSharedPreferences` backed by an AES-256-GCM Android Keystore key. The encrypted preference key scheme is AES-256-SIV.
- When encrypted storage cannot be created or accessed, the native mirror stays in memory only; the app does not write a plaintext SharedPreferences fallback or log token values.
- The hosted frontend's existing authentication design also keeps its token in WebView `localStorage`. This Android project mirrors that value into encrypted native preferences but does not encrypt or remove the WebView's own localStorage copy. Therefore, do not interpret the native mirror as proof that every on-device token copy is encrypted at rest.
- The bridge accepts only bounded JSON messages and rejects blank/oversized tokens.
- The app does not handle seed phrases or private keys. Wallet signing remains in the wallet application.
- `allowBackup=false` and `fullBackupContent=false` are set in the manifest.

## Limits and items requiring live verification

- Mirroring the hosted localStorage token is a compatibility mechanism, not a substitute for server-side session revocation or a native authentication API.
- The `onPageStarted` restoration path and post-login capture behavior must be tested against the live host's current frontend lifecycle.
- Clearing localStorage and native storage does not explicitly revoke a server session or clear the entire WebView cookie jar. The host's logout endpoint/flow should be confirmed before production distribution.
- `EncryptedSharedPreferences` 1.1.0-alpha06 is an alpha dependency retained from the supplied code. Review migration options before a long-lived production release.
- Debug APKs are debuggable and are not signed with a production release key. Do not distribute them as production releases.
