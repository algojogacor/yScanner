ARCHITECTURE.md

# yScanner — System Architecture

Status: Technical Architecture Baseline

Platform:
Native Android

Primary stack:

* Kotlin
* Jetpack Compose
* CameraX
* OpenCV
* LiteRT / TFLite-compatible on-device ML
* Local PDF generation
* Coroutines / structured concurrency

Design scope:
Visual design is intentionally outside this document.

---

# 1. ARCHITECTURE OBJECTIVE

yScanner harus dibangun sebagai scanner pipeline yang modular, testable, memory-conscious, dan dapat dikembangkan secara bertahap.

Arsitektur harus memisahkan:

* camera acquisition;
* real-time detection;
* target selection;
* temporal tracking;
* document geometry;
* image processing;
* enhancement;
* page/document domain model;
* persistence;
* PDF rendering;
* gallery import.

Tujuan utama arsitektur:

1. Real-time camera pipeline tetap ringan.
2. Full-resolution processing tidak membebani RAM secara tidak perlu.
3. AI model dapat diganti tanpa merombak seluruh aplikasi.
4. Geometry engine dapat dikembangkan dan diuji secara independen.
5. PageObject menjadi source of truth untuk document editing.
6. PDF export tidak bergantung langsung pada camera layer.
7. UI tidak mengetahui detail OpenCV, ML runtime, atau PDF implementation.
8. Processing pipeline dapat diuji tanpa kamera fisik.
9. Setiap subsystem mempunyai ownership dan boundary yang jelas.
10. Repository tetap maintainable ketika fitur bertambah.

---

# 2. ARCHITECTURE PRINCIPLES

## 2.1. Separation of Concerns

Setiap subsystem hanya bertanggung jawab terhadap domainnya.

Camera tidak boleh mengetahui bagaimana PDF dibuat.

PDF renderer tidak boleh mengetahui bagaimana camera bekerja.

UI tidak boleh langsung menjalankan OpenCV.

ML detector tidak boleh langsung memodifikasi PageObject persistence.

---

## 2.2. Dependencies Point Inward

Dependency direction harus secara umum mengikuti:

```text
UI
 ↓
Application / Use Cases
 ↓
Domain
 ↓
Infrastructure / Implementations
```

Infrastructure tidak boleh memaksa domain bergantung pada framework tertentu.

Contoh:

```text
PageObject
```

tidak boleh bergantung kepada:

* Compose;
* CameraX;
* OpenCV;
* Android View;
* PDF library.

---

## 2.3. Source of Truth

Source of truth berbeda untuk setiap tahap:

Camera:
→ source image/file

Detection:
→ detection result

Geometry:
→ PageGeometry

Processing:
→ processing parameters + source asset

Document editing:
→ PageObject

PDF:
→ generated artifact

Rendered bitmap bukan source of truth.

---

## 2.4. AI Is Replaceable

ML model merupakan implementation detail dari document detection.

Architecture tidak boleh menganggap:

"model X adalah satu-satunya model yang mungkin digunakan."

Model dapat diganti selama interface dan semantic output tetap kompatibel.

---

## 2.5. Real-Time dan Offline Processing Terpisah

Real-time pipeline:

```text
Camera
→ ImageAnalysis
→ AI
→ target selection
→ tracking
→ overlay
```

Final pipeline:

```text
Captured file
→ geometry refinement
→ processing
→ PageObject
```

Kedua pipeline memiliki kebutuhan performa yang berbeda.

---

## 2.6. User Control

Tidak ada subsystem yang boleh secara otomatis membatalkan keputusan user.

Quality assessment hanya memberi informasi/score kepada application layer.

Ia tidak memiliki otoritas untuk:

* memblokir shutter;
* menghapus capture;
* memaksa retake.

---

# 3. HIGH-LEVEL SYSTEM

```text
                           PRESENTATION
                                │
                                ▼
                         Camera / Pages / Export UI
                                │
                                ▼
                        APPLICATION LAYER
                                │
             ┌──────────────────┼──────────────────┐
             │                  │                  │
             ▼                  ▼                  ▼
        Scan Session       Page Management      PDF Export
             │                  │                  │
             └──────────────────┼──────────────────┘
                                ▼
                           DOMAIN MODEL
                                │
             ┌──────────────────┼──────────────────┐
             ▼                  ▼                  ▼
         Camera Flow       Processing Flow     Persistence
             │                  │                  │
             ▼                  ▼                  ▼
          CameraX       Geometry / Enhancement   Assets
                                │
                    ┌───────────┴───────────┐
                    ▼                       ▼
                 ML Layer              OpenCV Layer
                    │                       │
                    └───────────┬───────────┘
                                ▼
                         Local File Storage
```

---

# 4. RECOMMENDED MODULE STRUCTURE

Logical module structure:

```text
yScanner/
│
├── app/
│
├── core/
│   ├── common/
│   ├── image/
│   ├── math/
│   ├── logging/
│   └── concurrency/
│
├── camera/
│   ├── api/
│   └── implementation/
│
├── detection/
│   ├── api/
│   ├── model/
│   ├── candidate/
│   └── tracking/
│
├── geometry/
│   ├── api/
│   ├── singlepage/
│   ├── twopage/
│   ├── refinement/
│   └── transform/
│
├── processing/
│   ├── quality/
│   ├── natural/
│   ├── clean/
│   └── render/
│
├── domain/
│   ├── document/
│   ├── page/
│   ├── geometry/
│   └── session/
│
├── data/
│   ├── repository/
│   ├── storage/
│   └── persistence/
│
├── pdf/
│
├── import/
│
├── feature/
│   ├── scanner/
│   ├── pageeditor/
│   ├── pagemanager/
│   └── export/
│
├── benchmark/
│
└── testdata/
```

