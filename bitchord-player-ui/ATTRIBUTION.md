# BitChord Player UI — License and Attribution Notice

## Original Work Attribution
- **Project:** BitChord (BitChord Music Player)
- **Original Author & Maintainers:** Kushagra Singh and the BitChord Contributors
- **Source Repository:** https://github.com/kushagrasinghx/BitChord
- **Release Version Extracted:** 1.8.1
- **License:** GNU General Public License v3.0 (GPLv3)

## License Terms
This module (`:bitchord-player-ui`) contains source code adapted from the BitChord project under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

The full license text is provided in the [LICENSE](LICENSE) file located in this directory.

## Summary of Adaptations for DIVINE DECK
1. **Module Architecture:** Extracted from BitChord's Kotlin Multiplatform desktop/Android hierarchy into an independent Android Compose Library (`com.android.library`).
2. **Pure UI Contract:** Stripped internal direct playback service bindings and network calls. The UI components are completely stateless presentation elements driven by explicit parameters and interaction callbacks (`onPlayPause`, `onNext`, `onPrevious`, `onSeek`, `onLyricClick`).
3. **Platform Specialization:** Android-specific implementations of Coil 3 image decoding, `Palette` color sampling, and vibration/haptics were integrated directly, eliminating multiplatform `expect`/`actual` indirection.
4. **Landscape Optimization:** Tailored for horizontal cockpit environments, preserving BitChord's dual-column landscape player, responsive sleeve dimensions, Apple Music-style hairline scrubber, and synchronized lyrics engine.

## Distribution Obligations Notice
Any distribution of compiled binary artifacts incorporating this GPLv3-licensed module is subject to the provisions of the GNU General Public License v3.0.
