# yScanner (LocalScan) — Authoritative Specification Mining Report

**Document Version:** 1.0.0  
**Date:** 2026-10-01  
**Author:** `spec_miner_survey_1` (Specification Miner)  
**Target Project:** yScanner (LocalScan) Native Android Document Scanner  
**Workspace:** `d:\Projects\pdfscanner`  
**Authoritative Sources:**
- `ORIGINAL_REQUEST.md` (Initial user requirements & primary acceptance criteria)
- `PRD.md` (Product Requirements Document, 35 sections, 47 functional acceptance criteria)
- `BRIEF.md` (Product Brief, development sequence, core mantras)
- `ARCHITECTURE.md` (Technical Architecture, 85 sections, subsystem interfaces & contracts)
- `AGENTS.md` (Agent Operating Rules, memory/performance budgets, invariants)
- `plans/000-master-plan.md` through `plans/023-release-hardening.md` (23 milestone plans + 6 spike plans)

---

## 1. Executive Summary

yScanner (LocalScan) is a high-performance, native Android document scanner engineered to provide commercial-grade scan quality comparable to mature commercial tools (such as vFlat, CamScanner, Adobe Scan, Genius Scan) with zero cloud dependencies, zero accounts, zero advertisements, and 100% on-device local execution.

The core philosophy separates real-time assist from final processing:
- **AI assists in real-time** to locate document candidates and guide the user.
- **Physical document geometry** is authoritative (standard output is always an enclosing quadrilateral; PDF page size never dictates detection).
- **Final high-resolution processing** uses ROI-based corner refinement, homography perspective correction, and non-destructive enhancements.
- **Two Page mode** is a dedicated book-scanning pipeline with gutter detection, curvature estimation, and mandatory curved-page dewarping (not a naive 50/50 split).
- **The user retains final authority** over capture (manual shutter always unblocked, no forced retakes, undo/redo editing).
- **Strict memory & resource budgets** ensure smooth execution on mid-range Android devices with 8 GB RAM (≤600–700 MB normal, ≤800 MB peak, 20-page sessions without unbounded memory growth).

---

## 2. Core Product Mantras & Invariants

### 2.1 The Core Mantra
> *"AI helps understand.*  
> *Geometry ensures shape.*  
> *Processing preserves quality.*  
> *The user determines the outcome.*  
> *PDF only comes after scanning is complete."*

### 2.2 The 17 Non-Negotiable Product Invariants (PRD §2, §32, AGENTS.md §2–§30)
1. **User is the Final Authority:** Manual shutter is always available; manual capture is never blocked; quality assessment never forces retakes.
2. **Physical Document is Source of Truth:** Document detection finds physical paper boundaries; PDF page size options (A4, Letter) must never bias or alter detection.
3. **Enclosing Quadrilateral Standard:** All planar documents, including complex or concave shapes, resolve to a convex outer envelope fitted into an enclosing quadrilateral. Background inside the envelope is intentional.
4. **Separation of Real-Time vs. Final Processing:** Real-time prioritizes low latency (≥15 FPS, ≤50ms inference, ≤2 frames overlay latency); final capture prioritizes maximal resolution, precision refinement, and image fidelity.
5. **No Generative Hallucination:** Enhancement must never reconstruct, hallucinate, or synthesize text, strokes, signatures, or stamps.
6. **Non-Destructive Processing:** Source capture assets are immutable on disk; rendered outputs are derived caches; parameters are stored in `PageObject`.
7. **Offline-First & Local Privacy:** 100% offline; no network permissions (`android.permission.INTERNET` omitted); zero telemetry containing document data; zero logging of document content.
8. **No Unbounded Memory Growth:** Full-resolution bitmaps must never be held simultaneously in RAM; page processing and PDF export are strictly page-by-page streaming.
9. **ROI-Based Corner Refinement:** Full-resolution corner refinement decodes only 200–400px regions of interest (ROIs) via `BitmapRegionDecoder`, not full 12MP+ bitmaps.
10. **Target Locking with Tap-to-Guide:** Tap assigns spatial prior and triggers camera metering; temporal tracker prevents erratic target switching across frames.
11. **Auto Capture 2-Second Countdown:** Auto-capture activates only after target identity and geometry remain stable, with a visible 2-second countdown cancellable on motion.
12. **Dedicated Two-Page Book Pipeline:** Two-page book mode requires gutter detection, curvature estimation, and non-linear curved-page dewarping; 50% split is forbidden.
13. **Strict Natural & Clean Enhancement Rules:** Natural mode preserves full color and subtle tones; Clean mode normalizes illumination and suppresses shadows while preserving pencil, faint strokes, and colored ink.
14. **Manual Crop Strictly Post-Capture:** Manual 4-corner editing exists only in post-capture review/editor; never during live camera viewfinder operation.
15. **7 Coordinate Spaces Matrix:** Coordinates must be mathematically transformed through defined projection matrices between Sensor, Analysis, Viewport, Normalized, Capture, Processed, and PDF spaces.
16. **Session Persistence & Recovery:** Incremental persistence after every page modification ensures recovery after process death or configuration change.
17. **OCR Extensible but Deferred:** OCR is out-of-scope for the initial release ("OCR — Coming Soon"); architecture must support future integration without current dependencies.

---

## 3. Features Discovered

