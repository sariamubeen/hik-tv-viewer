# Hik TV Viewer

A small Android TV app for viewing Hikvision DVR/NVR cameras as a fullscreen grid.
Configure once, then live-monitor your cameras straight from the TV.

[![Build](https://github.com/sariamubeen/hik-tv-viewer/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/sariamubeen/hik-tv-viewer/actions/workflows/build.yml)
[![Latest build](https://img.shields.io/github/v/release/sariamubeen/hik-tv-viewer?include_prereleases&label=latest%20build)](https://github.com/sariamubeen/hik-tv-viewer/releases/tag/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Android API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)](https://developer.android.com/about/versions/nougat)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-blue.svg)](https://kotlinlang.org/)

## What it is

A no-frills TV client for Hikvision DVR/NVR systems. There's no Hikvision-published Android TV app, and the Android-phone iVMS clients aren't TV-friendly. This fills that gap.

Up to a 3x2 fullscreen grid of cameras, with one-tap entry into a higher-resolution single-camera view. D-pad-driven, edge-to-edge, fast.

## Why snapshots and not RTSP?

The honest answer: most modern Hikvision firmwares default to **H.265 (HEVC)** for channel encoding, and Android's RTSP stack (both ExoPlayer's RTSP module and libVLC) handles Hikvision's H.265-over-RTP packetization unreliably. The connection succeeds, then either fails to decode or shows a black frame indefinitely.

So this app uses Hikvision's ISAPI snapshot endpoint (`/ISAPI/Streaming/channels/{ch}01/picture`) with HTTP digest auth and polls it on a tight loop:
- **5 fps per tile** in the grid (six tiles ≈ 30 req/s on the DVR)
- **10 fps** in single-camera fullscreen
- Each new frame is decoded before the previous one is dropped, so there's no flash between frames

The result reads as smooth surveillance video on a TV-sized screen, and works on every Hikvision device that supports the ISAPI snapshot endpoint — which is essentially all of them since 2018.

## Features

- First-run setup screen with **Test Connection** button
- Credentials stored encrypted via `EncryptedSharedPreferences`
- 3x2 fullscreen grid, no gaps, edge-to-edge tiles
- D-pad navigation: arrows to move, **OK** to expand a tile, **Back** to return, **Menu** to re-open settings
- Catppuccin Mocha theme
- Registers as a Leanback launcher app — appears on the Android TV apps row
- ~17 MB APK, no external services, runs entirely on your LAN

## Installation

There are two release flavors:

- **[Latest build](https://github.com/sariamubeen/hik-tv-viewer/releases/tag/latest)** — auto-built from `main` on every push. Always the freshest, may have rough edges.
- **[Tagged releases](https://github.com/sariamubeen/hik-tv-viewer/releases?q=v)** — stable, versioned (`v0.1.0` etc.).

### Option 1 — Sideload via USB drive (no ADB required)

1. Download `HikTvViewer-latest.apk` from the [Latest build](https://github.com/sariamubeen/hik-tv-viewer/releases/tag/latest) (or pick a tagged version)
2. Copy it to a FAT32 / exFAT USB stick (root folder)
3. On your Android TV: install a file manager from the Play Store (e.g. **File Commander**)
4. Enable **Install unknown apps** for the file manager:
   `Settings → Apps → File Commander → Install unknown apps → On`
   *(exact path varies between TV brands)*
5. Plug in the USB stick, open the file manager, find `app-debug.apk`, install
6. Launch the app from the Android TV apps row

### Option 2 — Sideload via ADB over LAN

If your TV exposes network ADB (Settings → Developer options → Network debugging):

```bash
adb connect TV_IP:5555
adb -s TV_IP:5555 install -r app-debug.apk
```

## First-run configuration

On launch the setup screen asks for:

- **Host** — your DVR/NVR's IP or domain
- **HTTP port** — usually `80` (or whatever the DVR's web UI runs on)
- **HTTPS** — toggle if your DVR uses HTTPS
- **Username / Password** — any account with snapshot permission (a low-privilege "viewer" user is fine)
- **Channels** — comma-separated channel numbers (e.g. `1,2,3,4`)

Hit **Test connection** to verify (it pings `/ISAPI/System/deviceInfo`), then **Save & continue**.

To change settings later: from the grid, press **Menu** on your remote.

## Building from source

Requirements:
- JDK 17 (Temurin recommended)
- Android SDK with platform 34 and build-tools 34.0.0+
- `ANDROID_HOME` set, or `local.properties` with `sdk.dir=`

```bash
git clone https://github.com/sariamubeen/hik-tv-viewer.git
cd hik-tv-viewer
./gradlew :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

## Project structure

```
app/src/main/kotlin/com/hiktv/viewer/
  MainActivity.kt              # entry point
  ui/
    App.kt                     # screen router (Setup → Grid → Live)
    SetupScreen.kt             # first-run wizard with test-connection
    GridScreen.kt              # 3x2 fullscreen grid
    LiveScreen.kt              # single-camera fullscreen with prev/next
    SnapshotPoller.kt          # the smooth-handoff frame poller
    theme/Theme.kt             # Catppuccin Mocha colors
  data/
    Settings.kt                # config + URL builders
    SecureStore.kt             # EncryptedSharedPreferences wrapper
    HikClient.kt               # OkHttp + digest auth
```

## Compatibility

Tested against Hikvision DVR firmware V4.83.x. Should work on any Hikvision device that exposes the ISAPI snapshot endpoint, which covers the vast majority of devices from 2018 onward.

If your device serves snapshots at a different path, open an issue with the working URL and I'll add it as a configurable option.

## Limitations

- **No audio** — snapshots are video frames only.
- **~5-10 fps** — not real-time; perfectly fine for monitoring, not for forensic playback.
- **Hikvision-specific** — Dahua, Reolink, and other brands won't work without changes to the URL builders in `data/Settings.kt`.
- **No PTZ controls** — view-only.
- **No recording playback** — live monitoring only.

PRs welcome on any of the above.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[MIT](LICENSE) © 2026 sariamubeen
