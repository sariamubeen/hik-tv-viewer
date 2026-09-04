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

Scan the network or type an IP, sign in once, pick which cameras to show, and get a scrolling grid of live video with one-tap entry into a full-resolution single-camera view with audio. D-pad-driven, edge-to-edge, fast.

## Video engine: libVLC, not ExoPlayer

Most modern Hikvision firmwares default to **H.265 (HEVC)** for channel encoding. An earlier version of this app used ISAPI snapshot polling instead of real RTSP, because early testing found Android's RTSP stacks unreliable against Hikvision streams.

Digging deeper: Media3/ExoPlayer's RTSP module has a real, still-open gap for exactly this case ([androidx/media#901](https://github.com/androidx/media/issues/901) - it requires an `fmtp` attribute with `sprop-vps`/`sprop-sps`/`sprop-pps` for H.265 that some Hikvision firmware doesn't send). libVLC's failure reports, on inspection, trace mostly to Hikvision's proprietary "H.265+" smart-codec variant interacting with FFmpeg's low-framerate frame threading, and to insufficient RTSP network-caching for H.265's burstier GOP delivery - both tunable, not a structural parsing failure.

So this app now plays real RTSP video via **libVLC**, forced to RTSP-over-TCP, with per-channel option tuning driven by whether that channel's `SmartCodec` flag is set. Video decode concurrency is queried from the TV's own `MediaCodecInfo` at startup rather than assumed, so the grid uses live video up to whatever the hardware can actually decode and falls back to snapshot polling (still available, now frame-paced and lifecycle-aware) beyond that.

## Features

- **Network scan** (SADP multicast + a credential-free subnet sweep) or manual IP entry
- Enumerates the DVR/NVR's **real channels** - actual names, codec, and online state - instead of a hardcoded list
- Scrolling grid sized to however many cameras you have, not a fixed 3x2
- Live video with audio in the single-camera view; muted live video in the grid up to the TV's real decoder budget
- Credentials stored encrypted via `EncryptedSharedPreferences`, entered once - **Sign out** from the device settings screen is the only way back to the login flow
- D-pad navigation: arrows to move, **OK** to expand a tile (or mute/unmute in the single view), **Back** returns to the grid and remembers which camera you were on, **Menu** opens device settings
- Catppuccin Mocha theme
- Registers as a Leanback launcher app - appears on the Android TV apps row

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

On launch: **scan** the network (or choose **Enter IP address manually**), pick your DVR/NVR from the results, then enter:

- **Host / HTTP port / HTTPS** - pre-filled from the scan if you picked a device
- **Username / Password** - any account with streaming permission (a low-privilege "viewer" user is fine)

Hit **Connect** - this verifies the device and enumerates its real channels, then shows a checklist of the actual cameras it found (real names, not guessed numbers) to choose which ones appear in your grid.

To change devices or channels later, or sign out: from the grid, press **Menu** on your remote.

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
    App.kt                     # screen router
    DevicePickerScreen.kt      # scan results + manual entry
    CredentialsScreen.kt       # host/port/user/pass, connect + enumerate
    ChannelPickerScreen.kt     # checklist of the DVR's real channels
    DeviceSettingsScreen.kt    # edit channels, add device, sign out
    GridScreen.kt              # scrolling grid, video/snapshot per decoder budget
    LiveScreen.kt              # single-camera fullscreen, video + audio
    VlcPlayer.kt               # libVLC Compose wrapper
    SnapshotPoller.kt          # frame-paced fallback poller
    theme/Theme.kt             # Catppuccin Mocha colors
  data/
    Device.kt                  # Device + Channel models, URL builders
    SecureStore.kt              # EncryptedSharedPreferences wrapper + migration
    HikClient.kt                # OkHttp + digest auth
    Isapi.kt                    # channel enumeration + XML parsing
    Discovery.kt                 # SADP multicast + subnet sweep
    DecoderCapabilities.kt       # queries the TV's real decode concurrency
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
