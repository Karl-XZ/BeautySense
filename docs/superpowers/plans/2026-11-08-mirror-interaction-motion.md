# Mirror interaction and motion implementation plan

> For agentic workers: use subagent-driven-development for the independent Web and native UI tasks; verify and review before committing.

**Goal:** Refine the approved minimal single-camera mirror UI with clear, restrained feedback inspired by Apple HIG, for both Web and APK.
**Architecture:** Presentation-only changes on the current isolated codex/beautysense-v2-live-tryon feature branch. Keep session ownership, product facts, API contracts and camera/render pipelines intact. Use existing CSS/Web Animations and Compose/Material APIs; no new dependencies.
**Design basis:** Existing approved minimal product rail, short metrics and lower-area details; user requested Apple design principles. Our implementation choices are short (~120–260ms) control/panel transitions, immediate selection feedback, stable scroll/focus, and system reduced-motion support. Apple does not prescribe these exact timings.

## Design and impact
- Mirror content is the primary content. Never animate, blur, snapshot or crossfade camera/face/mesh subtrees. Never mount a second camera for an outgoing view.
- Keep oval guide, layout allocation, large-font and landscape face-safe-area invariant. Panel animation is clipped inside allocated information area, never above it.
- Taps get subtle press feedback; selection keeps visible check/semantic state. Native long press may use system haptics, respecting platform settings. No forced audio/haptics on every tap.
- Details/report/assistant transitions preserve context. Close and reset remain immediately operable; animation must not delay action ownership or keep old private content interactive.
- Preserve selected categories, horizontal/vertical scroll and keyboard focus when selecting within the same context. Reset clears transient presentation state.
- Animate real status changes and report metrics once, not every camera frame. No fake numeric progress, endless decorative shimmer or new long copy.
- Reduced motion uses system preference: Web prefers-reduced-motion; native platform/Compose duration scale. Disable translation/scale where reduced motion is active.
- Do not change backend, recommendation policy, credentials, storage, schemas, model requests or catalog.
- Risks: render() replaces nodes; longpress suppression/focus must survive. Native transitions must not duplicate camera or stale enabled content. Validate fast interruption, motion-disabled, text scaling and face geometry.
- Rollback: revert this task's source commits only; no data migration or release required. Public deployment/default branch remains unchanged.

## Task 1 — Web
Files: v2/app.js, v2/style.css, v2/index.html only if necessary; small focused motion helper if justified; relevant browser/presentation tests and package version metadata.
- Add a meaningful regression for scroll/focus continuity and reduced-motion / interrupted transitions; observe failure before logic changes.
- Implement short press/selection, panel/detail/content feedback within dock; preserve category/product rail and focus across same-context renders. Keep existing longpress and comparison fixes.
- Reuse CSS/platform APIs. Graceful fallback without Web Animations or reduced-motion; do not wait for animation to invoke a core action.
- Run syntax, unit and browser suites; save screenshots/motion evidence outside Git. Avoid overwriting tracked reports permanently. No desktop CUA or phone operations.

## Task 2 — Native
Files: MirrorScreen.kt, MirrorPanels.kt, a small presentation-only helper if needed, relevant tests; beautysense/build.gradle versionCode10 and versionName2.0.0-preview.5.
- Add meaningful regression only for new logic; preserve existing tags/selectors and core stage/state code.
- Refine custom action/product press and selection, lower-pane transitions and metric presentation using existing Compose libraries. Keep camera/mesh composables and geometry outside animated content.
- Preserve longpress-only disclosure, visible details route, Back/close/focus behavior. Honour platform duration scale; no independent infinite loop.
- Run native unit tests, assembleDebug/assembleDebugAndroidTest and compileReleaseKotlin with JDK17. Do not access phone.

## Task 3 — Integration acceptance
- Independently inspect both diffs; verify scope matches above.
- Run targeted headless browser with normal/reduced motion, quick open-close/reset/rapid selection, all product categories, small/landscape/large text. Capture actual UI and a short interaction recording.
- Install final native APK on connected V2509A; inject only the existing generated debug test portrait for deterministic UI; exercise real analysis/recommendation and preview controls; capture screens. Verify actual camera opens/releases and animation-disabled settings; restore settings and remove fixture/test harness.
- Keep model/camera/human-face evidence separate. Full real generation was verified in previous task; this change must not claim new human motion verification without cooperation.
- Update evidence and feature branches only after fresh checks; deliver APK and screenshots/recording with concise Apple reference and exact testing limits.
