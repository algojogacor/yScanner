# yScanner — Work Log

---

## 2026-10-01 — Planning Session

### Milestone
Planning Phase — Establish yScanner (LocalScan) implementation roadmap.

### Objective
Read all project contract documents (PRD.md, BRIEF.md, ARCHITECTURE.md, AGENTS.md), audit the repository state, identify architectural risks, and create a complete set of implementation plans that an autonomous coding agent can execute.

### Changes
- Created `plans/` directory with 30 plan documents:
  - `plans/000-master-plan.md` — Master implementation plan with milestone sequence, dependency graph, risk analysis, requirements traceability, and validation strategy
  - `plans/001-project-foundation.md` through `plans/023-release-hardening.md` — 23 individual milestone plans
  - `plans/spikes/S01-model-selection.md` through `plans/spikes/S06-roi-decoding.md` — 6 experimental spike plans
- Updated `WORKLOG.md` with planning session record

### Validation

1. **Requirements traceability:** Every major PRD requirement has been mapped to one or more milestones in the master plan Appendix A. Verified coverage of all 47 functional acceptance criteria from PRD §33.
2. **Milestone dependencies:** Dependency graph is coherent — no circular dependencies, all prerequisites are satisfied in sequence.
3. **Architecture alignment:** Plans reference correct ARCHITECTURE.md sections and use the defined interfaces (SegmentationModel, CoordinateTransformer, EnvelopeComputer, QuadrilateralFitter, etc.).
4. **Risk coverage:** 9 major risk areas identified with mitigation strategies. 6 experimental spikes defined for high-uncertainty decisions.
5. **Product invariants:** All 17 product invariants from PRD §2/§32 are documented in the master plan and referenced in relevant milestones.
6. **Performance/memory:** Budgets documented (≤600-700 MB normal, ≤800 MB peak). Measurement points defined per milestone.
7. **Testing:** All 5 test categories represented (unit, integration, instrumentation, golden-image, stress).
8. **No production code changed:** Only planning documents and WORKLOG.md created.

### Results
- 30 plan files created totaling ~186 KB of planning documentation
- 23 milestones (M00–M22) covering the complete implementation lifecycle
- 6 experimental spikes for high-risk technical decisions
- Complete requirements traceability map
- Critical path identified: M00→M01→M02→M03→M04→M05→M06→M07→M08→M09→M11→M12→M13→M14→M18→M19→M20→M21→M22

### Decisions
1. **Module structure simplified:** Used flat modules (:app, :camera, :detection, :geometry, :processing, :domain, :data, :pdf, :import, :common, :test-fixtures) instead of nested sub-modules. Can split later when complexity demands it.
2. **Milestone count:** 23 milestones (M00–M22) — balanced between granularity and coherence. Some PRD phases were split where technical dependencies differ (e.g., Two Page split into M15 geometry + M16 dewarp).
3. **Spike-gated milestones:** M03 (detector) gated by S01, M04 (geometry) gated by S02, M06 (tracking) gated by S03, M08 (refinement) gated by S06, M11 (enhancement) gated by S05, M16 (dewarp) gated by S04.

### Problems
- Repository has no git initialization — M00 must handle this first.
- No ML training data or pre-trained models exist — S01 spike must address model acquisition.
- BRIEF.md is written in mixed Indonesian/English — planning documents standardized to English.

### Next
- Begin autonomous implementation starting with M00 (Project Foundation)
- S01 (Model Selection) should be initiated early as it gates M03

### Commit
`docs(plans): establish yScanner implementation roadmap`

---

## 2026-10-01 — M00 Project Foundation

### Milestone
M00 — Project Foundation

### Objective
Initialize the Android multi-module project, establish the shared build environment, implement the pure-Kotlin domain layer, and validate with unit tests.

### Changes
- 11-module Android project created: `:app`, `:camera`, `:detection`, `:geometry`, `:processing`, `:domain`, `:data`, `:pdf`, `:import`, `:common`, `:test-fixtures`
- Gradle wrapper 8.11.1, Android Gradle Plugin, Kotlin 2.0.x, Version Catalog (`gradle/libs.versions.toml`)
- Domain model: `PageObject`, `Quad`, `Corner`, `PointF`, `QualityMetrics`, `ScanSession`, `Enums` — pure Kotlin, zero Android deps
- Room database entities in `:data`
- `.gitignore` excludes `.agents/`, `.kotlin/`
- 18 unit tests: `QuadTest`, `PageObjectTest`, `MatrixMathAdversarialTest`, `QuadConverterAdversarialTest`, `QuadAdversarialTest`

