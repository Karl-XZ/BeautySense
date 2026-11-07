# V2 native mirror

This module owns the native single-front-camera workflow, CameraX/MediaPipe tracking, regional mesh rendering and V2 session HTTP integration.

- Domain decisions are in docs/superpowers/specs/2026-11-07-beautysense-v2-requirements.md from the repository root.
- The companion Web service owns the PROYA catalog, model invocation and server-side selection validation. Never invent products, variants or precise shade data in this client.
- Preserve epoch, selection revision and operation ownership for every asynchronous update, including speech and explanation retries. Reset and identity discontinuity clear private state.
- Core click actions cannot require microphone access. Private frames remain in memory; no app photo logging or automatic test portraits in production.
- DebugPortraitInput has separate debug/release sources. Release must not enable synthetic input.
- Run :beautysense:testDebugUnitTest, :beautysense:assembleDebug and :beautysense:compileReleaseKotlin. Report emulator and physical-camera evidence separately.
