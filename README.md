# TOTEM MOBILE — Android

A native Android application built with Kotlin and Jetpack Compose. Home, native sign-in, chat, chat history, credits/wallet and profile are rendered by the app itself. The app calls the official hosted TOTEM API at **https://beta.totemai.xyz** for authentication, workspaces, chat streaming, account credits and wallet balance. **No website pages are embedded in the app.**

- Application ID: \`xyz.totemai.app\`
- Version: \`2.1\` (\`versionCode 3\`)
- Minimum Android: 7.0 / API 24
- Target / compile SDK: 34
- Java: 17
- Gradle: 8.7
- Android Gradle Plugin: 8.3.2
- Kotlin: 1.9.24

## Build an APK with GitHub Actions

1. Open the repository's **Actions** tab and select **Build TOTEM AI Android APK**.
2. Open the latest successful run on branch \`feature/native-chat-wallet-ui\`.
3. Download the \`TOTEM-APK\` artifact and extract \`app-debug.apk\`.
4. Install the APK on Android.

The workflow installs JDK 17, Android SDK Platform 34 / Build Tools 34.0.0 and Gradle 8.7, then runs \`:app:assembleDebug\`. The APK is a debug build and is not Play Store signed.

**Important for debug builds:** GitHub-hosted runners generate a temporary debug signing key for each run. Android may reject an update if the installed APK was signed by a different run. When that happens, uninstall the old debug app first, then install the newly downloaded APK. The app's saved local session will be removed when uninstalling.

## Local build

Install JDK 17 and Android SDK Platform 34 / Build Tools 34.0.0, then run:

\`\`\`bash
./gradlew :app:assembleDebug
\`\`\`

On Windows:

\`\`\`bat
gradlew.bat :app:assembleDebug
\`\`\`

The included \`gradle-wrapper.jar\` is a small project-local bootstrap launcher, not the upstream Gradle Wrapper JAR. Its Java source is included at \`gradle/wrapper/GradleWrapperMain.java\`. It downloads the Gradle 8.7 binary distribution over HTTPS, verifies its SHA-256 against \`gradle-wrapper.properties\`, and launches it. See \`gradle/wrapper/WRAPPER-NOTICE.md\`. GitHub Actions installs Gradle directly and does not depend on this bootstrap launcher.

## Native screens and hosted API

- Home: native Compose screen
- Sign-in: native Compose form calling the official TOTEM session API
- Chat: native Compose screen using mobile workspace APIs and server-sent streaming responses
- Chat history: native drawer for selecting previous conversations
- Wallet: native Compose screen for credits and an already-connected wallet balance
- Profile: native Compose screen
- Hosted origin: \`https://beta.totemai.xyz\`

The Android app does not contain a local AI backend, model runtime, credit ledger, payment processor or private-key manager. Wallet signing remains in the external wallet app. The current hosted API does not expose a safe endpoint for linking a new wallet to an existing account. Do not enter a seed phrase or private key into this app.

## Security notes

- HTTPS only; cleartext traffic is disabled.
- Account sessions and mobile-device tokens are stored using Android Keystore-backed encrypted preferences when available.
- If encrypted storage is unavailable, the session falls back to memory-only storage and will not survive an app restart.
- App backup is disabled; TLS errors are never bypassed.

See \`docs/SECURITY.md\`, \`docs/PORT_PARITY.md\`, \`docs/TESTING.md\` and \`docs/BUILD_GUIDE.md\` before distributing the app.