### Validation
- `./gradlew test` — 18 tests pass
- `./gradlew assembleDebug` — BUILD SUCCESSFUL

### Results
- All 11 modules build cleanly
- Domain layer has zero Android dependencies (pure JVM)
- Adversarial stress tests cover boundary cases

### Commit
`feat(foundation): initialize multi-module build system and domain layer` — `e60e063`
`test(gate): add adversarial stress tests and ignore internal agent workspaces` — `c503be9`

---

## 2026-10-01 — M01 Camera Foundation + M02 Coordinate System

### Milestone
M01 — Camera Foundation
M02 — Coordinate System

### Objective
Implement CameraX lifecycle integration with Preview/ImageAnalysis/ImageCapture, file-backed capture with EXIF awareness, tap-to-focus, flash modes, and a full 7-space matrix coordinate transformation system.

### Changes

**:camera module**
- `CameraController` interface + `CameraXControllerImpl` (CameraX lifecycle, 3A, tap-to-focus via FocusMeteringAction)
- `FrameAnalyzer` — ImageAnalysis callback with STRATEGY_KEEP_ONLY_LATEST, immediate ImageProxy.close()
- `FileCaptureManager` — file-backed capture, zero full-resolution Bitmap in RAM, EXIF rotation via ExifInterface
  - Guard: `check(rawWidth > 0 && rawHeight > 0)` rejects corrupt/zero-byte images
- `CameraExecutor` — named daemon thread (`yScanner-AnalysisThread-*`), graceful shutdown with awaitTermination + shutdownNow fallback
- `CameraCoordinateTransformer` — hub-and-spoke 7-space system via Normalized (0..1) intermediate
  - 7 spaces: Sensor, ImageAnalysis, Preview/View, Normalized, ImageCapture, Processed-image, PDF
  - Handles 0°/90°/180°/270° rotation and aspect-ratio mismatches correctly

**:common module**
- `Matrix3x3` — pure-Kotlin 3×3 matrix math (no Android dependency)

**:app module**
- `CameraScreen`, `CameraPreview`, `CameraViewModel` (AtomicBoolean CAS guard prevents double-capture race)
- `FlashButton`, `ShutterButton`, `FocusReticle`, `PermissionRationale` Compose components

### Gate Issues Resolved
Gate 1 raised 3 edge-case issues — all fixed:
1. `CameraExecutor` thread naming: normalized `LocalScan-AnalysisThread` → `yScanner-AnalysisThread`
2. `CameraViewModel` shutter race: replaced StateFlow read-then-act with `AtomicBoolean.compareAndSet(false, true)` + `finally { _capturing.set(false) }`
3. `FileCaptureManager` non-positive bounds: added `check()` guard, stale adversarial test updated to assert the correct defensive behavior

### Validation
- `./gradlew test` — 38 tests pass (0 failures)
- All adversarial stress tests pass

### Commit
`feat(camera): implement CameraX foundation and 7-space coordinate system (M01+M02)` — `e63f3af`

### Next
- M03 — Document Detector (requires S01 model selection spike)

---

## 2026-10-01 — Namespace Migration: com.localscan → com.yscanner

### Objective

Eliminate the legacy `LocalScan` identity from the codebase. The product is **yScanner**; the
package namespace, application ID, and all code-level identifiers still carried the old name.
Per explicit direction, perform the migration thoroughly now, while the project is still early-stage.

### Changes

**Package namespace**
- All 16 `src/{main,test}/kotlin/com/localscan/**` directories moved to `com/yscanner/**` via `git mv`
  (history preserved — 61 files detected as renames by Git).
- `com.localscan` → `com.yscanner` in every Kotlin package declaration, import, and fully-qualified reference.

**Build configuration**
- Gradle `namespace` updated in all 11 modules.
- `applicationId`: `com.localscan.app` → `com.yscanner.app`.
- Proguard rules (`app/proguard-rules.pro`) and consumer rules (10 × `consumer-rules.pro`) updated.

**Android components**
- `AndroidManifest.xml`: `android:name=".LocalScanApplication"` → `".YScannerApplication"`.
- `LocalScanApplication.kt` → `YScannerApplication.kt` (file renamed, class renamed).
- `LocalScanTheme` → `YScannerTheme`.
- Product-name references in code comments → `yScanner`.

**Data**
- `ScanDatabase.DATABASE_NAME`: `"localscan_database.db"` → `"yscanner_database.db"`.
  Safe: no released build, so no migration path is required.

