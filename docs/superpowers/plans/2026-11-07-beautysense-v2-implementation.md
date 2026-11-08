# BeautySense V2 Implementation Plan

> For agentic workers: implement the tasks below with test-driven development. Independent Android, catalog, and generation tracks may proceed concurrently; the integration owner maintains the shared HTTP contract.

**Goal:** Deliver the approved single-front-camera, analysis, PROYA selection and live makeup workflow on Web and Android, with real model calls and separately recorded camera evidence.

**Architecture:** Versioned session-bound V2 HTTP endpoints share one product catalog and generation pipeline. A new browser client and native Android flow consume the same response types. Generated regional makeup layers are warped using live face landmarks; legacy routes remain available only for previously released clients.

**Tech Stack:** Existing Node ESM server and Python vision worker; browser MediaPipe FaceLandmarker and Canvas/WebGL; Android Kotlin/Compose/CameraX with MediaPipe tracking.

**Spec:** ../specs/2026-11-07-beautysense-v2-requirements.md

## Global constraints

- Only PROYA brand; genuine sourced images; no invented shades or unavailable categories.
- Each category has at most one product/variant, total selected >= 1; skincare-only selection preserves original appearance.
- Camera-only core flow; microphone optional; no private images persisted to public files or logs.
- Stable user identity and selection revision bind every asynchronous response.
- No mouse or keyboard takeover. Use terminal browser automation for isolated test browsers.
- Generation must call a real image model; unavailable generation is reported as unavailable, never replaced with a fake success.

## Shared interface

All private requests use `Authorization: Bearer <session.token>` and JSON. Data URLs contain JPEG/PNG bytes. Errors are `{ok:false,error:{code,message}}`.

- `POST /api/v2/sessions` -> `{ok:true,session:{id,token,expiresAt}}`.
- `DELETE /api/v2/sessions` -> `{ok:true}`; revokes all jobs and private in-memory data.
- `GET /api/v2/catalog` -> `{ok:true,catalog:{version,categories:[{id,name}],products:[{id,brand,name,categoryId,image,source,description,ingredients,usage,variants:[{id,name,regions:[],colorHex?}],...}]}}`.
- `POST /api/v2/analysis` body `{frameDataUrl,capturedAt}` -> `{ok:true,analysis:{id,skinMetrics,faceFeatures,summary,selectionAdvice,quality,model}}`.
- `POST /api/v2/recommendations` body `{analysisId}` -> `{ok:true,recommendations:[{productId,reason}]}`.
- `POST /api/v2/generate` body `{analysisId,selectionRevision,items:[{productId,variantId}],frameDataUrl,capturedAt}` -> `{ok:true,job:{id,status:'queued'}}`.
- `GET /api/v2/jobs/:id` -> `{ok:true,job:{id,status:'queued'|'running'|'completed'|'failed',selectionRevision,effect?,error?}}`.
- Effect: `{textureDataUrl,sourceLandmarks:[[x,y,z],...],triangles:[integer,...],regions:['skin'|'lip'|'eye'|'blush'],engine,inputHash,generatedAt,unchanged?:boolean}`. Normalized source coordinates correspond to the original submitted frame; PNG texture uses the same full-frame dimensions. Exactly 478 source landmarks for MediaPipe. `unchanged:true` has no texture and denotes skincare-only selection.
- `POST /api/v2/dialogue` body `{text,stage,analysisId?,items?,visibleProductIds?}` -> `{ok:true,reply,action?:{type,productId?,variantId?,categoryId?,value?}}`. Actions go through the same client reducer and server constraints.
- `POST /api/v2/asr` / `tts`: reuse existing real speech services behind V2 validation.

Catalog file owner: `C:/temp/beautysense-modelscope-20261005/data/proya-catalog.json`, image URLs under `/assets/proya/`.
Generation owner: `v2/generation.mjs` exports `generateMakeup({frameDataUrl,items,signal})` returning the Effect above. It may invoke `python/makeup_v2.py`; keys remain server-side. No modification of server.mjs by that track.

## Review focus

1. Same-category variants and service-side forged selections.
2. Session reset or selection changes during generation and late callbacks.
3. Camera identity discontinuity and absent tracking, including background lifecycle.
4. Missing official product images or inconsistent version/shade claims.
5. External image URLs, oversized/private data, failure statuses and generation credentials.