The exact Gradle module count may differ.

Do not create a large number of Gradle modules merely for organization.

Logical boundaries are mandatory; physical module separation should be introduced where it provides real isolation.

---

# 5. LAYER RESPONSIBILITIES

## 5.1. Presentation Layer

Responsible for:

* Compose UI;
* rendering state;
* user input;
* displaying scanner state;
* displaying page previews;
* invoking use cases.

Must NOT:

* perform OpenCV operations;
* directly call the ML runtime;
* manipulate CameraX internals;
* directly write PDF files.

Presentation communicates through application/domain APIs.

---

## 5.2. Application Layer

Responsible for orchestrating workflows.

Examples:

* StartScanSession
* CapturePage
* ProcessCapturedPage
* ReplacePage
* DeletePage
* ReorderPages
* ApplyEnhancement
* ExportPdf
* ImportImage

Application layer coordinates subsystems but should not contain low-level image-processing algorithms.

---

## 5.3. Domain Layer

Contains business concepts independent of Android UI/framework implementation.

Core entities:

* Document
* PageObject
* ScanSession
* PageGeometry
* EnhancementParams
* QualityMetrics
* PdfExportOptions

Domain layer should remain lightweight and serializable where practical.

---

## 5.4. Infrastructure Layer

Contains framework-dependent implementations:

* CameraX;
* LiteRT;
* OpenCV;
* Android filesystem;
* Android MediaStore;
* PDF implementation;
* device-specific delegates.

Infrastructure implements interfaces expected by application/domain layers.

---

# 6. CAMERA ARCHITECTURE

Camera subsystem:

```text
CameraController
│
├── PreviewUseCase
├── ImageAnalysisUseCase
├── ImageCaptureUseCase
└── MeteringController
```

Responsibilities:

### CameraController

* bind/unbind CameraX use cases;
* manage lifecycle;
* expose camera state.

### PreviewUseCase

* provide live preview.

### ImageAnalysisUseCase

* provide analysis frames;
* maintain latest-frame behavior;
* send frames to detector.

### ImageCaptureUseCase

* capture final image;
* save source file.

### MeteringController

* tap-to-focus;
* exposure metering;
* focus state.

Camera layer must not contain document geometry logic.

---

# 7. CAMERA DATA FLOW

```text
CameraX
   │
   ├── Preview
   │
   ├── ImageAnalysis
   │       │
   │       ▼
   │   AnalysisFrame
   │       │
   │       ▼
   │   DocumentDetector
   │
   └── ImageCapture
           │
           ▼
      SourceAsset
```

`AnalysisFrame` must be lightweight.

It should not force unnecessary Bitmap allocation.

---

# 8. IMAGEANALYSIS FRAME OWNERSHIP

The analysis frame has strict ownership.

Flow:

```text
CameraX ImageProxy
        ↓
convert/access necessary data
        ↓
detector
        ↓
release ImageProxy
```

`ImageProxy` must be closed promptly.

Do not pass an `ImageProxy` deep into long-running pipelines unless ownership is explicit and safe.

Preferred architecture:

```text
ImageProxy
→ short-lived adapter
→ model input
→ release
```

---

# 9. REAL-TIME DETECTION PIPELINE

```text
AnalysisFrame
    ↓
SegmentationModel
    ↓
SegmentationMask
    ↓
BoundaryExtraction
    ↓
OuterEnvelope
    ↓
CandidateQuadrilateral
    ↓
CandidateSelector
    ↓
TargetTracker
    ↓
StableGeometry
```

Real-time geometry should remain computationally lightweight.

Final geometry refinement occurs only after capture.

---

# 10. DETECTION MODEL ARCHITECTURE

Interfaces:

```kotlin
interface DocumentDetector {
    suspend fun detect(frame: AnalysisFrame): DetectionResult
}

interface SegmentationModel {
    suspend fun infer(input: ModelInput): ModelOutput
}
```

Expected relationship:

```text
DocumentDetector
      ↓
SegmentationModel
      ↓
ModelOutput
      ↓
Geometry preprocessing
      ↓
DetectionResult
```

Do not make UI depend directly on `SegmentationModel`.

---

# 11. MODEL RUNTIME ABSTRACTION

Potential implementation:

```text
SegmentationModel
├── LiteRTCpuModel
├── LiteRtAcceleratedModel
└── ExperimentalModel
```

Selection policy belongs to infrastructure/application initialization, not UI.

Hardware acceleration must be optional.

Fallback:

```text
accelerated runtime unavailable
→ CPU implementation
```

---

# 12. CANDIDATE MODEL

A detection frame may contain multiple candidates.

Conceptual:

```kotlin
data class DocumentCandidate(
    val id: CandidateId,
    val maskRegion: Region,
    val envelope: Quad,
    val confidence: Float,
    val coverage: Float,
    val quality: CandidateQuality
)
```

