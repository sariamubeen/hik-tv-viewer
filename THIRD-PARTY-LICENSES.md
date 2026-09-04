# Third-party licenses

Hik TV Viewer's own source is MIT-licensed (see [LICENSE](LICENSE)). This file
covers what's bundled alongside it.

## libVLC (video and audio playback)

This app plays camera streams via **libVLC for Android**, version **3.7.2**
(`org.videolan.android:libvlc-all:3.7.2`), published by VideoLAN.

- **Java/Kotlin API layer**: GNU Lesser General Public License v2.1 or later
  (LGPL-2.1+). Full text: <https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html>
- **The native `libvlc.so` actually shipped in this artifact** additionally
  contains GPL-licensed components compiled directly into it (not as
  separable plugins) - specifically **x264**, **libdvdnav**, and
  **libdvdcss** - each GPL v2 or later. Full text:
  <https://www.gnu.org/licenses/old-licenses/gpl-2.0.html> and
  <https://www.gnu.org/licenses/gpl-3.0.html>

This does **not** change the license of this app's own source, which stays
MIT: libVLC is linked dynamically and unmodified, exactly as VideoLAN
publishes it.

**Source availability**: the source corresponding to the bundled libVLC
build is available from VideoLAN at
<https://code.videolan.org/videolan/libvlcjni> and
<https://code.videolan.org/videolan/vlc>, at the tag/commit corresponding to
release 3.7.2.

## Other bundled libraries

| Library | License |
|---|---|
| OkHttp (`com.squareup.okhttp3:okhttp`) | Apache License 2.0 |
| okhttp-digest (`io.github.rburgst:okhttp-digest`) | Apache License 2.0 |
| Jetpack Compose, AndroidX (`androidx.*`) | Apache License 2.0 |
| Coil (`io.coil-kt:coil-compose`) | Apache License 2.0 |
| kotlinx.coroutines, kotlinx.collections.immutable | Apache License 2.0 |