## Task 1 Session and catalog contract

- [x] Write Node tests for category validation, session authorization/expiry, cancellation and forged variants; observe failure.
- [x] Add `v2/service.mjs` with in-memory session owner and a request handler dependency-injected with existing vision/chat calls.
- [x] Obtain PROYA-only catalog and actual official assets; validate source and image decoding.
- [x] Run relevant Node tests and record result.

## Task 2 Real analysis, recommendations and generation

- [x] Test real-shaped analysis normalization, non-catalog LLM references and failed/late jobs.
- [x] Connect existing local skin worker and real vision/LLM calls; exclude unsupported hydration percentages.
- [x] Implement real image-model invocation and local region/landmark makeup extraction; compare model availability and resource constraints.
- [x] Generate fictional human test portraits using built-in imagegen, then invoke the actual configured API with them.
- [x] Record provider/model/request evidence without keys or private image payloads.

## Task 3 Browser single-mirror client

- [x] Test flow reducer before implementation: home/align/analyze/results/select/generate/live, per-category constraint, cancellation/revision invalidation.
- [x] Add `v2/index.html`, `app.js`, `state.mjs`, `style.css`, `renderer.js`; route `/` to V2, retain old UI only under explicit legacy route for regression.
- [x] Track real camera with FaceLandmarker; map generated texture triangles to current landmarks; hide absent/occluded/uncertain identity; keep camera alive across panels.
- [x] Add auxiliary dialogue and speech through the common actions, optional microphone only.
- [x] Run isolated Playwright with generated test-video input and actual service, plus negative API tests and screenshots.

## Task 4 Android native client

- [x] Unit-test state/selection/job revision and microphone-independent camera start.
- [x] Implement V2 Compose flow and native front CameraX tracking/texture render, using the shared HTTP contract.
- [x] Remove released UI entry to old wear/guide functionality, update version and build both APK and JVM tests.
- [x] On available emulator/physical device exercise permissions, lifecycle, camera, flow and inspect actual results; record physical-device absence explicitly if unresolved.

## Task 5 Integration and delivery

- [x] Run legacy privacy regression and V2 contract/browser/Android suites.
- [x] Inspect genuine product images and generated outputs; run multi-session A/B and late-response tests.
- [x] Run actual-key vision, recommendation, image generation and speech requests; distinguish API failure from successful generated images.
- [x] Fresh independent whole-branch code review, fix important findings with failing regression tests.
- [x] Commit both source branches; deliver APK, Web address, source and a requirement-mapped test report with actual evidence and gaps.

## Progress and rulings

2026-11-07: Approved specification and explicit authorization for actual API-key testing. Physical adb inventory currently empty. Existing source repositories are clean on the feature branch. Main integration owner implements Web/service; non-overlapping native/catalog/generation tracks follow this contract.


2026-11-08: Implementation and PC verification delivered on the feature branches. Web suites: 30 unit/API checks and 14 browser checks. Android: 23 JVM tests, debug APK and release Kotlin compile. Real DashScope analysis/recommendation/image generation and ASR/TTS verified with generated fictional input. An Android emulator completed genuine API generation and the original/effect toggle. Physical-device testing deferred by explicit user instruction to test on PC first; no physical acceptance claim. See the companion Web reports/v2/TEST-REPORT-20261108.md for A01–A52 evidence boundaries.

Engine ruling: actual Qwen image2.0 edit plus regional mesh extraction; no FRAM/Stable-Makeup/EleGANt weights integrated. PROYA catalog has 8 genuine products and 16 official product photos; no verified human makeup-reference image or calibrated exact shade. Formal physical tracking, P95 and production ModelScope acceptance are not marked passed.

Final error coverage: partial local skin report survives failed LLM interpretation, POST /api/v2/explanation retries the same report, and clients guard epoch/report ownership. Analysis requests cap at 60 seconds and generation at 90 seconds. Private speech request/response payloads are redacted. Final release remains a development preview; stable deployments are preserved.

2026-11-08 UI correction: researched YouCam official storefront screenshots and the historical Sephora Virtual Artist design case. Added a persistent protected oval, separate camera/information regions, whole-face alignment validation, compact product selection, docked assistant and inline reanalysis confirmation. PC browser layout and Android emulator measured bounds include keyboard/landscape. Current version 2.0.0-preview.1, versionCode 6. No new physical-camera acceptance claim.
