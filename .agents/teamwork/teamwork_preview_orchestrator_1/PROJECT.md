# Project: yScanner (LocalScan) Native Android Document Scanner

## Architecture
- **Paradigm**: Offline-first, reactive, clean multi-module architecture with strict inward-pointing dependencies.
- **Pure Kotlin Domain**: `:domain` module contains zero Android dependencies, enabling rapid JVM testing of all business logic, state machines, and mathematical representations.
- **Module Boundaries**:
  - `:common`: Primitive utilities, matrix math, concurrency dispatchers, logging wrappers.
  - `:domain`: Core domain models (`PageObject`, `ScanSession`, `DocumentCandidate`, `Corner`, `Quad`, `ProcessingMode`, `PdfExportSettings`, etc.) and repository interfaces.
  - `:test-fixtures`: Shared test doubles, synthetic test image generators, golden image corpus, and assertions.
  - `:geometry`: 7-space coordinate transformation engine (`CoordinateTransformer`), outer envelope fitting, quadrilateral extraction, and ROI corner refinement.
  - `:detection`: Document segmentation model inference, candidate extraction, tap-to-guide selection, temporal tracking (One Euro Filter), and auto-capture readiness engine.
  - `:processing`: Perspective homography rectification, image enhancement (`Natural`, `Clean`, `Original`), and Two-Page book spread dewarping.
  - `:camera`: CameraX integration (`Preview`, `ImageAnalysis`, `ImageCapture`), flash control, tap-to-metering, and frame lifecycle management.
  - `:data`: Room database persistence, metadata storage, app-private file storage management, and session state machine.
  - `:pdf`: Streaming PDF generator ($O(1)$ memory page-by-page rendering), 6 page sizes, 3 quality profiles, and file size estimation.
  - `:import`: Gallery image ingestion and normalization pipeline.
  - `:app`: Android application entry point, dependency injection, navigation, and Jetpack Compose functional UI.

- **Dependency Graph**:
  ```text
  :app ──────► :camera, :detection, :geometry, :processing, :data, :pdf, :import, :domain, :common
  :camera ───► :geometry, :domain, :common
  :detection ─► :geometry, :domain, :common
  :processing ► :geometry, :domain, :common
  :geometry ──► :domain, :common
  :pdf ──────► :processing, :domain, :common
  :import ───► :processing, :domain, :common
  :data ─────► :domain, :common
  :domain ───► :common (ZERO Android framework imports)
  :test-fixtures ──► :domain, :common
  ```