Candidate IDs are logical/temporal identities, not raw detector indexes.

Raw detector order must not determine persistent target identity.

---

# 13. TARGET SELECTION ARCHITECTURE

```text
DocumentCandidates
        │
        ▼
TargetSelector
        │
        ├── TapPrior
        ├── ExistingTarget
        ├── Geometry
        ├── Stability
        ├── Coverage
        └── Confidence
        │
        ▼
SelectedCandidate
```

Once a candidate is selected, `TargetTracker` maintains continuity across frames.

A raw confidence spike from another candidate must not immediately cause a target switch.

---

# 14. TEMPORAL TRACKER

Tracker responsibilities:

* target identity;
* temporal continuity;
* corner smoothing;
* confidence smoothing;
* dropout tolerance.

Conceptual interface:

```kotlin
interface TargetTracker {
    fun update(
        candidates: List<DocumentCandidate>,
        timestampNanos: Long
    ): TrackingResult
}
```

Possible internal algorithms:

* exponential smoothing;
* One Euro Filter;
* Kalman-like filter;
* hybrid approach.

The algorithm is implementation detail.

Selection must be based on benchmark results.

---

# 15. TRACKER STATE MACHINE

```text
SEARCHING
    ↓
CANDIDATE
    ↓
TRACKING
    ↓
STABLE
    ↓
CAPTURE_READY
    ↓
CAPTURED
```

State transitions should be deterministic and testable.

A temporary detector dropout must not immediately cause:

```text
STABLE → SEARCHING
```

unless the configured loss threshold is reached.

---

# 16. TAP-TO-GUIDE DATA FLOW

```text
User Tap
   │
   ├── Preview coordinate
   │
   ▼
Coordinate Mapper
   │
   ├── candidate hit test
   │
   ▼
TargetSelector
   │
   ▼
Target Lock
```

In parallel:

```text
User Tap
   ↓
Preview/Camera coordinate
   ↓
CameraX Metering Point
   ↓
Focus / Exposure
```

Tap-to-guide therefore affects both:

1. target selection;
2. camera metering.

---

# 17. LIVE OVERLAY ARCHITECTURE

Overlay receives only a stable geometry/state representation.

Example:

```kotlin
data class OverlayState(
    val quad: Quad?,
    val confidence: Float,
    val trackingState: TrackingState
)
```

Overlay must not know:

* segmentation model internals;
* OpenCV APIs;
* camera sensor coordinates.

The presentation layer receives already-transformed display coordinates.

---

# 18. COORDINATE SYSTEM ARCHITECTURE

yScanner has multiple coordinate systems:

```text
1. Camera sensor coordinates
2. ImageAnalysis coordinates
3. Preview/View coordinates
4. Normalized coordinates
5. ImageCapture coordinates
6. Processed-image coordinates
7. PDF coordinates
```

Never mix these implicitly.

Create explicit types/wrappers where practical.

Example:

```text
AnalysisPoint
PreviewPoint
SensorPoint
CapturePoint
ProcessedPoint
PdfPoint
```

Avoid passing generic `PointF` everywhere without knowing which coordinate system it belongs to.

---

# 19. COORDINATE TRANSFORMATION PIPELINE

```text
AnalysisPoint
      ↓
CameraX Transformation
      ↓
Sensor / Normalized Geometry
      ↓
CapturePoint
      ↓
Local Geometry Refinement
      ↓
ProcessedGeometry
      ↓
PDF Layout
      ↓
PdfGeometry
```

Coordinate transformation is a dedicated service:

```kotlin
interface CoordinateMapper
```

It must account for:

* image rotation;
* crop rectangle;
* sensor orientation;
* viewport;
* aspect ratio;
* output resolution.

---

# 20. FULL-RESOLUTION CAPTURE PIPELINE

The live quad is only an initial estimate.

After capture:

```text
SourceAsset
    ↓
Capture metadata
    ↓
mapped live geometry
    ↓
local ROI extraction
    ↓
edge/boundary refinement
    ↓
corner refinement
    ↓
Final PageGeometry
```

The system should preserve enough surrounding area around the estimated quad to recover from small detection errors.

---

# 21. DOCUMENT GEOMETRY ENGINE

The geometry engine is a core subsystem.

```text
DocumentGeometryEngine
│
├── BoundaryExtractor
├── OuterEnvelopeEstimator
├── QuadrilateralFitter
├── CornerRefiner
├── EdgeRefiner
├── PerspectiveEstimator
├── SinglePageProcessor
│
├── BookSpreadDetector
├── PageBoundaryDetector
├── GutterDetector
├── CurvatureEstimator
├── DewarpEngine
└── CoordinateMapper
```

Geometry code must not depend on Compose.

---

# 22. OUTER ENVELOPE

Pipeline:

```text
SegmentationMask
    ↓
Boundary
    ↓
Noise cleanup
    ↓
Outer envelope
    ↓
Quadrilateral fitting
```

The envelope is the physical boundary abstraction used to derive the scanner crop.

---

# 23. COMPLEX / CONCAVE GEOMETRY

Normal output is an enclosing quadrilateral.

For complex shapes:

```text
document mask
→ outermost visible geometry
→ enclosing envelope
→ four-sided representation
```

Do not switch to arbitrary polygon cropping as the default.

Background inside the enclosing quadrilateral is acceptable when required by the product geometry rule.