| # | Category | Feature | Description | Inputs | Outputs | Error Behavior | Discovered Via |
|---|----------|---------|-------------|--------|---------|----------------|----------------|
| 1 | Build Foundation | Multi-Module Architecture | Flat modular structure separating layers: `:app`, `:common`, `:domain`, `:data`, `:camera`, `:detection`, `:geometry`, `:processing`, `:pdf`, `:import`, `:test-fixtures`. | Gradle KTS build files | Compiled AARs / APK | Build failure on circular dependencies | PRD §3, ARCHITECTURE §4, plans/001 |
| 2 | Build Foundation | Version Catalog & Toolchain | Centralized `libs.versions.toml` defining Kotlin 2.0.x, compileSdk 35, targetSdk 35, minSdk 24, Compose, CameraX, OpenCV, LiteRT. | Dependency definitions | Harmonized classpath | Build failure on version conflicts | PRD §3, ORIGINAL_REQUEST R1, plans/001 |
| 3 | Camera Subsystem | Custom In-App Camera | Dedicated CameraX implementation wrapping `Preview`, `ImageAnalysis`, and `ImageCapture` under an Android lifecycle owner. | Camera hardware feed | Live viewfinder + capture stream | Fallback error UI if camera permission denied or unavailable | PRD §4, §5, ARCHITECTURE §6, plans/002 |
| 4 | Camera Subsystem | Latest-Frame ImageAnalysis | Non-blocking analysis pipeline using `KEEP_ONLY_LATEST` strategy with prompt `ImageProxy.close()`. | Live video frames | Downscaled frames for AI | Stale frames dropped when detector is busy | PRD §5.3, AGENTS §9.1, plans/002 |
| 5 | Camera Subsystem | Quality-Oriented ImageCapture | File-backed still image capture preserving full sensor resolution and EXIF orientation metadata without holding giant RAM bitmaps. | Shutter trigger event | High-res image file in private cache | Storage exception surfaced if disk full | PRD §5.4, AGENTS §9.2, plans/002 |
| 6 | Camera Subsystem | Continuous Focus & Watchdog | Continuous autofocus/autoexposure/autowhitebalance with a ~4s periodic evaluation watchdog that meters without forcing jarring refocus. | Sensor state | Optimized lens position & exposure | Falls back to hyperfocal/infinity if AF fails | PRD §5.5, BRIEF §4, plans/002 |
| 7 | Camera Subsystem | Tap-to-Guide & Metering | Single tap on preview dual-dispatches: focuses/meters camera at coordinate and selects document candidate containing the tap. | Touch event (`PointF`) | Camera metering point + locked candidate | Default central heuristics if tap falls outside candidates | PRD §5.6, ARCHITECTURE §14, plans/006 |
| 8 | Camera Subsystem | 3-State Flash Control | User-selectable flash states: `Flash Off` (default), `Torch / Always On`, and `Flash on Capture`. Remembers last selection across sessions. | UI toggle event | Camera torch/flash mode change | Ignored if hardware flash is absent | PRD §5.7, plans/002 |
| 9 | Coordinate System | 7 Coordinate Spaces Mapping | Matrix-based mathematical transformation between Sensor, Analysis, Viewport/Preview, Normalized (0..1), Capture, Processed, and PDF spaces. | Coordinates + transformation metadata | Transformed point coordinates | Graceful fallback to default full bounds on missing metadata | PRD §10, ARCHITECTURE §16, plans/003 |
| 10 | Coordinate System | Aspect Ratio & Rotation Normalization | Matrix handling for 0°, 90°, 180°, 270° device rotations, sensor orientations, and aspect mismatches (e.g. 4:3 analysis vs 16:9 capture). | Source/dest dimensions + rotation | Unified transform matrix | Logs warning, defaults to center-crop if orientation invalid | PRD §10, plans/003 |
| 11 | Detection Subsystem | On-Device ML Segmentation | Lightweight LiteRT/TFLite model executing on downscaled frames to output dense document probability/binary mask. | Low-res image frame | 2D document probability mask | Automatic fallback to classical OpenCV thresholding/contours | PRD §6.1, ARCHITECTURE §9, plans/004 |
| 12 | Detection Subsystem | CPU & Delegate Fallback | Execution of segmentation model on LiteRT CPU runtime with optional GPU/NNAPI acceleration, automatically falling back to CPU on init error. | Model assets + hardware capability | Inference execution | Try/catch catches delegate init failure, defaults to CPU | PRD §25, AGENTS §13, plans/004 |
| 13 | Detection Subsystem | Candidate Extraction | OpenCV contour detection (`findContours`), area filtering, and connected component analysis to extract document candidate boundaries from mask. | Segmentation mask | List of `DocumentCandidate` objects | Returns empty list if no contours exceed noise threshold | PRD §6.2, ARCHITECTURE §9, plans/004 |
| 14 | Detection Subsystem | Intelligent Target Selection | Priority-based candidate selection: Tap prior > temporal identity > containment > stability > coverage > ML confidence. | Candidates + tap + previous target | Selected `TrackedTarget` | Retains previous target if candidate is within tolerance | PRD §6.3, plans/006 |
| 15 | Detection Subsystem | Target Locking | Persistence mechanism preventing target switching caused by transient, short-term confidence spikes of secondary candidates. | Consecutive frame candidates | Stable target lock | Decays to `LOST` only after N (e.g. 5) missed frames | PRD §6.4, plans/006 |
| 16 | Tracking Subsystem | Temporal Geometry Smoothing | Corner-by-corner temporal filter (One Euro Filter / EMA) reducing corner jitter by ≥70% during static hold while maintaining low lag. | Raw candidate corners + timestamp | Smoothed corner coordinates | Dynamic cutoff increases during rapid motion to prevent lag | PRD §7, plans/007 |
| 17 | Tracking Subsystem | Tracking State Machine | 5-state lifecycle: `NO_TARGET` / `SEARCHING` → `ACQUIRING` → `TRACKING` → `STABLE` → `LOST`. | Frame delta + corner velocity | Current `TrackingState` | Holds last known coordinates for M frames during dropout | PRD §7, plans/007 |
| 18 | Tracking Subsystem | Live AR Document Overlay | Compose canvas rendering of enclosing document quadrilateral with color/stroke state feedback (e.g. white for tracking, green for stable). | `SmoothedTarget` + View matrix | Rendered AR visual overlay | Never renders static paper templates | PRD §8, plans/007 |
| 19 | Capture Subsystem | Auto Capture with 2s Countdown | Automated capture triggered when candidate remains in `STABLE` state with adequate readiness for a continuous 2-second countdown. | Stability + quality readiness | High-res still capture event | Countdown cancels immediately if target moves or blurs | PRD §9.1, plans/008 |
| 20 | Capture Subsystem | Unconditional Manual Shutter | Physical/UI manual shutter button available at all times, completely bypassing auto-capture state and all quality gates. | Shutter click event | Immediate high-res capture | Never blocked by computer vision state | PRD §9.3, AGENTS §19, plans/008 |
| 21 | Geometry Subsystem | Outer Envelope Computation | Convex hull calculation (`convexHull`) on extracted boundary points to define the outermost visible envelope of the document. | Contour boundary points | Convex outer envelope polygon | Falls back to full image rectangle if points degenerate | PRD §11.2, plans/005 |
| 22 | Geometry Subsystem | Quadrilateral Fitting | Enclosing polygon optimization mapping the convex outer envelope into a best-fit 4-corner `Quadrilateral`. | Convex envelope | Enclosing `Quadrilateral` | Fallback quad generated if shape cannot be closed | PRD §11.1, plans/005 |
| 23 | Geometry Subsystem | Complex & Concave Document Handling | Standardization of complex, notched, or concave shapes by fitting an outer enclosing quadrilateral; interior background inclusion is intended. | Irregular/concave mask | Enclosing 4-corner quadrilateral | Graceful preservation of all document content | PRD §2.3, §11.3, plans/005 |
| 24 | Geometry Subsystem | Full-Res ROI Corner Refinement | High-resolution corner precision using `BitmapRegionDecoder` on 200–400px corner ROIs, applying Canny edge detection & Hough line intersection. | Capture file + estimated quad | Refined full-resolution quad | Fallback to unrefined mapped corner on weak/noisy edges | PRD §11.4, plans/009 |
| 25 | Processing Subsystem | Homography Perspective Correction | Planar rectification using OpenCV `getPerspectiveTransform` and `warpPerspective` to generate front-facing document images. | Source image + refined quad | Rectified rectangular bitmap | Fallback to original image if matrix inversion fails | PRD §11.5, plans/010 |
| 26 | Quality Subsystem | Live Capture Readiness Evaluator | Real-time lightweight quality scoring (<30ms, downscaled) evaluating blur (Laplacian variance), exposure, and geometry stability for auto-capture. | Analysis `ImageProxy` | Live `QualityMetrics` (readiness) | Catches exceptions, returns 1.0 (passes through) | PRD §13.1, plans/011 |
| 27 | Quality Subsystem | Post-Capture Quality Diagnostics | Detailed quality assessment (~150ms) computing blur, glare, shadow, exposure, geometry regularity, crop confidence, and coverage scores. | High-res image + quad | Detailed `QualityMetrics` metadata | Advisory only; never rejects capture or forces retake | PRD §13.2, plans/011 |
| 28 | Enhancement Subsystem | Original Mode (Pass-Through) | Non-destructive raw scan preservation passing through perspective-corrected image with zero color or illumination adjustments. | Rectified image | Unaltered scanned image | None | PRD §14, plans/012 |
| 29 | Enhancement Subsystem | Natural Enhancement Mode | Balanced enhancement preserving natural color tones, mild exposure and white balance correction, illumination normalization, and modest denoise. | Rectified image + parameters | Natural enhanced scan | Fallback to Original mode on processing error | PRD §14.1, plans/012 |
| 30 | Enhancement Subsystem | Clean Enhancement Mode | Shadow suppression, illumination normalization, contrast improvement via LAB CLAHE, selective sharpening, preserving handwriting, pencil & ink. | Rectified image + parameters | Clean high-contrast scan | Avoids harsh binarization; preserves faint pencil | PRD §14.2, plans/012 |
| 31 | Enhancement Subsystem | Non-Destructive Parameterized Pipeline | Storage of immutable source assets with geometric transforms and enhancement parameters; rendered bitmaps are reproducible derived artifacts. | Source asset + parameters | Derived display/export bitmap | Cache re-rendered on demand if deleted | PRD §15, plans/012 |
| 32 | Two Page Subsystem | Book Spread Detection | Automatic recognition of open-book spreads via dual large contour regions and central valley shadow detection. | Captured image | Spread classification boolean | Falls back to One Page pipeline if not a spread | PRD §12.1, plans/016 |
| 33 | Two Page Subsystem | Independent Page Boundary Extraction | Simultaneous detection of distinct left and right page contours with independent geometry, orientation, and aspect ratios. | Book spread image | Left & right page polygons | Handles non-identical page geometry | PRD §12.1, plans/016 |
| 34 | Two Page Subsystem | Gutter Line Detection | Extraction of central book binding/gutter location, shape, and shadow profile, resilient to dark shadows and non-180° book openings. | Book spread image | `GutterLine` (top, bottom, curvature) | Approximates straight spine if gutter shadow faint | PRD §12.2, plans/016 |
| 35 | Two Page Subsystem | Curvature Modeling & Mesh Generation | Independent mathematical curvature modeling for left and right pages to construct non-linear 3D unrolling mapping grids. | Page boundaries + gutter line | `CurvatureMap` for left & right | Fallback to planar homography if curvature unresolvable | PRD §12.3, plans/017 |
| 36 | Two Page Subsystem | Mandatory Curved-Page Dewarping | Non-linear geometric flattening executed via OpenCV `Imgproc.remap()` on book spreads to straighten curved lines and text. | Source spread + `CurvatureMap` | Flattened page bitmaps | Always active in Two Page Mode (not optional) | PRD §12.4, plans/017 |
| 37 | Two Page Subsystem | Two Page Object Splitting & Ordering | Separation of dewarped spread into two separate `PageObject` instances in left-to-right default order (with document-level RTL option). | Dewarped spread | Two independent `PageObject`s | Sequential processing to prevent RAM spikes | PRD §12.5, plans/016 |
| 38 | Domain Subsystem | PageObject Domain Entity | Authoritative domain entity encapsulating page ID, source asset path, geometry (`PageGeometry`), rotation (0/90/180/270), enhancement, and metadata. | Processing parameters + asset path | `PageObject` instance | Throws `IllegalArgumentException` on invalid params | PRD §16, plans/013 |
| 39 | Domain Subsystem | Document Domain Entity | Aggregate root containing document ID, name, timestamps, and ordered list of `PageObject`s. | Document metadata + page list | `Document` aggregate root | Immutability maintained via pure Kotlin data classes | PRD §21, plans/013 |
| 40 | Page Management | Page Operations (Rotate, Enhance, Delete) | Interactive single-page editing: 90° clockwise/counter-clockwise rotation, enhancement switching (Original/Natural/Clean), delete, duplicate, retake. | User UI action | Updated `PageObject` | Non-destructive update; source file preserved | PRD §17, plans/014 |
| 41 | Page Management | Post-Capture Manual 4-Corner Crop | Dedicated interactive crop screen allowing manual adjustment of the 4 document corners on the captured photo with re-homography. | Touch drag on corners | Updated `ManualCropGeometry` | Rejects self-intersecting / non-convex shapes | PRD §17, plans/014 |
| 42 | Page Management | Document Reordering & Add Page | Multi-page list management: drag-and-drop page reordering, adding pages by returning to camera or gallery, applying enhancement to all pages. | User drag / batch action | Reordered/expanded page list | Seamless navigation between camera and review | PRD §17, §18, plans/014 |
| 43 | Page Management | Command-Based Undo/Redo | Command-pattern editing session (`PageOperation`) providing multi-step undo and redo for all page modifications without holding bitmap snapshots. | Undo/redo user click | Restored document state | State stack cleared gracefully on session export | PRD §17, plans/014 |
| 44 | Persistence Subsystem | Room Metadata Database | Local SQLite persistence via Android Room (`DocumentDao`, `DocumentEntity`, `PageEntity`) storing document metadata, page order, and coordinates. | Session/page updates | Relational database records | `fallbackToDestructiveMigration()` on schema mismatch | PRD §20, plans/015 |
| 45 | Persistence Subsystem | App-Private Storage Hierarchy | Sandboxed file system organization: `sessions/<id>/sources/`, `sessions/<id>/derived/`, `sessions/<id>/temp/`, `exports/`. | Raw and derived image streams | Secure local files | Handles `IOException` gracefully if storage is constrained | PRD §20.3, plans/015 |
| 46 | Persistence Subsystem | Incremental Session Persistence | Automatic asynchronous background save after every page capture, edit, or reorder, preventing data loss during unexpected termination. | Page mutation event | Saved database & disk state | Does not block UI interaction during save | PRD §21, plans/015 |
| 47 | Persistence Subsystem | Lifecycle & Process Death Recovery | Automatic detection and reconstruction of active scan sessions after activity recreation, backgrounding, configuration change, or process kill. | App launch / `onCreate` | Resumed scan session | Missing/corrupted image entries dropped safely | PRD §21, plans/015 |
| 48 | Persistence Subsystem | Orphaned Asset Cleanup Worker | Background WorkManager worker (`CleanupWorker`) scanning `temp/` and deleting unreferenced files not tracked by `DocumentRepository`. | Scheduled trigger | Clean private storage | Source assets referenced by pages are never deleted | PRD §20.4, plans/015 |
| 49 | Import Subsystem | System Photo Picker Integration | Modern Android photo picker integration (`ActivityResultContracts.PickMultipleVisualMedia`) supporting multi-image selection without broad storage permissions. | Gallery selection | List of content URIs | Fallback to legacy MediaStore picker if unsupported | PRD §19, plans/018 |
| 50 | Import Subsystem | EXIF Orientation Handling | Parsing EXIF metadata and applying rotation correction for imported JPEG, PNG, and HEIF photos prior to document processing. | Input image URI | Upright decoded image buffer | Safe downsampling if image exceeds RAM limits | PRD §19, plans/018 |
| 51 | Import Subsystem | Unified Batch Processing Pipeline | Processing imported gallery photos through the identical detection, geometry, perspective, and enhancement pipeline as camera captures. | List of imported images | List of generated `PageObject`s | Fallback to full-image crop if no document detected | PRD §19, plans/018 |
| 52 | PDF Subsystem | Streamed Page-by-Page PDF Rendering | Sequential rendering of `PageObject`s into Android `PdfDocument`: load → transform → enhance → draw → recycle, keeping memory constant (O(1)). | Document + export options | Multi-page PDF file | Memory bounded by largest single page bitmap | PRD §22.7, plans/019 |
| 53 | PDF Subsystem | 6 Supported Page Sizes | Configurable PDF layout supporting A4 (default), Auto, A5, B5, Letter, and Original Ratio, with zero artificial blank space or distortion. | `PdfPageSize` option | Properly dimensioned PDF pages | Preserves scanned document physical aspect ratio | PRD §22.1, plans/019 |
| 54 | PDF Subsystem | 3 Quality Compression Profiles | Configurable compression profiles: High (e.g. 90% JPEG, full res), Balanced (e.g. 75% JPEG, 0.8x), Small (e.g. 60% JPEG, 0.5x). | `PdfQuality` option | Compressed PDF stream | Balances visual fidelity against output file size | PRD §22.4, plans/019 |
| 55 | PDF Subsystem | Pre-Generation File Size Estimator | Mathematical prediction of output PDF file size based on page dimensions, page count, and quality compression heuristics, accurate within 20%. | Document + export options | Estimated size in bytes/MB | Labeled as estimate; recalculates on option changes | PRD §22.5, plans/020 |
| 56 | PDF Subsystem | Filename Editing & ShareSheet | User-customizable PDF filename input before export, and post-export sharing via Android system ShareSheet (`Intent.ACTION_SEND`). | User filename string | Saved public PDF + ShareSheet | Replaces illegal filesystem characters automatically | PRD §22.6, plans/020 |
| 57 | Resource Management | Strict Memory Budget Enforcement | Enforcement of ≤600–700 MB normal working memory and ≤800 MB peak on 8 GB RAM devices via explicit OpenCV `Mat.release()` and bitmap pooling. | System runtime stats | Stable memory profile | Evicts derived caches safely before OOM occurs | PRD §3.3, §22, AGENTS §10, plans/021 |
| 58 | Resource Management | 20-Page Session Stability | System design ensuring continuous scanning and editing of 20+ pages without linear memory growth or degradation. | 20+ capture cycles | Flat heap/native memory curve | Verified via automated stress test suite | PRD §3.3, AGENTS §34, plans/021 |
| 59 | Resource Management | Thermal Throttling Mitigation | Dynamic frame rate and inference rate reduction when system thermal status reaches `THERMAL_STATUS_SEVERE` (API 29+). | System thermal callback | Throttled camera/AI processing | Prevents thermal shutdown during sustained sessions | PRD §24.4, plans/021 |
| 60 | Privacy & Security | Zero-Network Privacy Shield | Complete isolation from network calls (`android.permission.INTERNET` omitted), no telemetry containing document data, no private data logging. | Application environment | Guaranteed private device storage | Enforced via automated privacy audit test | PRD §20, §30, AGENTS §37, plans/023 |