- **Coordinate System (7 Spaces)**:
  1. Sensor Space (raw sensor pixels)
  2. ImageAnalysis Space (buffer resolution, typically 640x480 or 1280x720)
  3. Preview/View Space (UI screen coordinates, handling Fill/Fit center and letterboxing)
  4. Normalized Space ([0, 1] relative coordinates independent of aspect ratio)
  5. ImageCapture Space (full resolution JPEG/DNG coordinates, e.g., 4000x3000)
  6. Processed Space (rectified document pixels after homography)
  7. PDF Space (72 DPI points based on chosen page size, e.g., 595x842 for A4)

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | F01: Toolchain & SDK Setup | Android SDK 35 platform, JDK 21, Gradle wrapper 8.11.1 bootstrap | M1 | ORIGINAL_REQUEST §R1 |
| 2 | F02: Version Catalog | `gradle/libs.versions.toml` defining AGP, Kotlin 2.0, Compose, CameraX, Room, OpenCV | M1 | ORIGINAL_REQUEST §R1 |
| 3 | F03: Multi-Module Structure | 11 modules with correct Gradle KTS configuration and inward dependencies | M1 | ORIGINAL_REQUEST §R1 |
| 4 | F04: Core Domain Entities | Pure Kotlin models (`PageObject`, `ScanSession`, `Quad`, `Corner`, `PointF`) | M1 | ARCHITECTURE §5 |
| 5 | F05: CameraX Preview | Lifecycle-aware camera preview with PreviewView binding | M2 | ORIGINAL_REQUEST §R2 |
| 6 | F06: ImageAnalysis Pipeline | Latest-frame strategy, zero queue buildup, immediate ImageProxy closure | M2 | ORIGINAL_REQUEST §R2 |
| 7 | F07: ImageCapture Quality | File-backed JPEG capture with correct EXIF orientation | M2 | ORIGINAL_REQUEST §R2 |
| 8 | F08: Flash Control | Off / On / Torch modes with persistence across sessions | M2 | ORIGINAL_REQUEST §R2 |
| 9 | F09: Tap-to-Meter | Focus and metering point triggered by user tap | M2 | ORIGINAL_REQUEST §R2 |
| 10 | F10: 7 Coordinate Spaces | Complete affine/projective transformation pipeline | M2 | ORIGINAL_REQUEST §R2 |
| 11 | F11: Aspect Ratio & Rotation | Handling 0/90/180/270 rotations and differing preview/capture ratios | M2 | ORIGINAL_REQUEST §R2 |
| 12 | F12: Coordinate Invariants | Bidirectional mapping between Normalized, Analysis, and Preview spaces | M2 | ARCHITECTURE §18 |
| 13 | F13: Document Segmentation | ML model inference on analysis frames with fallback detector | M2 | ORIGINAL_REQUEST §R2 |
| 14 | F14: Candidate Extraction | Extracting connected components and polygonal document boundaries | M2 | ORIGINAL_REQUEST §R2 |
| 15 | F15: Tap-to-Guide Selection | User tap prioritizes candidate document without triggering crop editor | M2 | ORIGINAL_REQUEST §R2 |
| 16 | F16: Multi-Document Tracking | Tracking multiple visible candidates simultaneously | M2 | ORIGINAL_REQUEST §R2 |
| 17 | F17: Temporal Jitter Reduction | One Euro Filter on quad corners across frames | M2 | ORIGINAL_REQUEST §R2 |
| 18 | F18: Target Locking | Hysteresis preventing candidate switching on minor confidence changes | M2 | ORIGINAL_REQUEST §R2 |
| 19 | F19: Live Overlay Rendering | Drawing tracking quadrilateral and confidence indicators on preview | M2 | PRD §3.5 |
| 20 | F20: Auto Capture Countdown | 2-second countdown triggering capture when geometry & readiness are stable | M2 | ORIGINAL_REQUEST §R2 |
| 21 | F21: Manual Shutter Override | Unconditional manual capture regardless of detection/tracking state | M2 | ORIGINAL_REQUEST §R2 |
| 22 | F22: Boundary Extraction | Contour tracing on document segmentation masks | M3 | ORIGINAL_REQUEST §R3 |
| 23 | F23: Outer Envelope Estimator | Convex hull / outer envelope preserving concave document corners | M3 | ORIGINAL_REQUEST §R3 |
| 24 | F24: Quadrilateral Fitting | Minimum-area bounding quad fitting for complex boundaries | M3 | ORIGINAL_REQUEST §R3 |
| 25 | F25: ROI Corner Refinement | High-resolution sub-pixel corner refinement using `BitmapRegionDecoder` | M3 | ORIGINAL_REQUEST §R3 |
| 26 | F26: Perspective Correction | 4-point homography warp producing front-facing rectangular document | M3 | ORIGINAL_REQUEST §R3 |
| 27 | F27: Blur Metric | Laplacian variance quality assessment | M3 | ORIGINAL_REQUEST §R3 |
| 28 | F28: Glare Metric | Specular reflection / saturation histogram analysis | M3 | ORIGINAL_REQUEST §R3 |
| 29 | F29: Shadow Metric | Local illumination gradient analysis | M3 | ORIGINAL_REQUEST §R3 |
| 30 | F30: Exposure Metric | Histogram clipping and mean luminance validation | M3 | ORIGINAL_REQUEST §R3 |
| 31 | F31: Original Mode | Pass-through non-destructive enhancement mode | M3 | ORIGINAL_REQUEST §R3 |
| 32 | F32: Natural Enhancement | Color preservation, mild contrast boost, content preservation | M3 | ORIGINAL_REQUEST §R3 |
| 33 | F33: Clean Enhancement | Shadow reduction, background whitening, faint pencil/ink retention | M3 | ORIGINAL_REQUEST §R3 |
| 34 | F34: Non-Destructive Storage | Enhancement parameters stored alongside source asset, re-renderable | M3 | ORIGINAL_REQUEST §R3 |
| 35 | F35: Book Spread Detection | Two Page mode detection of double-page spreads | M3 | ORIGINAL_REQUEST §R3 |
| 36 | F36: Gutter Detection | Binding crease / gutter line localization | M3 | ORIGINAL_REQUEST §R3 |
| 37 | F37: Curvature Dewarping | Non-linear grid dewarping for curved pages (not naive 50% split) | M3 | ORIGINAL_REQUEST §R3 |
| 38 | F38: Two Page Splitting | Splitting into two independent PageObjects in left-to-right order | M3 | ORIGINAL_REQUEST §R3 |
| 39 | F39: PageObject Entity | Authoritative representation holding source URI, geometry, enhancement | M4 | ORIGINAL_REQUEST §R4 |
| 40 | F40: Page Manager CRUD | Add, replace, retake, duplicate, delete pages | M4 | ORIGINAL_REQUEST §R4 |
| 41 | F41: Page Reordering | Drag/drop or programmatic page re-indexing | M4 | ORIGINAL_REQUEST §R4 |
| 42 | F42: Undo / Redo Stack | Command pattern for all page operations | M4 | ORIGINAL_REQUEST §R4 |
| 43 | F43: Incremental Room Persistence | Atomic database writes for every page modification | M4 | ORIGINAL_REQUEST §R4 |
| 44 | F44: File Storage Hierarchy | App-private storage for sources, thumbnails, and exports | M4 | ARCHITECTURE §38 |
| 45 | F45: Process Death Recovery | Session state reconstruction after OS process termination | M4 | ORIGINAL_REQUEST §R4 |
| 46 | F46: Gallery Image Ingestion | Importing external photos/PDFs with URI permission handling | M4 | ORIGINAL_REQUEST §R4 |
| 47 | F47: Unified Import Pipeline | Gallery images processed via identical geometry/enhancement pipeline | M4 | ORIGINAL_REQUEST §R4 |
| 48 | F48: Streaming PDF Renderer | Page-by-page streaming rendering with $O(1)$ memory consumption | M4 | ORIGINAL_REQUEST §R4 |
| 49 | F49: 6 PDF Page Sizes | A4 (default), Auto, A5, B5, Letter, Original Ratio | M4 | ORIGINAL_REQUEST §R4 |
| 50 | F50: 3 PDF Quality Profiles | High (100% res, 90% JPEG), Balanced (75% res, 80%), Small (50%, 65%) | M4 | ORIGINAL_REQUEST §R4 |
| 51 | F51: File Size Estimator | Pre-export PDF size estimation accurate within 20% | M4 | ORIGINAL_REQUEST §R4 |
| 52 | F52: Editable Filename | Custom document naming with sanitize-on-export rules | M4 | ORIGINAL_REQUEST §R4 |
| 53 | F53: Camera Screen UI | Functional Jetpack Compose camera UI with live overlay and controls | M4 | PRD §3.5 |
| 54 | F54: Review & Export Screen UI | Functional Compose UI for page gallery, crop editing, and PDF export | M4 | PRD §3.5 |
| 55 | F55: Normal Memory Budget | <= 600-700 MB normal working memory on 8 GB devices | M5 | ORIGINAL_REQUEST §R5 |
| 56 | F56: Peak Memory Budget | <= 800 MB absolute peak memory during full-resolution processing | M5 | ORIGINAL_REQUEST §R5 |
| 57 | F57: 20-Page Session Stability | Zero unbounded memory growth across 20-page scan sessions | M5 | ORIGINAL_REQUEST §R5 |
| 58 | F58: Native OpenCV Mat Cleanup | Explicit release of all native OpenCV memory via try/finally | M5 | ORIGINAL_REQUEST §R5 |
| 59 | F59: Offline Privacy Invariant | 100% local processing with zero network calls and no leaks | M5 | ORIGINAL_REQUEST §R5 |
| 60 | F60: Golden Regression Suite | E2E test verification across diverse lighting, angles, and documents | M5 | ORIGINAL_REQUEST §R5 |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Project Foundation & Build System | Toolchain (SDK 35), Version Catalog, 11 modules, pure Kotlin domain models, Room & matrix foundations | none | PLANNED |
| M2 | Camera & Real-Time Detection Pipeline | CameraX preview/analysis/capture, 7 coordinate spaces, ML segmentation, temporal tracking, auto capture | M1 | PLANNED |
| M3 | Document Geometry & Image Processing | Outer envelope, quad fitting, ROI corner refinement, perspective homography, enhancement, Two Page dewarping | M1 | PLANNED |
| M4 | Domain State, Persistence, Gallery & PDF | Page Manager CRUD/undo/redo, Room session persistence, process death recovery, gallery import, streaming PDF, Compose UI | M2, M3 | PLANNED |
| M5 | Quality Hardening & Acceptance Verification | E2E test suite pass (Tiers 1-4), adversarial testing (Tier 5), memory profiling (≤600-800 MB), offline privacy audit | M4 | PLANNED |