---

# 24. SINGLE PAGE PIPELINE

```text
SourceAsset
    ↓
Boundary / geometry estimation
    ↓
Outer envelope
    ↓
Quadrilateral
    ↓
Corner refinement
    ↓
Homography
    ↓
Rectified image
    ↓
Quality assessment
    ↓
Enhancement
    ↓
PageObject
```

Single-page geometry should remain independent of PDF page size.

---

# 25. TWO PAGE PIPELINE

Two Page processing is a separate geometry path.

```text
SourceAsset
    ↓
BookSpreadDetector
    ↓
Left/Right Page Boundaries
    ↓
GutterDetector
    ↓
CurvatureEstimator
    ↓
DewarpMesh
    ↓
Dewarp
    ↓
Page Separation
    ↓
Perspective Rectification
    ↓
Quality Assessment
    ↓
Enhancement
    ↓
PageObject Left
PageObject Right
```

Do not implement Two Page as:

```text
wide crop
→ split at 50%
```

except as a temporary experimental fallback.

---

# 26. DEWARP ARCHITECTURE

Dewarp is a geometry transformation.

It should be represented as a mapping, not merely a visual filter.

Conceptually:

```text
source image
    +
surface model
    ↓
dewarp mesh
    ↓
nonlinear remap
    ↓
flattened page
```

`DewarpEngine` should receive geometry information and return a transformed image/result.

It should not know about:

* Page Manager;
* PDF;
* Compose.

---

# 27. QUALITY ASSESSMENT ARCHITECTURE

Quality should be a pure analysis service.

Conceptual interface:

```kotlin
interface QualityAssessor {
    suspend fun assess(
        image: ProcessedImage,
        geometry: PageGeometry
    ): QualityMetrics
}
```

Metrics:

```text
blur
glare
shadow
exposure
geometry
corner confidence
coverage
```

The assessor returns information.

It does not decide whether the user is allowed to continue.

---

# 28. AUTO CAPTURE DECISION ARCHITECTURE

Auto Capture should depend on live readiness rather than post-capture quality.

```text
TrackingResult
+
LiveQualityMetrics
+
TargetState
        ↓
CaptureReadinessEvaluator
        ↓
CaptureReady / NotReady
```

Then:

```text
CaptureReady
→ 2-second countdown
→ ImageCapture
```

The shutter is never disabled because readiness is false.

---

# 29. ENHANCEMENT ARCHITECTURE

Common interface:

```kotlin
interface ImageEnhancer {
    suspend fun render(
        source: ProcessedImage,
        params: EnhancementParams
    ): RenderedImage
}
```

Implementations:

```text
NaturalEnhancer
CleanEnhancer
```

Enhancers do not modify source assets.

---

# 30. NON-DESTRUCTIVE RENDERING

Concept:

```text
SourceAsset
     +
PageGeometry
     +
Rotation
     +
EnhancementParams
     ↓
RenderPipeline
     ↓
RenderedPage
```

The rendered image is derived.

Changing:

```text
Natural
→ Clean
```

should only change parameters and regenerate the derived result.

---

# 31. RENDER CACHE

Optional derived cache:

```text
PageObject
    ↓
RenderKey
    ↓
Cache
```

Render key can be derived from:

* source version;
* geometry version;
* rotation;
* enhancement parameters;
* renderer version.

If cache becomes invalid:

```text
delete cache
→ regenerate
```

Never treat cache as permanent source data.

---

# 32. DOMAIN MODEL

Core domain:

```text
Document
│
├── id
├── title
├── pages[]
├── export preferences
└── metadata
```

```text
PageObject
│
├── id
├── sourceAsset
├── geometry
├── rotation
├── enhancementParams
├── qualityMetrics
└── metadata
```

```text
ScanSession
│
├── sessionId
├── documentId
├── currentPage
├── pages
├── state
└── timestamps
```

---

# 33. PAGE GEOMETRY MODEL

Geometry should be represented explicitly.

Potential hierarchy:

```text
PageGeometry
│
├── SinglePageGeometry
├── TwoPageGeometry
│   ├── LeftPageGeometry
│   └── RightPageGeometry
└── ManualCropGeometry
```

Geometry may contain:

* source coordinate space;
* quadrilateral;
* crop bounds;
* perspective parameters;
* dewarp information;
* processing version.

Geometry is data, not an image.

---

# 34. PAGE MANAGER ARCHITECTURE

Page Manager operates on the Document domain model.

Operations:

```text
addPage
deletePage
duplicatePage
reorderPage
replacePage
updateGeometry
updateRotation
updateEnhancement
undo
redo
```

Page Manager should not know how pixels are processed internally.

It changes state.

Processing services render the consequences of that state.

---

# 35. UNDO / REDO ARCHITECTURE

Use command/state-based editing rather than storing a full Bitmap for every edit.

Example:

```text
Initial PageObject
      ↓
Rotate
      ↓
Crop
      ↓
Clean
      ↓
Manual crop
```

Undo reverses domain state.

Do not create:

```text
Bitmap v1
Bitmap v2
Bitmap v3
Bitmap v4
```

for every edit.

---

# 36. SESSION PERSISTENCE

Session repository:

