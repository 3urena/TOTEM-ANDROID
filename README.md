# TOTEM MOBILE — Android

A native Android shell built with Kotlin, Jetpack Compose and Navigation Compose. The hosted TOTEM platform at **https://beta.totemai.xyz** remains the source of truth for accounts, authentication, AI models, credits, payments and tools. Workspace and wallet pages are hosted in a single, origin-restricted Android WebView; Home, Profile, splash screen, bottom navigation, offline/error states and encrypted native session storage are implemented natively.

- Application ID: `xyz.totemai.app`
- Version: `2.0` (`versionCode 2`)
- Minimum Android: 7.0 / API 24
- Target / compile SDK: 34
- Java: 17
- Gradle: 8.7
- Android Gradle Plugin: 8.3.2
- Kotlin: 1.9.24

## Build an APK with GitHub Actions

1. Create a new GitHub repository for this Android project.
2. Extract this ZIP and upload the **contents** of the `TOTEM-MOBILE` folder to the repository root (the `.github` folder must also be uploaded).
3. Commit the files to the `main` branch.
4. Open the repository's **Actions** tab and select **Build TOTEM AI Android APK**.
5. For a manual build, click **Run workflow** and choose `main`. A build also starts automatically after a push to `main` and for pull requests.
6. When the workflow finishes successfully, open the run and download the **TOTEM-APK** artifact.
7. Extract the downloaded artifact. It contains `app-debug.apk`. Transfer it to the Android phone and open it to install. Android may ask you to allow installation from that source.

The workflow installs JDK 17, Android SDK Platform 34 / Build Tools 34.0.0 and Gradle 8.7, then runs `:app:assembleDebug`. The APK is a debug build and is not Play Store signed.

## Local build

Install JDK 17 and Android SDK Platform 34 / Build Tools 34.0.0, then run:

```bash
./gradlew :app:assembleDebug
```

On Windows:

```bat
gradlew.bat :app:assembleDebug
```

The included `gradle-wrapper.jar` is a small project-local bootstrap launcher, not the upstream Gradle Wrapper JAR. Its Java source is included at `gradle/wrapper/GradleWrapperMain.java`. It downloads the Gradle 8.7 binary distribution over HTTPS, verifies its SHA-256 against `gradle-wrapper.properties`, and launches it. See `gradle/wrapper/WRAPPER-NOTICE.md`. GitHub Actions installs Gradle directly and does not depend on this bootstrap launcher.

## Hosted service and navigation

- Hosted origin: `https://beta.totemai.xyz`
- Home: `/`
- Login: `/login`
- Wallet connections: `/settings/mobile-connections`
- Deep links: `totemai://home`, `totemai://workspace`, `totemai://wallet`, `totemai://profile`

The Android app does not contain a local AI backend, model runtime, credit ledger, payment processor or private-key manager. Wallet signing remains in the external wallet app. Do not enter a seed phrase or private key into this app.

## Security notes

- HTTPS only; cleartext traffic is disabled.
- The WebView loads only the allowlisted TOTEM hosts in its main frame; other web destinations open externally.
- The JavaScript message bridge is bound to the exact `https://beta.totemai.xyz` origin and ignores subframe messages.
- A native mirror of the hosted session token is stored in Android Keystore-backed `EncryptedSharedPreferences`; if encryption is unavailable, this mirror is memory-only. The hosted frontend also retains its own localStorage copy inside WebView storage, so the native mirror does not mean every copy of the token is encrypted at rest.
- Third-party cookies, geolocation, file-system access and JavaScript-created pop-up windows are disabled.
- File upload uses Android's system picker and the user's selected content URI.
- App backup is disabled; TLS errors are cancelled rather than bypassed.

See `docs/SECURITY.md`, `docs/PORT_PARITY.md`, `docs/TESTING.md` and `docs/BUILD_GUIDE.md` before distributing the app.