## Parallel Track: E2E Testing
- **Orchestrator**: `teamwork_preview_orchestrator` (E2E Testing Track)
- **Scope**: Requirements-driven, opaque-box test suite covering Tiers 1-4 (Features, Boundaries, Pairwise, Real-World Workloads)
- **Status**: PLANNED

## Interface Contracts

### `:common` & `:domain`
```kotlin
package com.localscan.domain.model

data class PointF(val x: Float, val y: Float)

data class Corner(val x: Float, val y: Float, val confidence: Float = 1.0f)

data class Quad(
    val topLeft: Corner,
    val topRight: Corner,
    val bottomRight: Corner,
    val bottomLeft: Corner
) {
    fun toArray(): FloatArray
    fun isConvex(): Boolean
    fun area(): Float
}

enum class EnhancementMode { ORIGINAL, NATURAL, CLEAN }
enum class FlashMode { OFF, ON, TORCH }
enum class CaptureMode { ONE_PAGE, TWO_PAGE }
enum class PageSize { A4, AUTO, A5, B5, LETTER, ORIGINAL_RATIO }
enum class QualityProfile(val compressionQuality: Int, val maxDimension: Int?) {
    HIGH(90, null),
    BALANCED(80, 2048),
    SMALL(65, 1280)
}

data class PageObject(
    val id: String,
    val sessionId: String,
    val pageIndex: Int,
    val sourceImageUri: String,
    val detectedQuad: Quad,
    val userQuad: Quad? = null,
    val rotationDegrees: Int = 0,
    val enhancementMode: EnhancementMode = EnhancementMode.NATURAL,
    val isDewarped: Boolean = false,
    val qualityMetrics: QualityMetrics? = null
)
```