```kotlin
interface ScanSessionRepository {
    suspend fun create(): ScanSession
    suspend fun get(id: SessionId): ScanSession?
    suspend fun save(session: ScanSession)
    suspend fun delete(id: SessionId)
}
```

Source assets use a separate repository:

```kotlin
interface AssetRepository {
    suspend fun save(...)
    suspend fun open(...)
    suspend fun delete(...)
}
```

Metadata and binary assets should not be tightly coupled.

---

# 37. STORAGE MODEL

Suggested:

```text
internal app storage
│
├── sessions/
│   └── <session-id>/
│
│       ├── metadata
│       ├── sources/
│       ├── derived/
│       └── temp/
│
└── exports/
```

Temporary and derived files must be distinguishable.

Final PDF exports are separate artifacts.

Actual storage implementation should follow Android's current storage model appropriate to the selected minimum/target SDK.

---

# 38. ASSET LIFECYCLE

Source:

```text
capture/import
→ source asset
→ retained while referenced by PageObject
```

Derived:

```text
source + parameters
→ derived cache
→ disposable
```

Final PDF:

```text
PageObjects
→ export
→ final user file
```

Never delete a source asset while any PageObject still references it.

---

# 39. IMPORT ARCHITECTURE

Gallery import must enter the processing domain at a similar point to captured images.

```text
Gallery
→ ImageImporter
→ SourceAsset
→ Geometry Pipeline
→ Processing
→ PageObject
```

Camera and import should differ primarily in acquisition, not core geometry behavior.

---

# 40. PDF ARCHITECTURE

PDF must depend on domain/page rendering, not CameraX.

```text
Document
   ↓
PdfExportOptions
   ↓
PageRenderer
   ↓
PdfWriter
   ↓
PDF file
```

`PdfWriter` should not know how a camera image was acquired.

---

# 41. PDF PAGE LAYOUT

Important separation:

```text
PageGeometry
→ processed page
→ PDF page layout
```

PDF page size must never feed back into:

```text
DocumentDetector
QuadrilateralFitter
CornerRefiner
```

Supported page-size abstraction:

```text
A4
Auto
A5
B5
Letter
OriginalRatio
```

---

# 42. PDF RENDERING MEMORY MODEL

PDF export must stream.

```text
PageObject 1
→ render
→ encode
→ write
→ release

PageObject 2
→ render
→ encode
→ write
→ release

...
```

At no time should the renderer intentionally retain all processed page bitmaps.

---

# 43. OUTPUT QUALITY ABSTRACTION

Quality:

```text
High
Balanced
Small
```

should map to renderer configuration.

Conceptually:

```kotlin
data class PdfRenderProfile(
    val targetResolution: Int,
    val imageQuality: Int,
    val compressionMode: CompressionMode
)
```

Actual values should be benchmarked.

Do not hard-code arbitrary quality numbers before benchmarking.

---

# 44. FILE SIZE ESTIMATION

The size estimator should be independent of final file writing.

Input:

```text
page count
processed dimensions
quality profile
compression configuration
PDF overhead estimate
```

Output:

```text
EstimatedFileSize
```

It must not require generating the complete PDF just to show the estimate.

Estimate is informational and does not have to exactly match the final file.

---

# 45. APPLICATION USE CASES

Recommended core use cases:

```text
StartScanSession
CapturePage
ProcessCapturedPage
ApplyManualCrop
ReplacePage
RetakePage
DeletePage
DuplicatePage
ReorderPages
RotatePage
ChangeEnhancement
ImportImage
ExportPdf
RecoverSession
```

These use cases coordinate components but do not own low-level image algorithms.

---

# 46. SCAN SESSION STATE MACHINE

Possible state:

```text
IDLE
  ↓
CAMERA_ACTIVE
  ↓
CAPTURING
  ↓
PROCESSING
  ↓
PAGE_READY
  ↓
CAMERA_ACTIVE
  ↓
...
  ↓
REVIEWING
  ↓
EXPORTING
  ↓
COMPLETED
```

Error/interrupt states must preserve data when possible.

---

# 47. PROCESSING STATE

A PageObject may have processing metadata such as:

```text
RECEIVED
GEOMETRY_PROCESSING
ENHANCEMENT_PROCESSING
READY
ERROR
```

Processing state must not cause source deletion.

If processing fails:

```text
source remains
→ error recorded
→ retry/reprocess allowed
```

---

# 48. CONCURRENCY ARCHITECTURE

Use structured concurrency.

Separate workload classes:

### Camera thread / CameraX

Short-lived camera operations.

### Detection executor

CPU/GPU inference.

### Geometry processing

CPU-heavy OpenCV work.

### Enhancement

CPU/native processing.

### PDF rendering

Sequential and memory-aware.

Do not let multiple large image-processing jobs execute concurrently without reason.

For example, scanning one page and exporting a 30-page PDF simultaneously should not create uncontrolled memory pressure.

---

# 49. CANCELLATION

Long-running tasks must support cancellation where practical.

Cancellation examples:

* user leaves editor;
* session deleted;
* export canceled;
* processing replaced;
* app lifecycle destroyed.

Cancellation must not leave partially written state that is mistaken for a valid finished page.

---

# 50. MEMORY OWNERSHIP

Every large object should have a clear owner.

Examples:

```text
ImageProxy
→ Camera analysis callback

Large processing buffer
→ processing task

Rendered page
→ current export operation

Source image
→ filesystem
```