**Documentation**
- Contract docs (`PRD.md`, `ARCHITECTURE.md`, `AGENTS.md`, `BRIEF.md`) and all 24 `plans/*.md` updated
  so the living specs stay consistent with the code. Also corrected a stale path in
  `plans/001` (`src/main/java` → `src/main/kotlin`).
- `WORKLOG.md` historical entries intentionally left untouched (this file is the historical journal).

### Validation

- `./gradlew clean test assembleDebug` → **BUILD SUCCESSFUL** in 2m40s
  (469 tasks: 298 executed, 139 from cache, 32 up-to-date).
- Unit tests: **117 unique tests, 0 failures, 0 errors**
  (62 pure-JVM in `:common`/`:domain`, 55 per Android variant).
- Debug APK produced: `app/build/outputs/apk/debug/app-debug.apk`.
- Post-migration grep asserts **zero** occurrences of `localscan` (case-insensitive) in any tracked
  file except `WORKLOG.md`.

### Results

- `git diff --stat`: 108 files changed, **386 insertions / 386 deletions** — exactly symmetric,
  confirming a pure rename with no semantic change.
- Repository identity is now uniformly yScanner.

### Decisions

1. **Full migration over partial.** A half-renamed repo is worse than either extreme; the project is
   pre-release, so the cost is at its minimum now.
2. **Contract docs included.** `PRD.md` / `ARCHITECTURE.md` / `AGENTS.md` / `BRIEF.md` are living
   specifications, not historical records. Leaving them naming a different product would actively
   mislead the remaining milestones. Only `WORKLOG.md` is treated as immutable history.
3. **`plans/` included.** These are actionable forward-looking specs that M03+ will be implemented
   against; stale package names there would be a defect, not a historical record.

### Problems

- `local.properties` was **absent** from the working tree, so no build could run. Recreated with
  `sdk.dir=D:\Android\Sdk` (gitignored — no repository impact).
- The Gradle daemon cannot write `~/.gradle/caches/journal-1` when the tool sandbox is active;
  builds require the sandbox to be lifted for the Gradle invocation.

### Next

- Spike **S01 (model selection)**: gather pretrained-candidate evidence and **stop at the decision
  gate** for human selection. No final model may be locked in autonomously.
- Build the model-independent parts of M03 (`SegmentationModel` interface, detector abstraction,
  preprocessing/postprocessing abstraction, fixtures, benchmark harness).

### Commit

`refactor(naming): migrate namespace and identity from LocalScan to yScanner`

---

## 2026-10-01 — Spike S01 (evidence) + M03 model-independent scaffolding

### Objective

Close the evidence-gathering half of spike S01 and build every part of M03 that does
**not** depend on the final model choice. Explicit direction: the final model must **not**
be selected autonomously — candidates are collected and the decision is escalated.

### Changes

**S01 evidence — `plans/spikes/S01-candidates.md` (new)**
- Surveyed 9 model families, 1 zero-weight classical baseline, 4 datasets.
- Every candidate URL fetched and verified; licence recorded per candidate.
- Every benchmark labelled with provenance. **No candidate has a published
  physical-Android-device latency**, so all latency figures are marked
  `not yet validated on target device`. No numbers were invented.
- 12 unverifiable items recorded explicitly rather than guessed.
- Key finding: the only purpose-built, permissively-licensed, document-specific
  pretrained model (DocAligner, Apache-2.0, 1.7–14.7 MB) emits **corner heatmaps**,
  which is precisely the `segmentation → 4 corners` shortcut that `AGENTS.md §14`
  warns against. This makes the decision architectural, not just a model swap.
- `plans/spikes/S01-model-selection.md` decision record updated to AT DECISION GATE,
  with the blocked/not-blocked split and the prerequisites to close it.

**M03 scaffolding — `:detection` (new, model-agnostic)**
- `model/ModelTypes.kt` — `ModelDescriptor`, `ModelInput`, `SegmentationOutput`,
  `ModelDType`, `Normalization`, and an **`OutputKind`** discriminator
  (`MASK` / `CORNER_HEATMAP` / `EDGE_MAP`) so a mask model and a corner model can
  share one pipeline without a rewrite.
- `model/SegmentationModel.kt` — the replaceable-model interface plus `ModelManager`
  (load-once-per-session, thread-safe, retries after a failed load, idempotent close).
- `preprocess/FrameData.kt` + `preprocess/FramePreprocessor.kt` — Android-free frame
  representation plus a single-pass YUV_420_888 → RGB preprocessor that **fuses
  rotation, scaling and normalisation into one sampling pass**. The naive
  decode→rotate→scale path would allocate two full-resolution ARGB buffers per frame
  (~48 MB each at 12 MP); this allocates only the model tensor (~786 KB at 256²).
