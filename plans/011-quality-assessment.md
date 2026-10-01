# M10: Quality Assessment

## Objective
Implement robust image quality assessment for live preview (driving auto-capture readiness) and post-capture analysis (for diagnostics). This milestone evaluates blur, glare, shadows, exposure, geometry, and crop confidence without ever blocking user actions or forcing retakes.

## Product Requirements
- PRD.md §13 (Quality Assessment)
- PRD.md §15 (Auto Capture Readiness)

## Architecture References
- ARCHITECTURE.md §27 (Quality Assessment Pipeline)

## Current State
Perspective-corrected document images are produced from M09. The repository contains the core image processing pipeline and basic auto-capture state structures, but lacks real-time and post-capture quality signals.

## Scope
- Implement `QualityMetrics` data class.
- Define `QualityAssessor` interface.
- Implement specific quality estimators:
  - Blur detection (Laplacian variance or similar)
  - Glare detection (highlight region analysis)
  - Shadow detection (local brightness variation)
  - Exposure assessment (histogram analysis)
  - Geometry quality (quad regularity, corner confidence)
  - Crop confidence (edge strength at boundaries)
- Implement composite `overallScore` logic.
- Differentiate between lightweight live assessment (for preview/auto-capture) and detailed post-capture assessment.

## Non-Goals
- **CRITICAL**: Quality assessment will NEVER reject user captures, force retakes, delete results, disable editing or export, or block manual captures.
- Modifying or correcting the image based on quality metrics (quality assessment is strictly read-only observation).

## Dependencies
- M09 (Perspective Correction) must be complete to analyze crop and geometry confidence.
- OpenCV integration for Laplacian, histogram, and thresholding operations.

## Components
- `com.localscan.core.quality.QualityMetrics`: Data class holding all individual scores and the overall composite score.
- `com.localscan.core.quality.QualityAssessor`: Interface defining `assessLive(ImageProxy)` and `assessDetailed(Bitmap/Mat, Quadrilateral)`.
- `com.localscan.core.quality.impl.OpenCVQualityAssessor`: Main implementation using OpenCV.
- `com.localscan.core.quality.estimators.BlurEstimator`: OpenCV Laplacian variance implementation.
- `com.localscan.core.quality.estimators.ExposureEstimator`: Histogram-based implementation.
- `com.localscan.core.quality.estimators.GlareEstimator`: Thresholding and highlight region analysis.
- `com.localscan.core.quality.estimators.ShadowEstimator`: Local brightness variation detection.
- `com.localscan.core.quality.estimators.GeometryEstimator`: Quadrilateral regularity and edge gradient strength.

## Data Flow
1. **Live (Auto-Capture Readiness):** CameraX `ImageProxy` → Downscaled → `QualityAssessor.assessLive()` → (skips heavy estimators) → `QualityMetrics` → Auto Capture State Machine (updates UI countdown/readiness).
2. **Post-capture:** Captured high-res `Bitmap` (or `Mat`) + detected `Quadrilateral` → `QualityAssessor.assessDetailed()` → `QualityMetrics` → Attached to document metadata for diagnostic storage and pipeline benchmarking.

## Implementation Steps
1. Create `com.localscan.core.quality.QualityMetrics` with float fields: `blurScore`, `glareScore`, `shadowScore`, `exposureScore`, `geometryScore`, `cropConfidence`, `cornerConfidence`, and `overallScore`.
2. Create `com.localscan.core.quality.QualityAssessor` interface with `assessLive` and `assessDetailed` methods.
3. Implement `BlurEstimator` using `Imgproc.Laplacian` and variance calculation.
4. Implement `ExposureEstimator` using `Imgproc.calcHist` to evaluate under/over-exposure.
5. Implement `GlareEstimator` and `ShadowEstimator` to analyze localized highlight/shadow clusters.
6. Implement `GeometryEstimator` taking a `Quadrilateral` to measure shape regularity and corner confidence.
7. Implement `OpenCVQualityAssessor` to orchestrate estimators. Weight individual scores to produce `overallScore` [0.0 - 1.0].
8. Optimize `assessLive` by downsampling images and only running `BlurEstimator`, `ExposureEstimator`, and `GeometryEstimator` for real-time performance.
9. Integrate `QualityMetrics` into the Auto Capture flow to gate the capture countdown.

## Testing
- `BlurEstimatorTest`: Verify blurry test images return lower scores than sharp test images.
- `ExposureEstimatorTest`: Verify over/under-exposed images return low scores, balanced images return high scores.
- `OpenCVQualityAssessorTest`: Verify composite `overallScore` is calculated correctly based on weighting.
- `QualityAssessorPerformanceTest`: Verify `assessLive` executes within the 30ms latency budget.

## Validation
- Run unit tests against a curated dataset of known good, blurry, glared, and poor-geometry document images.
- Verify through manual testing that the auto-capture countdown only begins when the document is in focus and well-lit.
- Verify manual capture is completely unblocked regardless of quality scores.

## Performance
- **Live Assessment:** Must run < 30ms per frame to avoid dropping preview frames. Uses aggressive downsampling.
- **Detailed Assessment:** Can take up to ~150ms as it runs asynchronously post-capture.

## Failure Cases
- If an OpenCV exception occurs during assessment, the pipeline catches it and returns a `QualityMetrics` instance with perfect scores (1.0). This prevents pipeline stalls and adheres to the invariant of never blocking the user on quality failures.

## Acceptance Criteria
- `QualityMetrics` class includes all specified fields.
- `assessLive` reliably identifies motion blur and poor exposure in real-time, executing in < 30ms.
- Auto-capture successfully uses live metrics to wait for a stable, sharp image before triggering.
- Detailed metrics are generated post-capture and stored in metadata.
- Manual capture bypasses quality gating entirely.

## Git Checkpoint
`feat: Implement image quality assessment for auto-capture and diagnostics`

## Risks
- False positives in blur or glare detection could prevent auto-capture from firing, frustrating the user.
- Tuning the weights for the `overallScore` requires empirical testing.

## Open Questions
- What are the optimal weighting coefficients for combining individual metric scores into the `overallScore`?
- What exact `overallScore` threshold should trigger auto-capture readiness?
