# Device test matrix

Run on at least one Android 7–9 device/emulator and one recent Android release. Wallet behavior also depends on the wallet app and its installed version.

1. Install the debug APK, launch it, confirm the splash screen animates and the app does not crash.
2. Confirm Home shows network and signed-in state; turn on Airplane mode and check the offline indicator.
3. Open Workspace. Confirm `https://beta.totemai.xyz/` loads and the sign-in flow works.
4. Sign in with a test account. Confirm native Profile reflects signed-in state and (where Keystore permits) encrypted storage.
5. Force-stop the app, relaunch, and verify the hosted session and native signed-in indicator.
6. Open Wallet. Confirm `/settings/mobile-connections` loads and a wallet link opens the intended installed wallet or a safe browser fallback.
7. Send a non-sensitive test message and attach a harmless test file through the system picker.
8. Verify the bottom tabs switch between Home, Workspace, Wallet and Profile without creating duplicate WebViews or losing the hosted session.
9. In Workspace, navigate to a second hosted page and use Android Back; verify it walks WebView history. On other tabs, Back should return to the previous navigation destination and eventually close the activity.
10. Sign out through Profile. Confirm native session state clears and the hosted login page appears; force-stop and relaunch to confirm signed-out state.
11. Disable network, reload Workspace, and verify the error/retry UI and offline banner. Restore network and retry.
12. Rotate the device on every tab and verify navigation, WebView state, keyboard resizing and file chooser behavior.
13. Test the deep links:

```bash
adb shell am start -a android.intent.action.VIEW -d "totemai://wallet"
adb shell am start -a android.intent.action.VIEW -d "totemai://workspace"
```

14. Test a link to an untrusted HTTPS host and confirm it opens outside the app rather than inside the WebView.
15. Confirm there is no WebView TLS-error bypass, third-party cookie dependency, geolocation prompt or permission request for storage/camera/microphone.

Do not use a real wallet with meaningful funds for initial tests. Use a test wallet and test account first.
