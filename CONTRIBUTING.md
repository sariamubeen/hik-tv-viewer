# Contributing

Thanks for your interest. Bug reports, feature ideas, and pull requests are all welcome.

## Reporting bugs

Open an issue with:
- Your TV model and Android TV version
- DVR/NVR model and firmware version (visible in `Settings → System → Information` on most Hikvision devices)
- What you expected to happen
- What actually happened (screenshots help if the UI is involved)

## Pull requests

1. Fork the repo, branch off `main`
2. Keep changes focused — one fix or feature per PR
3. Run `./gradlew :app:assembleDebug` locally to confirm it builds
4. Update `CHANGELOG.md` under `[Unreleased]` if your change is user-visible

## Local development

You need:
- JDK 17 (Temurin recommended)
- Android SDK with platform 34 and build-tools 34.0.0
- Set `ANDROID_HOME` to your SDK path, or create `local.properties` with `sdk.dir=...`

Build:

```bash
./gradlew :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

## Coding style

- Kotlin official style (the Gradle build is configured for it)
- One feature per file when reasonable
- No comments unless they explain non-obvious *why*

## Questions

If you're unsure whether something fits, open an issue first to discuss before
investing time in a PR.
