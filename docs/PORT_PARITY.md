# Hosted platform parity

| Hosted behavior | Android shell handling |
| --- | --- |
| Wallet / SIWE authentication | Hosted page in the same-origin WebView; wallet scheme links are handed to installed wallet apps |
| Username / password login | Hosted `/login` page in WebView |
| Public sign-up | Hosted page flow, where available |
| Bearer token in localStorage | Mirrored to Android Keystore-backed encrypted preferences after a trusted-origin main-frame message |
| Session cookies | WebView cookie jar; flushed when the host is disposed |
| `/settings/mobile-connections` | Wallet tab loads this route |
| Model / credit / tool / payment APIs | Requests remain with the hosted TOTEM service; no local API/backend is introduced |
| File upload | Android system picker; WebView receives user-selected URI(s) |

## Native functionality

- Splash screen and TOTEM visual identity
- Native Home and Profile screens
- Navigation Compose routes and bottom navigation
- One shared WebView instance for Workspace and Wallet
- WebView history-aware Back behavior in Workspace
- Offline indicator, loading progress and retry panel
- Encrypted native session mirror
- Android deep links using the `totemai://` scheme

## Not fully native / not guaranteed

- Workspace conversation UI, account pages, model selection, credits, payments and wallet-connect screens are still provided by the hosted website. This is intentional so the hosted service remains authoritative.
- The Android shell cannot fix server-side authentication, upstream wallet-connect or payment bugs.
- Host-specific changes to routes, localStorage keys, CSP, authentication behavior or wallet SDKs may require a new app build.
- Popup-based flows are intentionally unsupported; the hosted in-page wallet modal is expected to be used instead.
- Google login is not presented as a supported flow because the supplied project notes say it is not offered by the host.

## Source assumptions to re-check against the live host

- Origin: `https://beta.totemai.xyz`
- Routes: `/`, `/login`, `/settings/mobile-connections`
- Session localStorage keys: `anythingllm_authToken`, `anythingllm_user`

These host details came from the supplied Venice AI project dump and have not been verified against a live authenticated account in this build environment.
