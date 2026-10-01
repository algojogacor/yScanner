# Original User Request

## Initial Request — 2026-10-01T04:47:09Z

Implement yScanner, a native Android document scanner application, from an existing repository of specification documents and implementation plans.

Working directory: d:\Projects\pdfscanner

## Requirements

### R1. Project Foundation and Build System
Initialize a multi-module Android project (Gradle KTS, Kotlin 2.0.x, compileSdk 35, minSdk 24) with modules: `:app`, `:camera`, `:detection`, `:geometry`, `:processing`, `:domain`, `:data`, `:pdf`, `:import`, `:common`, `:test-fixtures`. All dependencies configured in a version catalog. The project must build successfully.

### R2. Camera and Real-Time Detection Pipeline
Implement CameraX integration (Preview, ImageAnalysis with latest-frame strategy, ImageCapture with file-backed output), coordinate transformation between 7 spaces, ML-based document segmentation, candidate extraction, target selection with tap-to-guide, temporal tracking with jitter reduction, live overlay, and Auto Capture with 2-second countdown. Manual shutter always available.

### R3. Document Geometry and Image Processing
Implement boundary extraction → outer envelope → quadrilateral fitting → corner refinement pipeline. Full-resolution ROI-based corner refinement. Perspective correction via homography. Quality assessment metrics. Natural and Clean enhancement modes (non-destructive). Two Page mode with book spread detection, gutter detection, curvature estimation, and mandatory curved-page dewarping.

### R4. Domain Model, Persistence, and Export
Implement PageObject/Document domain model, Page Manager with crop/rotate/enhance/replace/duplicate/delete/undo/redo, incremental session persistence with process death recovery, gallery import through the same processing pipeline, and PDF rendering (page-by-page streaming) with 6 page sizes × 3 quality profiles, file-size estimation, and filename editing.

### R5. Quality and Performance
Memory budget: ≤600-700 MB normal, ≤800 MB peak on 8 GB RAM devices. No unbounded memory growth over 20-page sessions. All processing offline. Unit tests for geometry/math/transforms, integration tests for pipelines, golden-image regression tests, and stress tests.

## Acceptance Criteria

### Build and Structure
- [ ] Project builds with `./gradlew assembleDebug` without errors
- [ ] All specified modules exist with correct dependency graph
- [ ] Version catalog contains all dependencies

### Camera Pipeline
- [ ] CameraX Preview, ImageAnalysis, and ImageCapture functional
- [ ] ImageProxy released immediately after use (no frame queue buildup)
- [ ] Coordinate transforms handle portrait, landscape, rotation, and mismatched aspect ratios
- [ ] Flash modes (off/on/torch) work and persist
- [ ] Tap triggers both target selection and camera metering

### Detection and Tracking
- [ ] Segmentation model loads and runs inference on analysis frames
- [ ] Multiple document candidates detected and tracked
- [ ] Temporal tracking reduces corner jitter measurably vs raw detection
- [ ] Target locking prevents switching on small confidence differences
- [ ] Auto Capture fires after 2-second countdown when stable
- [ ] Manual shutter always works regardless of detection state

### Geometry and Processing
- [ ] Quadrilateral fitting produces enclosing quad (complex shapes included)
- [ ] Full-resolution corner refinement uses ROI (not full bitmap decode)
- [ ] Perspective correction outputs front-facing document
- [ ] Natural enhancement preserves color and handwriting
- [ ] Clean enhancement improves contrast while preserving pencil/faint marks
- [ ] Enhancement is non-destructive (re-renderable from source + parameters)

### Two Page Mode
- [ ] Book spread detection identifies left/right pages
- [ ] Gutter detection locates binding
- [ ] Curved-page dewarping applied (not a 50% split)
- [ ] Two separate PageObjects produced in left→right order

### Domain and Persistence
- [ ] PageObject stores source reference, geometry, rotation, enhancement params
- [ ] Page Manager supports all specified operations with undo/redo
- [ ] Session persists incrementally and recovers after process death
- [ ] Gallery images processed through same pipeline as camera captures

### PDF Export
- [ ] PDF renders page-by-page (constant memory regardless of page count)
- [ ] All 6 page sizes produce valid PDFs with correct dimensions
- [ ] File size estimation within 20% of actual
- [ ] Filename editable before generation

### Performance
- [ ] 20-page stress test shows no unbounded memory growth
- [ ] OpenCV Mat objects released in try/finally blocks
- [ ] No simultaneous full-resolution page bitmaps retained in RAM
- [ ] All core processing works without network

### Testing
- [ ] Unit tests pass for geometry math, coordinate transforms, quad fitting
- [ ] Integration tests pass for detection→geometry→processing→PDF pipeline
- [ ] All tests pass with `./gradlew test`