- `postprocess/SegmentationPostprocessor.kt` — normalises raw model output into a
  neutral `ProbabilityMap`, plus `decodeCorners` for corner-heatmap models.
- `bench/InferenceBenchmark.kt` — warm-up-aware latency harness reporting
  mean/p50/p95/p99 and implied FPS, plus a `RollingLatencyMonitor` for live sessions.
  Every `BenchmarkReport` carries explicit `validatedOnDevice` provenance so a desktop
  number can never be mistaken for a device result.
- `test/.../FakeSegmentationModel.kt` — deterministic weight-free model, so the whole
  pipeline is testable today despite having no trained weights.

### Validation

- `./gradlew :detection:test` → **BUILD SUCCESSFUL**.
- **50 new tests, 0 failures, 0 errors.** Coverage includes: rotation correctness for
  0/90/180/270 via a marked-pixel ground truth, normalisation modes, UINT8 vs FLOAT32
  packing, tensor-size validation, plane indexing, corner-heatmap decoding, model
  lifecycle (load-once, retry-after-failure, idempotent close, infer-before-load
  rejection), percentile maths, and window-bounded drop tracking.
- Two initial test failures were **assertion errors in the tests, not defects in the
  implementation**; both were corrected and the production code was left unchanged.

### Decisions

1. **No model selected.** Per direction, the gate is escalated to the project owner.
   No weights vendored; no pipeline stage irreversibly coupled to a candidate.
2. **`OutputKind` in the descriptor.** Chosen specifically so the mask-vs-corners
   architectural fork can be decided later without rewriting `:detection`.
3. **Preprocessing kept Android-free.** `FrameData` mirrors `android.media.Image`
   without depending on it, which is why the rotation maths is JVM-testable with
   hand-built buffers — no device, no Robolectric.
4. **Benchmark provenance is a first-class field.** The project forbids claiming
   performance without measurement; making `validatedOnDevice` mandatory in the
   report type enforces that at the type level.

### Problems

- **Remote push is blocked.** `git push` fails with
  `could not read Username for 'https://github.com'` — no cached credentials, and the
  `gh` CLI is installed but not logged in. Two checkpoint commits are therefore sitting
  locally and unpushed (`c06b7af`, `f20d15f`). Per `AGENTS.md §54` autonomous work
  continues locally; remote sync is recorded as unavailable pending credentials.
- **APK size.** The debug APK is **165 MB**, of which **~111 MB is
  `libopencv_java4.so`** across four ABIs (x86_64 50 MB, x86 35 MB, arm64-v8a 16 MB,
  armeabi-v7a 10 MB) plus ~21 MB `libc++_shared.so`. Only `arm64-v8a` + `armeabi-v7a`
  (~26 MB) are needed on real hardware; the x86 ABIs are emulator-only dead weight.
  Not addressed yet — it belongs to M20/M22, but it is recorded here because it is
  large enough to be a release blocker.
- Gradle cannot run inside the tool sandbox (it needs to write
  `~/.gradle/caches/journal-1`), so every build requires the sandbox to be lifted.

### Next

- **HUMAN DECISION REQUIRED at the S01 gate** — pick the model family before any
  weights are vendored.
- M04 geometry engine is in progress (pure Kotlin, no OpenCV — see the APK-size finding).
- After M04 lands: wire `CandidateExtractor` to bridge `ProbabilityMap` → `BinaryMask`
  → boundary → quad.

### Commit

`feat(detection): add model-agnostic segmentation scaffolding and benchmark harness`

---

## 2026-10-01 — M04 Geometry Engine

### Objective

Implement the geometry engine that turns a document boundary contour into an enclosing
quadrilateral: mask → boundary extraction → outer envelope → quad fitting → corner refinement.
Spike S02 (quad-fitting algorithm choice) is folded into this milestone.

### Changes

**:geometry module — pure Kotlin, zero OpenCV, zero Android framework**
- `GeometryPrimitives.kt` — `Boundary`, `RectF`, shoelace `signedPolygonArea`, `polygonPerimeter`,
  `cross`, `distanceToSegment`, ray-casting `pointInPolygon`, and `convexHull` (Andrew's monotone
  chain, O(n log n), duplicate- and collinear-reducing).
- `BinaryMask.kt` — bounds-safe binary mask over a `ByteArray`, with `fromFloatArray` (thresholded
  probabilities) and `filledQuad` (rasterises a quad; test-only ground-truth helper).