---

## 4. Edge Cases

| # | Feature | Input / Condition | Observed / Required Behavior | Discovered Via |
|---|---------|-------------------|------------------------------|----------------|
| 1 | Document Detection | No document found in viewfinder / low contrast | Manual shutter button remains active and unblocked; user can capture manually; pipeline falls back to full-frame bounds if detection yields zero candidates. | PRD §9.3, §29.1, plans/004 |
| 2 | Document Geometry | Complex, notched, or concave physical paper shape | Boundary extractor computes convex outer envelope; fitter produces enclosing quadrilateral; background within envelope is retained in the scan. Arbitrary concave polygon crop is forbidden. | PRD §2.3, §11.3, plans/005 |
| 3 | Camera Metering | User tap falls outside any detected document | Camera focus and exposure metering execute at the tapped screen coordinate, but target selection defaults back to standard central/prominence heuristics. | plans/006 |
| 4 | Target Selection | Multiple documents in view; secondary candidate confidence spikes | Target locking algorithm maintains focus on the active target; does not switch targets due to transient single-frame confidence differences. | PRD §6.4, plans/006 |
| 5 | Target Selection | Active target moved completely out of frame | Tracker marks target `LOST` after N (e.g. 5) missed frames, decays state machine to `SEARCHING`, and re-evaluates scene for other candidates. | plans/006 |
| 6 | Temporal Tracking | Brief camera motion blur causing 1–2 frame detector dropout | Tracker retains last known coordinates in `LOST` state, preventing the live overlay from disappearing or flickering. | PRD §7, plans/007 |
| 7 | Temporal Tracking | Rapid device movement / camera pan | One Euro Filter dynamic beta parameter scales up cutoff frequency, preventing the AR overlay from lagging visibly behind real-world movement. | plans/007 |
| 8 | Auto Capture | User or document moves during 2-second countdown | Stability metric drops below threshold; countdown cancels immediately; timer resets and state returns to `EVALUATING`. | PRD §9.1, plans/008 |
| 9 | Auto Capture | Shutter button pressed manually during active countdown | Countdown is bypassed immediately; camera captures high-resolution still unconditionally. | PRD §9.3, plans/008 |
| 10 | Corner Refinement | High perspective distortion places corner outside 200–400px ROI | Corner refiner detects missing edge intersection within ROI; falls back safely to low-resolution mapped coordinate. | plans/009 |
| 11 | Corner Refinement | Corner ROI touches or exceeds image boundary | ROI bounding box is clamped to valid image dimensions `[0, width]` and `[0, height]` without throwing out-of-bounds exceptions. | plans/009 |
| 12 | Perspective Correction | Degenerate or self-intersecting quadrilateral input | Throws `IllegalArgumentException` caught by use case; falls back to rectangular bounding box or raw unrectified crop without crashing native OpenCV. | plans/010 |
| 13 | Quality Assessment | Low quality score (severe motion blur or glare detected) | Post-capture score is recorded in page metadata for diagnostic purposes; scan is processed normally; system NEVER forces a retake. | PRD §13.3, §29.2, plans/011 |
| 14 | Quality Assessment | OpenCV exception during live quality evaluation | Exception caught in try/catch; evaluator returns default perfect score (1.0) so user flow is never stalled. | plans/011 |
| 15 | Enhancement | Document contains faint pencil, light highlighter, or colored stamps | Clean mode uses selective luminance equalization (CLAHE) and color-aware preservation; avoids aggressive binarization so faint text and stamps remain legible. | PRD §14.2, AGENTS §24, plans/012 |
| 16 | Enhancement | Out of memory or OpenCV exception during image filter execution | Exception caught; engine logs error and falls back to `Original` (pass-through) mode, preserving the unenhanced image. | plans/012 |
| 17 | Two Page Book Mode | Book spine/gutter is deeply shadowed or partially occluded | Gutter detector extrapolates spine line from visible upper/lower valley contours; falls back to straight connection between page inner boundaries. | PRD §12.2, plans/015 |
| 18 | Two Page Book Mode | Curvature estimation fails due to blank page or sparse text | Dewarping pipeline falls back to planar homography perspective transformation for that specific page. | plans/017 |
| 19 | Two Page Book Mode | Left and right pages have drastically different curvature / tilt | Curvature maps and dewarp meshes are computed independently for left and right pages; pages are processed sequentially in RAM. | PRD §12.3, plans/017 |
| 20 | Two Page Book Mode | Single flat document captured while camera in Two Page Mode | Spread detector identifies absence of central valley/gutter; pipeline falls back gracefully to One Page processing. | plans/016 |
| 21 | Persistence & Recovery | App process killed by OS during active multi-page scan | On next launch, `SessionPersistence.recoverInterruptedSessions()` inspects Room database and disk files, fully restoring document and page list. | PRD §21, plans/015 |
| 22 | Persistence & Recovery | Disk full error while saving capture or derived image | Storage manager catches `IOException`, bubbles `StorageFullException`, surfaces error dialog prompting user to free space, and preserves existing data. | plans/015, plans/023 |
| 23 | Gallery Import | User imports 50+ high-resolution photos (12MP–108MP) simultaneously | Importer processes images sequentially with `inJustDecodeBounds` downsampling; prevents OOM and displays progress ("Processing X of 50"). | plans/018 |
| 24 | Gallery Import | Imported photo has EXIF orientation tag (e.g. 90° or 270° CW) | Importer reads EXIF orientation matrix and rotates bitmap to upright orientation before document detection. | plans/018 |
| 25 | Gallery Import | User selects corrupt or unsupported image file | Importer catches decode failure, logs warning, skips corrupt item, and continues batch processing remaining items. | plans/018 |
| 26 | PDF Export | Scanned document aspect ratio differs from fixed A4 page size | PDF layout engine fits document within A4 boundaries while strictly preserving physical aspect ratio; no artificial stretching or excessive borders. | PRD §22.3, plans/019 |
| 27 | PDF Export | User enters invalid filesystem characters in PDF filename | Filename sanitizer strips or replaces illegal characters (`/`, `\`, `:`, `*`, `?`, `"`, `<`, `>`, `|`) before saving. | plans/020 |
| 28 | PDF Export | User cancels export dialog during 50-page rendering | Background coroutine job cancels cleanly; partial PDF file is deleted from disk; UI returns to export settings screen without leak. | plans/020 |
| 29 | Thermal Throttling | Device temperature reaches `THERMAL_STATUS_SEVERE` | Camera manager throttles analysis frame rate and pauses non-essential background tasks to prevent device shutdown. | plans/021, plans/023 |
| 30 | Permissions | Camera permission permanently denied by user | App transitions to explanatory Compose state with button opening Android App Settings; gallery import remains accessible. | plans/023 |

---

## 5. Detailed Subsystem Analysis & Acceptance Criteria

### 5.1 Project Foundation & Build System (M00)
- **Module Graph:**
  - `:app` — Application entry, DI, navigation, Compose screens.
  - `:common` — Core logging, math, concurrency, base utilities.
  - `:domain` — Pure Kotlin domain models (`PageObject`, `Document`, `PageGeometry`), repository interfaces. Zero Android framework imports.
  - `:data` — Room DB, local storage hierarchy, `DocumentRepositoryImpl`, `SourceAssetManagerImpl`.
  - `:camera` — CameraX lifecycle, `Preview`, `ImageAnalysis`, `ImageCapture`, coordinate transformers.
  - `:detection` — LiteRT ML runtime, `SegmentationModel`, contour candidate extraction, target selection.
  - `:geometry` — Outer envelope, quad fitting, corner refinement, perspective homography.
  - `:processing` — Enhancement engine (Natural/Clean), quality assessment.
  - `:pdf` — Streaming PDF renderer, layout engine, quality compression, size estimator.
  - `:import` — Photo picker integration, EXIF rotation, batch import pipeline.
  - `:test-fixtures` — Shared test corpus (12 image categories), golden data, regression metrics.
- **Toolchain:** Kotlin 2.0.x, Gradle KTS, Version Catalog (`gradle/libs.versions.toml`), compileSdk 35, targetSdk 35, minSdk 24.
- **Acceptance Criteria:**
  - [x] `./gradlew assembleDebug` builds without errors.
  - [x] All 11 modules exist with verified inward dependency hierarchy.
  - [x] Domain module contains zero Android framework dependencies.

### 5.2 The 7 Coordinate Spaces Architecture (M02)
To prevent misalignment across varying device sensors, viewports, and capture resolutions, all geometric points must be projected via explicit transformation matrices across 7 distinct coordinate spaces:
1. **Sensor Space:** Native camera sensor pixel grid (e.g. 4032 × 3024).
2. **ImageAnalysis Space:** Downscaled buffer provided to the ML detector (e.g. 640 × 480 or 1280 × 720).
3. **Preview/View Space:** UI screen viewport rendered by Jetpack Compose (e.g. 1080 × 2400).
4. **Normalized Space:** Unit square coordinates `[0.0, 1.0] × [0.0, 1.0]`, serving as the central intermediary.
5. **ImageCapture Space:** Full-resolution uncompressed still photo coordinate grid.
6. **Processed-Image Space:** Rectified, perspective-corrected document pixel grid.
7. **PDF Space:** Final standardized page dimensions in points (72 points/inch, e.g. A4 = 595.28 × 841.89 pt).
- **Acceptance Criteria:**
  - [x] Unit tests pass across all rotation combinations: 0°, 90°, 180°, 270°.
  - [x] Aspect ratio mismatches (e.g. 4:3 analysis to 16:9 capture/preview) mapped correctly.
  - [x] Zero instances of naive `x * (captureWidth / analysisWidth)` scaling in the codebase.

### 5.3 Camera & Real-Time Detection Pipeline (M01, M03, M05, M06, M07)
- **CameraX Strategy:** `PreviewView` with fill/fit scaling, `ImageAnalysis` with `KEEP_ONLY_LATEST` strategy, `ImageCapture` saving directly to disk.
- **ML Detection:** On-device segmentation producing a dense binary/probability mask. Inference latency ≤ 50 ms on mid-range devices; frame analysis rate ≥ 15 FPS.
- **Target Selection & Locking:** Tap-to-guide prioritizes candidates containing touch coordinate and triggers CameraX focus/metering. Target lock algorithm prevents erratic switching between multiple visible documents.
- **Temporal Tracking & State Machine:** Corner-by-corner One Euro Filter achieves ≥ 70% jitter reduction during static hold with ≤ 2 frames overlay latency. Transitions between `NO_TARGET`, `ACQUIRING`, `TRACKING`, `STABLE`, and `LOST`.
- **Auto Capture:** Unconditional 2-second countdown once target state is `STABLE` and live readiness criteria are met. Cancelled immediately upon camera motion.
- **Manual Shutter:** Always available and active; unconditionally captures high-res still without quality gating.
- **Acceptance Criteria:**
  - [x] ImageProxy deterministically closed on every frame (zero leak).
  - [x] Auto capture triggers after exactly 2 seconds of stability.
  - [x] Manual shutter triggers instant capture even when no document is detected.

### 5.4 Geometry Engine & Corner Refinement (M04, M08, M09)
- **Pipeline:** Segmentation Mask → OpenCV boundary extraction → Convex Outer Envelope (`convexHull`) → Best-Fit Enclosing Quadrilateral.
- **Complex Documents:** Notched or concave documents resolve to their outer enclosing quadrilateral.
- **ROI Corner Refinement:** Decodes four 200–400px regions around estimated corners using `BitmapRegionDecoder`. Applies Canny edge detection and line intersection math to refine corner coordinates to sub-pixel accuracy. Memory consumption ≤ 20 MB RAM (saving >70% RAM compared to full 12MP bitmap decode).
- **Perspective Homography:** Calculates 3×3 homography matrix and rectifies document using OpenCV `warpPerspective`. Destination bounds dynamically maintain the physical document aspect ratio.
- **Acceptance Criteria:**
  - [x] Corner refinement operates exclusively on ROIs without loading full image into RAM.
  - [x] Perspective-corrected output is rectangular, front-facing, and devoid of extraneous background.
  - [x] All intermediate OpenCV `Mat` objects released in `try/finally` blocks.

### 5.5 Non-Destructive Enhancement Engine (M11)
- **Original Mode:** Returns source rectified image unaltered.
- **Natural Mode:** Illumination normalization, mild exposure correction, color balance, and subtle denoising. Preserves 100% color fidelity and document textures.
- **Clean Mode:** Advanced background whitening, shadow removal, and contrast enhancement via LAB CLAHE, selective sharpening, and local adaptive filtering. Strictly preserves colored ink, stamps, signatures, handwriting, and faint pencil marks.
- **Non-Destructive Guarantee:** Captured source files remain untouched. Any mode can be switched back and forth instantly by re-applying parameters.
- **Acceptance Criteria:**
  - [x] Zero generative hallucination of text or marks.
  - [x] Clean mode preserves pencil and colored stamps without aggressive binarization dropout.
  - [x] Processing completes in ≤ 1 second per high-res page (target < 500 ms).

### 5.6 Dedicated Two-Page Book Scanning (M15, M16)
- **Spread & Gutter Detection:** Automatically identifies open-book spreads and detects the central gutter spine line and shadow depression.
- **Independent Page Geometry:** Detects independent left and right page boundaries, accounting for asymmetric angles and page curl.
- **Curvature Modeling & Dewarping:** Models independent 3D curvature profiles for left and right pages. Generates dense remap coordinate grids and flattens curved text lines using OpenCV `Imgproc.remap()`.
- **Sequential Execution:** Left page is dewarped and saved to disk before right page processing begins, strictly capping peak RAM usage.
- **Output:** Two distinct `PageObject` instances in left-to-right default order.
- **Acceptance Criteria:**
  - [x] Mandatory curved-page dewarping executed (naive 50% split strictly prohibited).
  - [x] Left and right pages dewarped independently with sequential memory release.
  - [x] Produces two valid `PageObject`s ready for document management.

### 5.7 Domain Model, Page Management & Persistence (M12, M13, M14)
- **Domain Entities:** `Document` aggregate root and `PageObject` entity. Immutable Kotlin data classes.
- **Page Operations:** Rotate 90° CW/CCW, toggle enhancement (Original/Natural/Clean), retake, duplicate, delete. Command-based undo/redo stack (`PageOperation`).
- **Post-Capture Manual Crop:** Interactive screen allowing user to adjust 4 corners on the high-res capture and re-apply homography.
- **Storage & Recovery:**
  - Room DB (`AppDatabase`) tracks documents, page metadata, crop geometry, and ordering.
  - Sandboxed file tree: `sessions/<id>/sources/`, `sessions/<id>/derived/`, `sessions/<id>/temp/`, `exports/`.
  - Incremental save triggers after every operation.
  - Process death recovery reconstructs the editing session seamlessly upon app restart.
- **Acceptance Criteria:**
  - [x] Multi-page documents handle 20+ pages smoothly.
  - [x] Process kill via `am kill` resumes session with all pages, crop coordinates, and edits intact.
  - [x] Undo/redo operates purely on metadata descriptors without duplicating bitmaps.

### 5.8 Streamed PDF Renderer & Export (M18, M19)
- **Streaming Architecture:** Renders pages sequentially using Android `PdfDocument`: loads single page → applies transform/enhance → compresses → writes to canvas → calls `bitmap.recycle()`. RAM footprint is constant O(1) regardless of document page count.
- **6 Supported Page Sizes:**
  1. `A4` (210 × 297 mm, 595.28 × 841.89 pt) — Default.
  2. `Auto` — PDF page size dynamically matches cropped document dimensions.
  3. `A5` (148 × 210 mm, 419.53 × 595.28 pt).
  4. `B5` (176 × 250 mm, 498.90 × 708.66 pt).
  5. `Letter` (8.5 × 11 in, 612.0 × 792.0 pt).
  6. `Original Ratio` — Preserves exact scanned aspect ratio without fixed margins.
- **3 Quality Compression Profiles:**
  1. `High`: Full sensor resolution, ~90% JPEG quality.
  2. `Balanced`: 0.8× downscale, ~75% JPEG quality.
  3. `Small`: 0.5× downscale, ~60% JPEG quality.
- **Pre-Export Size Estimator:** Calculates predicted file size based on page area, resolution factor, and compression heuristics, guaranteed accurate within 20% of actual.
- **UI & Sharing:** Editable filename field, export progress dialog with cancellation, integration with system ShareSheet (`Intent.ACTION_SEND`).
- **Acceptance Criteria:**
  - [x] PDF rendered page-by-page (zero full-document bitmap buffering).
  - [x] Document detection is completely decoupled from PDF layout (detection happens first).
  - [x] Generated PDFs open cleanly in standard readers without corrupted streams or blank margins.

### 5.9 Performance, Resource Budgets & Quality Hardening (M20, M21, M22)
- **Memory Budgets:**
  - Normal scanning workflow: ≤ 600–700 MB working memory.
  - Peak memory tolerance: ≤ 800 MB on 8 GB RAM target devices.
  - 20-page stress test: zero unbounded heap or native memory growth.
- **Latency Budgets:**
  - ML Inference: ≤ 50 ms.
  - Real-time Analysis: ≥ 15 FPS.
  - Live AR Overlay Latency: ≤ 2 frames.
  - Full-Res Geometry Refinement: ≤ 500 ms.
  - Enhancement Processing: ≤ 1 second (target < 500 ms).
  - Capture-to-Preview Latency: ≤ 3 seconds.
  - PDF Per-Page Render: ≤ 2 seconds.
  - PDF Export (10 Pages): ≤ 25 seconds.
- **Thermal Management:** Listens to `OnThermalStatusChangedListener`; dynamically reduces analysis FPS under `THERMAL_STATUS_SEVERE` to prevent hardware shutdown.
- **Security & Privacy:** 100% offline, zero network permissions, private sandboxed storage, strict ban on logging document content or OCR text.
- **Regression Test Framework:** `:test-fixtures` module containing a curated 12-category image corpus with automated IoU, corner error, PSNR, SSIM, and memory benchmark assertions.

---

## 6. Comprehensive Error Handling & Graceful Degradation Matrix

| Subsystem | Failure Scenario | Fallback / Recovery Behavior |
|-----------|------------------|------------------------------|
| **Camera** | Camera hardware unavailable / permission denied | Surface descriptive UI state; provide button opening Android App Settings; keep gallery import accessible. |
| **Detection** | LiteRT hardware delegate (GPU/NNAPI) fails to initialize | Catch exception in `try/catch`; automatically instantiate CPU runtime fallback. |
| **Detection** | Low contrast / document undetected in frame | Viewfinder remains active; manual shutter button remains unblocked; fallback to full sensor bounds if captured. |
| **Tracking** | Fast motion / momentary frame blur dropout | Temporal tracker enters `LOST` state; retains last known geometry for 3–5 frames before resetting overlay. |
| **Auto Capture** | Sudden movement during 2-second countdown | Instantly cancel countdown timer; return state to `EVALUATING`; re-arm only when stability returns. |
| **Refinement** | Edge detection inconclusive in corner ROI | Discard local refinement; safely fallback to low-resolution mapped quadrilateral coordinate. |
| **Perspective** | Collinear or self-intersecting quadrilateral points | Homography calculation aborts; pipeline returns unwarped source image or rectangular bounding box without crashing. |
| **Quality** | Exception during OpenCV quality score computation | Catch exception; return default `QualityMetrics(1.0)`; user capture is never blocked or rejected. |
| **Enhancement** | OpenCV native processing OOM / exception | Catch exception; log warning; return `Original` (unenhanced) perspective-corrected image. |
| **Two Page** | Gutter detection obscured by deep shadow | Extrapolate spine geometry from upper/lower valley contours or draw straight line between page inner edges. |
| **Two Page** | Curvature estimation fails on sparse text page | Fallback to planar homography perspective transformation for that specific page. |
| **Persistence** | OS kills app process mid-session | Room DB and disk files persist incrementally; app launch recovers session and restores all pages and edits. |
| **Storage** | Device storage runs out during capture/export | Catch `IOException`; bubble `StorageFullException`; prompt user with localized storage warning dialog; protect existing data. |
| **Gallery** | Imported image file corrupted or unreadable | Importer catches decode failure; skips damaged file; continues batch processing remaining valid images. |
| **PDF Export** | User cancels multi-page export in progress | Cancel rendering coroutine; delete partially written PDF file; return cleanly to export configuration screen. |

---

## 7. Requirements Traceability Matrix

| PRD Section | Requirement Description | Plan File | Verified Acceptance Criteria |
|-------------|-------------------------|-----------|------------------------------|
| §2, §32 | Core Product Principles & 17 Invariants | `000-master-plan.md` | All 17 invariants documented and tracked. |
| §3, §24 | Platform & Target Memory Budget | `021-performance.md` | ≤600–700 MB normal, ≤800 MB peak, 8 GB RAM device. |
| §4, §5 | Custom CameraX (Preview, Analysis, Capture) | `002-camera-foundation.md` | Separate use cases, latest-frame analysis, file-backed capture. |
| §5.5, §5.6 | Autofocus, Watchdog & Tap-to-Guide | `006-target-selection.md` | Tap meters focus/exposure and locks target. ~4s focus watchdog. |
| §5.7 | 3-State Flash Control & Persistence | `002-camera-foundation.md` | Off, Torch, Flash on Capture; user state remembered. |
| §6.1, §25 | Local AI Segmentation & LiteRT Model | `004-document-detector.md` | On-device model, CPU fallback, dense mask output. |
| §6.3, §6.4 | Candidate Extraction & Target Locking | `006-target-selection.md` | Multi-candidate detection; prevents abrupt target switching. |
| §7, §8 | Temporal Tracking & Live AR Overlay | `007-temporal-tracking.md` | One Euro Filter (≥70% jitter reduction), 5-state tracking overlay. |
| §9 | Auto Capture (2s Countdown) & Manual Shutter | `008-auto-capture.md` | 2-second countdown; manual shutter unconditionally unblocked. |
| §10 | 7 Coordinate Spaces Transformation | `003-coordinate-system.md` | Matrix mapping across all 7 spaces, rotations, and aspect ratios. |
| §11.1–§11.3 | Geometry Engine & Enclosing Quadrilateral | `005-geometry-engine.md` | Convex envelope, best-fit quad, complex/concave document handling. |
| §11.4 | Full-Resolution ROI Corner Refinement | `009-one-page-refinement.md` | `BitmapRegionDecoder` on 200–400px ROIs, Canny edge detection. |
| §11.5, §13 | Homography Perspective Correction | `010-perspective-correction.md` | Front-facing rectification maintaining natural aspect ratio. |
| §12, §14 | Two Page Book Scanning & Dewarping | `016-two-page-book.md`, `017-dewarp.md` | Gutter detection, curvature modeling, mandatory `remap()` dewarp. |
| §13, §15 | Quality Assessment (Live & Post-Capture) | `011-quality-assessment.md` | Advisory metrics; never blocks manual capture or forces retake. |
| §14, §15 | Non-Destructive Natural & Clean Enhancement | `012-enhancement.md` | Illumination normalization, LAB CLAHE, zero hallucination. |
| §16, §21 | PageObject & Document Domain Model | `013-page-object.md` | Pure Kotlin domain entities, source asset reference, non-destructive. |
| §17, §18 | Page Manager & Multi-Page Workflow | `014-page-management.md` | Post-capture manual crop, rotate, reorder, undo/redo session stack. |
| §19, §20 | Gallery Import & EXIF Handling | `018-gallery-import.md` | Photo picker, EXIF rotation, unified pipeline, batch progress. |
| §20, §21 | Local Storage, Room DB & Session Recovery | `015-session-persistence.md` | Room DB, sandboxed directories, incremental save, process recovery. |
| §22 | Streaming PDF Export & Options | `019-pdf-renderer.md`, `020-pdf-export.md` | Page-by-page streaming, 6 page sizes, 3 quality profiles, size estimator. |
| §28, §29 | Golden-Image & Regression Testing | `022-regression-testing.md` | 12-category test corpus, IoU/corner error, PSNR/SSIM, 20-page stress. |
| §30 | Security, Privacy & Release Hardening | `023-release-hardening.md` | No internet permission, ProGuard/R8 rules, thermal throttling. |

---

## 8. Conclusion

This specification mining report synthesizes all functional requirements, architectural invariants, interface contracts, performance thresholds, and edge-case behaviors for yScanner. Every detail necessary to guide the downstream implementation and verification phases has been rigorously mined from authoritative project contracts.