Avoid global caches containing full-resolution images.

Use caches only for bounded, derived results.

---

# 51. NATIVE MEMORY

OpenCV and ML runtime may allocate memory outside the Java/Kotlin heap.

Therefore memory profiling must consider:

* Java heap;
* native heap;
* Bitmap memory;
* OpenCV buffers;
* ML tensors;
* model allocation;
* temporary files.

Do not consider only Kotlin object allocation when evaluating RAM.

---

# 52. MODEL MEMORY

The ML runtime should load the detector once per active scanner lifecycle where practical.

Do not repeatedly:

```text
load model
→ infer
→ unload
→ load model
```

for every frame.

At the same time, do not keep unnecessary models resident when the scanner is not active.

Model lifecycle should follow actual usage.

---

# 53. PERFORMANCE BOUNDARIES

Target behavior:

```text
Live:
camera + detection + tracking
→ low latency

Capture:
high quality
→ asynchronous processing

Export:
sequential
→ predictable memory
```

Do not allow final image processing to block the live camera unnecessarily.

After capture, the camera may continue for next-page workflow depending on the intended UX and available resources.

---

# 54. DEVICE CAPABILITY ABSTRACTION

Some devices may differ in:

* CPU;
* GPU;
* camera resolutions;
* supported flash behavior;
* hardware delegates;
* memory;
* Android version.

Do not hard-code assumptions that every device is identical.

Create capability queries where useful:

```text
DeviceCapabilities
├── camera features
├── flash
├── supported resolutions
├── hardware acceleration
└── memory class
```

---

# 55. IMAGE REPRESENTATION STRATEGY

Avoid passing Bitmap everywhere.

Prefer abstractions such as:

```text
SourceAsset
DecodedRegion
ProcessedImage
RenderedImage
```

This allows the implementation to change storage/decode strategy without changing every consumer.

Where possible:

```text
file
→ region decode
→ processing
```

instead of:

```text
file
→ giant Bitmap
→ giant Bitmap copy
→ giant Bitmap copy
```

---

# 56. REGION-OF-INTEREST PROCESSING

Full-resolution refinement should use ROI when practical.

Concept:

```text
estimated quad
    ↓
expanded ROI
    ↓
decode/process only necessary region
    ↓
refine geometry
```

The ROI must retain enough surrounding area to recover from small initial errors.

Do not crop the ROI so tightly that refinement becomes impossible.

---

# 57. IMAGE PROCESSING ORDER

Single-page baseline:

```text
source
→ geometry detection/refinement
→ perspective correction
→ quality assessment
→ enhancement
→ render
```

Do not perform aggressive enhancement before geometry refinement if enhancement could obscure useful edges.

Two Page:

```text
source
→ spread geometry
→ gutter
→ curvature
→ dewarp
→ split
→ perspective correction
→ quality assessment
→ enhancement
```

Specific order may be tuned through benchmark experiments.

---

# 58. ARCHITECTURAL BOUNDARIES FOR OPEN-CV

OpenCV should exist behind geometry/processing interfaces where practical.

Preferred:

```text
GeometryEngine
→ OpenCV implementation
```

rather than:

```text
ViewModel
→ Mat
→ Imgproc
→ warpPerspective
```

This makes the scanner testable without Compose and easier to replace later.

---

# 59. ARCHITECTURAL BOUNDARIES FOR ML

ML runtime should be encapsulated.

Preferred:

```text
DocumentDetector
→ SegmentationModel
→ LiteRT implementation
```

rather than:

```text
ViewModel
→ Interpreter
→ Tensor
```

This also allows fake detectors for tests.

Example:

```text
FakeDocumentDetector
→ deterministic test geometry
```

---

# 60. TESTABILITY ARCHITECTURE

Every major subsystem should be testable independently.

Examples:

```text
FakeSegmentationModel
FakeDocumentDetector
FakeTargetTracker
FakeQualityAssessor
FakeAssetRepository
FakePdfWriter
```

This allows:

```text
PageObject
→ PDF export
```

to be tested without opening a camera.

---

# 61. GOLDEN IMAGE ARCHITECTURE

Golden test data should be organized by problem class.

Suggested:

```text
testdata/
├── single-page/
├── perspective/
├── complex-shapes/
├── multiple-documents/
├── occlusion/
├── shadow/
├── glare/
├── books/
├── curved-pages/
└── enhancement/
```

Each test case should ideally contain:

```text
source image
expected geometry
expected metadata
expected output characteristics
```

Not every test requires exact pixel equality.

Geometry/semantic invariants may be more appropriate.

---

# 62. BENCHMARK ARCHITECTURE

Keep benchmark code outside production scan logic.

Suggested:

```text
benchmark/
├── detector/
├── tracking/
├── geometry/
├── enhancement/
├── pdf/
└── memory/
```

Benchmarks must be reproducible.

Record:

* device;
* Android version;
* model version;
* processing configuration;
* timings;
* memory;
* result quality.

---

# 63. LOGGING ARCHITECTURE

Create centralized diagnostic logging.

Examples:

```text
CameraLog
DetectionLog
GeometryLog
ProcessingLog
PdfLog
PerformanceLog
```

Logs may contain:

* timing;
* confidence;
* state;
* dimensions;
* error categories.

Logs must not contain document contents.

---

# 64. ERROR BOUNDARIES

