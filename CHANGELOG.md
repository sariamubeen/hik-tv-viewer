# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.4.0] - 2026-09-04

### Added
- Network discovery: SADP multicast + a credential-free subnet sweep, alongside manual IP entry.
- Real channel enumeration via ISAPI - actual names, codec, online state, and audio capability, replacing the hardcoded channel list.
- Real video and audio playback via libVLC (RTSP over TCP), with per-channel option tuning driven by Hikvision's `SmartCodec` flag.
- Live video decode concurrency queried from the device's own `MediaCodecInfo` at startup; the grid uses live video up to that budget and snapshot polling beyond it.
- Multi-device support: saved devices persist until an explicit Sign out, no more re-prompting on every launch.
- Scrolling grid sized to the actual channel count (was a fixed 3x2).
- `BackHandler`-based navigation: Back reliably returns to the grid and restores focus to the camera you were viewing.
- Auto-hiding HUD and a mute toggle in the single-camera view.
- Unit tests for ISAPI XML parsing, subnet math, SADP reply parsing, and the credential migration path, using response fixtures captured from a real device.

### Changed
- `data/Settings.kt` replaced by `data/Device.kt` (`Device` + `Channel` models); `SecureStore` migrates existing installs' saved credentials automatically.
- Snapshot poller (`SnapshotPoller.kt`) is now frame-paced, lifecycle-aware, and surfaces a distinct error state instead of an indefinite black tile.
- Grid tiles request the substream snapshot instead of the main stream when falling back to polling.

### Fixed
- Snapshot poller restarting on every frame due to an unstable lambda key (the primary cause of the choppy "frame-by-frame" video).
- Back button exiting the app from the single-camera view on modern Android instead of returning to the grid.
- Grid always focusing the first tile on return, instead of the camera just viewed.

## [0.1.0] - 2026-05-08

### Added
- Initial public release.
- First-run setup screen for Hikvision DVR/NVR connection (host, port, credentials, channels).
- Encrypted credential storage using AndroidX Security `EncryptedSharedPreferences`.
- Snapshot-based grid view (3x2 layout, fullscreen, no gaps).
- Per-tile JPEG polling via Hikvision ISAPI `/Streaming/channels/{ch}01/picture` with HTTP digest auth.
- Smooth frame-handoff: previous bitmap stays visible until next frame finishes decoding (no blink).
- Configurable poll rate: 5 fps in grid, 10 fps in single-camera fullscreen view.
- D-pad navigation, OK to expand a camera, Back to return to grid, Menu to re-open setup.
- Catppuccin Mocha theme.
- Leanback launcher integration so the app appears on the Android TV apps row.
