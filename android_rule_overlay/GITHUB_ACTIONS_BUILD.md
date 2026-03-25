# GitHub Actions APK Build

This project can build a debug APK with GitHub Actions.

## Repo layout assumption

- repository root: current workspace root
- Android project: `android_rule_overlay/`
- workflow file: `.github/workflows/build-android-apk.yml`

## What the workflow does

1. Checks out the repository.
2. Installs JDK 17.
3. Installs Android SDK command line tools.
4. Installs `platforms;android-34` and `build-tools;34.0.0`.
5. Installs Gradle 8.7.
6. Builds `:app:assembleDebug`.
7. Uploads `app-debug.apk` as an artifact.

## How to use

1. Push this workspace to a GitHub repository.
2. Open the repository Actions tab.
3. Run `Build Android APK`, or push to `main` or `master`.
4. Download the artifact named `rule-overlay-debug-apk`.

## Notes

- This workflow currently builds a debug APK for self-installation and testing.
- If you later want a release APK or AAB, add signing configuration and secrets.
- If your GitHub repository root is `android_rule_overlay/` itself, move the workflow into that repository and remove the `working-directory: android_rule_overlay` line.