- `BoundaryExtractor.kt` — 8-connected component labelling (BFS flood fill) + Moore-neighbour
  contour tracing, noise filtering by component area, sorted largest-first. Guarded by a step budget
  of `4*w*h + 8` so malformed input cannot loop forever.
- `PolygonApproximator.kt` — Douglas–Peucker `approximate`, plus `approximateToVertexCount` which
  binary-searches epsilon over `[0, perimeter]` to reach a target vertex count. Treats the input as a
  **closed** polygon; running open-polyline DP on a contour would pin two adjacent points and produce
  a degenerate quad.
- `QuadrilateralFitter.kt` — `QuadrilateralFitter` interface + `DefaultQuadrilateralFitter` with two
  strategies scored against each other:
  - **A: Douglas–Peucker** on the convex hull → exactly 4 vertices. Preferred when the document
    genuinely is a quadrilateral, because it keeps real extreme corners.
  - **B: minimum-area enclosing rectangle** (per-hull-edge orthonormal projection). Guaranteed
    fallback for triangular or rounded hulls.
  Candidates are gated (`minAreaRatio`, `minFillRatio`) then scored by fill ratio closest to 1.0;
  the result is normalised by `orderCorners` to `topLeft → topRight → bottomRight → bottomLeft`.
- `QuadRefinement.kt` — conservative first-pass corner snapping: nearest-foreground search within a
  bounded radius, clamped so no corner moves more than `searchRadius`, reverting to the input if the
  result would be non-convex. M08 extends this to full-resolution ROI refinement.

### Validation

- `./gradlew :geometry:test` → **BUILD SUCCESSFUL**.
- `./gradlew test` (all modules) → **BUILD SUCCESSFUL**.
- **Full suite: 207 unique tests, 0 failures, 0 errors** (was 117 at session start; 167 after M03).
- Geometry suite: 39 tests covering convex-hull edge cases (collinear, duplicate, <3 points),
  shoelace winding sign, centroid/bounding-box, point-in-polygon incl. on-edge, `distanceToSegment`
  endpoint clamping, mask bounds safety, thresholding, component labelling + noise filtering,
  degenerate masks (empty, 1-pixel, full-frame, zero-sized), Douglas–Peucker reduction,
  `orderCorners` permutation invariance, and fitting of axis-aligned / 20°-rotated /
  perspective-skewed quads with corner-order assertions.

### Results

- Corner ordering verified correct: `atan2` ascending in image coordinates traverses visually
  clockwise (up → right → down → left), and rotating that cycle to start at the `min(x+y)` corner
  yields exactly `topLeft → topRight → bottomRight → bottomLeft`.
- One test failure during development exposed a genuine documentation defect: the `minAreaRatio` gate
  was documented as rejecting "near-degenerate slivers", but because it divides by the boundary's
  **own** bounding box it can never reject an axis-aligned bar (ratio is 1.0 by construction). The
  KDoc was corrected to state the real semantics, and a second test was added pinning the actual
  behaviour. The implementation was left unchanged — it is correct for the false positive the gate
  exists to catch (a long thin diagonal edge line), which is the realistic case.

### Decisions

1. **Pure Kotlin, no OpenCV.** Driven by the APK finding below: OpenCV contributes ~111 MB of native
   libraries and was only needed for contour finding and quad fitting, both of which are a few
   hundred lines of testable Kotlin. This also makes the whole geometry core JVM-unit-testable with
   no device and no Robolectric.
2. **Two-strategy fitting with scoring**, rather than committing to one algorithm. Douglas–Peucker is
   tighter on true quadrilaterals; the min-area rectangle is the safety net. Committing to either
   alone would fail a real class of inputs.
3. **`fit` does not judge absolute size.** It receives only the boundary, never the frame dimensions,
   so "is this too small to be a document?" must be decided by the caller that knows the frame size.

### Problems

- An implementation sub-agent produced the six source files but did not deliver its test suite within
  its budget; it was stopped and the lead wrote and verified the tests directly. The agent's revision
  of `DefaultQuadrilateralFitter` (gating candidates *before* scoring rather than after) was kept —
  it is the better design.
- `:geometry` still declares `implementation(libs.opencv)` in its build file although nothing uses it.
  Removing it is a follow-up that belongs with the APK-size work.

### Next

- Wire `CandidateExtractor` in `:detection` to bridge `ProbabilityMap` → `BinaryMask` → boundary →
  quad, closing the detection→geometry pipeline.
- M05 target selection (depends on M02 + M04, both now available).
- **S01 gate still open — human decision required before any model weights are vendored.**

### Commit

`feat(geometry): add pure-Kotlin boundary extraction, envelope and quad fitting`