### `:geometry` ↔ `:camera` & `:detection`
```kotlin
package com.localscan.geometry

interface CoordinateTransformer {
    fun mapPoint(point: PointF, from: CoordinateSpace, to: CoordinateSpace): PointF
    fun mapQuad(quad: Quad, from: CoordinateSpace, to: CoordinateSpace): Quad
    fun updateTransform(from: CoordinateSpace, to: CoordinateSpace, matrix: android.graphics.Matrix)
}
```

### `:detection` ↔ `:camera`
```kotlin
package com.localscan.detection

interface DocumentDetector {
    suspend fun detect(imageProxy: androidx.camera.core.ImageProxy): List<DocumentCandidate>
}

interface TemporalTracker {
    fun update(candidates: List<DocumentCandidate>, tapPrior: PointF?): TrackedDocument?
    fun reset()
}
```

### `:processing` ↔ `:domain`
```kotlin
package com.localscan.processing

interface ImageProcessor {
    suspend fun rectify(sourceUri: String, quad: Quad): android.graphics.Bitmap
    suspend fun enhance(bitmap: android.graphics.Bitmap, mode: EnhancementMode): android.graphics.Bitmap
    suspend fun dewarpBookSpread(sourceUri: String): Pair<android.graphics.Bitmap, android.graphics.Bitmap>
}
```

### `:pdf` ↔ `:domain`
```kotlin
package com.localscan.pdf

interface PdfRenderer {
    suspend fun render(
        pages: List<PageObject>,
        outputFile: java.io.File,
        pageSize: PageSize,
        qualityProfile: QualityProfile,
        onProgress: (Int, Int) -> Unit
    ): Long // returns generated file size in bytes

    fun estimateSize(pages: List<PageObject>, pageSize: PageSize, qualityProfile: QualityProfile): Long
}
```

## Code Layout
```text
d:\Projects\pdfscanner\
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── app/
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/localscan/app/
├── common/
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/localscan/common/
├── domain/
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/localscan/domain/
├── test-fixtures/
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/localscan/testing/
├── geometry/
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/localscan/geometry/
├── detection/
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/localscan/detection/
├── processing/
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/localscan/processing/
├── camera/
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/localscan/camera/
├── data/
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/localscan/data/
├── pdf/
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/localscan/pdf/
└── import/
    ├── build.gradle.kts
    └── src/main/kotlin/com/localscan/import/
```