Every subsystem should expose failures through typed/domain-safe errors.

Example:

```text
CameraError
DetectionError
GeometryError
ProcessingError
StorageError
PdfExportError
```

Avoid passing raw low-level exceptions across the entire application.

The application layer translates infrastructure failures into actionable states.

---

# 65. FALLBACK ARCHITECTURE

Preferred fallback hierarchy:

```text
AI segmentation
    ↓
geometry engine
    ↓
classical CV fallback
    ↓
best-effort geometry
    ↓
manual crop/edit
```

A model failure should not make the complete scanner unusable.

Fallback quality may be lower, but workflow should remain functional where technically possible.

---

# 66. OFFLINE ARCHITECTURE

Core scanner path must not require network access.

```text
Camera
→ local AI
→ local geometry
→ local processing
→ local PageObject
→ local PDF
```

No server is needed in this chain.

Future cloud features must be layered on top rather than built into the scanner core.

---

# 67. PRIVACY ARCHITECTURE

Document data path:

```text
camera
→ device local storage
→ local processing
→ user export
```

There should be no implicit:

```text
camera
→ remote server
```

pipeline.

Telemetry must not transmit document image data.

---

# 68. FEATURE EXTENSIBILITY

Architecture should leave room for future features without implementing them prematurely.

Potential future systems:

```text
OCR
Search
Document indexing
Cloud backup
Sharing
Printing
Batch automation
```

Future features should consume PageObject/Document abstractions rather than bypassing scanner internals.

Example future OCR:

```text
PageObject
→ OCR Processor
→ OCR Result
```

not:

```text
Camera
→ OCR
```

---

# 69. VERSIONING OF PROCESSED DATA

Image processing algorithms will evolve.

PageObject metadata should preserve processing version information.

Example:

```text
processingVersion
geometryVersion
enhancementVersion
modelVersion
```

This allows future migration or reprocessing.

Without versioning:

```text
old output
```

may become impossible to explain or regenerate consistently.

---

# 70. ARCHITECTURE DECISION RULES

When choosing between implementations, prioritize:

1. product correctness;
2. document fidelity;
3. memory safety;
4. testability;
5. maintainability;
6. measured performance;
7. implementation simplicity.

Do not select an architecture merely because it is fashionable.

Do not add abstraction without a real boundary.

Do not remove a necessary boundary just because the first version is small.

---

# 71. PRODUCT-SPECIFIC INVARIANTS

The following architecture invariants must never be violated.

### Invariant 1

PDF page size cannot influence document detection.

### Invariant 2

The source capture remains available until no longer needed.

### Invariant 3

Rendered images are derived data.

### Invariant 4

PageObject is the editing source of truth.

### Invariant 5

Manual shutter cannot be blocked by quality assessment.

### Invariant 6

Auto Capture and manual capture use the same ImageCapture layer.

### Invariant 7

Two Page is a dedicated geometry pipeline.

### Invariant 8

Two Page performs dewarping.

### Invariant 9

Complex shapes resolve to enclosing quadrilateral geometry.

### Invariant 10

Low-resolution geometry must be mapped/refined before full-resolution processing.

### Invariant 11

Camera frame queues must not grow unbounded.

### Invariant 12

All core processing works without a network.

---

# 72. RECOMMENDED PACKAGE DEPENDENCY GRAPH

Conceptual:

```text
                         ┌─────────────┐
                         │ Presentation│
                         └──────┬──────┘
                                │
                                ▼
                         ┌─────────────┐
                         │ Application │
                         └──────┬──────┘
                                │
                    ┌───────────┴───────────┐
                    ▼                       ▼
              ┌───────────┐          ┌────────────┐
              │   Domain  │          │ Use Cases  │
              └─────┬─────┘          └─────┬──────┘
                    │                       │
                    └───────────┬───────────┘
                                ▼
                     ┌─────────────────────┐
                     │ Infrastructure APIs │
                     └──────────┬──────────┘
                                │
              ┌─────────────────┼──────────────────┐
              ▼                 ▼                  ▼
           Camera              ML                OpenCV
              │                 │                  │
              └─────────────────┼──────────────────┘
                                ▼
                         Android / Storage
```

The actual dependency graph should avoid circular dependencies.

---

# 73. SUGGESTED INTERFACE BOUNDARIES

Core interfaces:

```kotlin
interface CameraController

interface DocumentDetector

interface SegmentationModel

interface TargetSelector

interface TargetTracker

interface CoordinateMapper

interface DocumentGeometryEngine

interface QualityAssessor

interface ImageEnhancer

interface PageRepository

interface ScanSessionRepository

interface AssetRepository

interface PdfRenderer

interface PdfWriter
```

Concrete implementation names may differ.

Interfaces should only exist where substitution/testing/boundary isolation is useful.

Do not create interfaces for every class mechanically.

---

# 74. RESPONSIBILITY MATRIX

```text
CameraController
→ camera acquisition

DocumentDetector
→ document candidate detection

TargetSelector
→ choose intended candidate

TargetTracker
→ temporal continuity

CoordinateMapper
→ coordinate-system transformation

DocumentGeometryEngine
→ physical document geometry

QualityAssessor
→ quality metrics

ImageEnhancer
→ content-preserving enhancement

PageObject
→ editable document state

PageManager / use cases
→ document editing operations

AssetRepository
→ binary asset lifecycle

ScanSessionRepository
→ session metadata/state

PdfRenderer
→ PageObject to renderable page

PdfWriter
→ page output to PDF file
```

