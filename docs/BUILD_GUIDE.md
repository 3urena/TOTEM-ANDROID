# Build guide

## GitHub Actions (recommended)

1. Create a new GitHub repository for the Android project.
2. Upload the contents of this folder to the repository root, including the hidden `.github` folder.
3. Push to the `main` branch.
4. Open **Actions** → **Build TOTEM AI Android APK**.
5. Select **Run workflow** for a manual build. Pushes to `main` and pull requests also trigger the workflow.
6. After a green run, open the run summary and download the `TOTEM-APK` artifact.
7. Extract `app-debug.apk` and install it on the Android phone.

The workflow provisions JDK 17, Android Platform 34, Build Tools 34.0.0 and Gradle 8.7, then assembles a debug APK. A successful CI run is the first real Android build verification for this source tree.

## Local build

Requirements: JDK 17, Android SDK Platform 34, Android Build Tools 34.0.0 and an internet connection for Gradle/dependency downloads.

Linux/macOS:

```bash
./gradlew :app:assembleDebug
```

Windows PowerShell or Command Prompt:

```bat
gradlew.bat :app:assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The custom bootstrap launcher verifies the Gradle 8.7 archive against the SHA-256 configured in `gradle-wrapper.properties`. GitHub Actions uses the Gradle setup action directly and invokes `gradle`, so the CI build is independent of the local bootstrap implementation.
