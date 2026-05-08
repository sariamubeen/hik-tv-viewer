# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