No subsystem should silently absorb responsibilities from another subsystem.

---

# 75. SCAN PIPELINE CONTRACT

The end-to-end scanner contract is:

```text
CAMERA
  ↓
Analysis Frame
  ↓
Document Detection
  ↓
Candidate Selection
  ↓
Temporal Tracking
  ↓
Capture
  ↓
Source Asset
  ↓
Coordinate Mapping
  ↓
Geometry Refinement
  ↓
One Page / Two Page
  ↓
Perspective / Dewarp
  ↓
Quality Assessment
  ↓
Enhancement
  ↓
PageObject
  ↓
Document
  ↓
PDF Renderer
  ↓
PDF
```

Each stage should have a clear input/output contract.

---

# 76. CAMERA TO PAGE CONTRACT

Input:

```text
CaptureResult
```

Output:

```text
PageObject
```

Intermediate stages:

```text
CaptureResult
→ GeometryResult
→ ProcessedImage
→ QualityMetrics
→ EnhancementState
→ PageObject
```

This contract should allow the entire post-capture pipeline to be tested using static image files.

---

# 77. PAGE TO PDF CONTRACT

Input:

```text
Document
+
PdfExportOptions
```

Output:

```text
PdfExportResult
```

PDF system must not need to know whether a PageObject originated from:

* camera;
* gallery import;
* One Page;
* Two Page;
* manual crop.

It only consumes valid page state.

---

# 78. RECOVERY CONTRACT

If any post-capture stage fails:

```text
source asset remains
PageObject/session state remains recoverable
```

The system must not produce:

```text
processing failed
→ source deleted
```

Recovery is part of architecture, not just error UI.

---

# 79. DESIGN INDEPENDENCE

UI architecture must consume state rather than own scanner internals.

Example:

```text
CameraViewModel
→ ScannerUiState
```

not:

```text
Compose
→ CameraX
→ OpenCV
→ ML
```

The final visual implementation may be replaced without rewriting scanner/business logic.

---

# 80. OBSERVABILITY

Every major pipeline stage should expose measurable timing.

Example:

```text
captureTime
detectionTime
mappingTime
geometryTime
dewarpTime
enhancementTime
renderTime
pdfWriteTime
```

This allows bottlenecks to be identified using real measurements.

---

# 81. BUILD / DEVELOPMENT SEQUENCE

Architecture should be implemented incrementally.

Recommended:

```text
1. Android project baseline
2. CameraX foundation
3. Coordinate mapping
4. Detector API
5. Detector implementation
6. Target selection
7. Temporal tracking
8. Single-page geometry
9. Full-resolution refinement
10. Enhancement
11. PageObject
12. Session persistence
13. Page Manager
14. Two Page geometry/dewarp
15. PDF rendering
16. Gallery import
17. Recovery
18. Performance optimization
19. Regression suite
```

Do not create a fully abstracted empty architecture before any real subsystem works.

---

# 82. ARCHITECTURAL TESTING STRATEGY

Test each boundary independently.

Examples:

```text
FakeDetector
→ tracking tests

Known mask
→ geometry tests

Known PageObject
→ PDF tests

Known source image
→ enhancement tests

Known session
→ recovery tests
```

A subsystem should not require the entire app to test a local property.

---

# 83. PERFORMANCE ARCHITECTURE CHECKLIST

Before considering the architecture stable, verify:

* no unbounded ImageAnalysis queue;
* `ImageProxy` release is correct;
* ML model not recreated every frame;
* no unnecessary full-resolution bitmap copies;
* no simultaneous retention of all page images;
* PDF export is sequential;
* OpenCV resources are released;
* derived caches are bounded;
* large background tasks are cancellable;
* native memory is monitored;
* 20-page sessions do not produce unbounded memory growth.

---

# 84. FINAL ARCHITECTURE DEFINITION

yScanner follows a modular pipeline architecture in which:

CameraX handles acquisition.

ImageAnalysis feeds a lightweight local document detector.

Target selection and temporal tracking stabilize the user's intended target.

A dedicated geometry engine converts segmentation information into an enclosing quadrilateral and performs full-resolution refinement.

One Page uses planar perspective correction.

Two Page uses a dedicated book-spread geometry pipeline containing page-boundary detection, gutter detection, curvature estimation, dewarping, and page separation.

Quality assessment measures image/geometry conditions but never overrides the user.

Enhancement operates non-destructively on source assets.

PageObject represents editable scan state and acts as the source of truth for document editing.

Persistence stores sessions and source assets independently from derived renders.

PDF rendering consumes PageObjects and writes pages sequentially to control memory.

The entire core pipeline remains local and independent of network services.

The architecture must remain suitable for mid-range Android devices and must treat memory, lifecycle, and native image-processing resources as first-class engineering constraints.

---

# 85. ARCHITECTURE MANTRA

```text
Camera acquires.

AI detects.

Selection chooses.

Tracking stabilizes.

Geometry decides shape.

Refinement restores precision.

Dewarp flattens books.

Enhancement clarifies.

PageObject preserves editability.

Persistence preserves state.

PDF renders the result.

The user remains the authority.
```
