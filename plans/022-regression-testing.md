# M21: Golden-Image & Regression Testing

## Objective
Establish a robust, deterministic regression testing framework utilizing golden-image comparisons, geometry evaluation metrics, and stress tests to ensure quality and prevent regressions across the document scanning pipeline.

## Product Requirements
- **28.3** Golden Image Tests
- **28.4** Stress Tests
- **29** Evaluation Metrics

## Architecture References
- **16** Evaluation Metrics & Benchmarks
- **61** Testing Infrastructure
- **62** Regression Suite
- **63** Golden Image Testing Pipeline

## Current State
Performance optimizations (M20) are complete. Testing is primarily unit-based without a structured regression suite or deterministic golden-image tests for the end-to-end vision pipeline. Test images are not systematically organized or benchmarked.

## Scope
- Creation of a `:test-fixtures` module to house test images, reference data, and testing utilities.
- Implementation of a golden-image test framework for detection, geometry, and image processing.
- Creation of a comprehensive test corpus encompassing various challenging document scenarios (rotated, perspective, shadows, glare, etc.).
- Development of regression suites for geometry (IoU, corner error), enhancement (visual diff, PSNR/SSIM), two-page splitting, and PDF generation.
- Implementation of stress tests for memory stability (20-page sessions, large source images) and pipeline resilience.
- Implementation of benchmark result recording and regression alerting.

## Non-Goals
- Fixing issues discovered by the regression tests (this milestone only builds the test framework).
- Implementation of fully automated CI/CD pipeline execution (this focuses on the test code and execution framework locally/instrumented).
- Testing UI layout or navigation (this is focused on the core processing pipeline).

## Dependencies
- Completion of M20 (Performance Optimization).
- Existing end-to-end processing pipeline, including detection, geometry adjustment, processing, and PDF export.

## Components
- `com.yscanner.testfixtures`: New module for shared test data and utilities.
- `com.yscanner.testfixtures.corpus.TestCorpus`: Registry for test images and their expected outputs.
- `com.yscanner.testfixtures.metrics.GeometryMetrics`: Calculates IoU and corner error.
- `com.yscanner.testfixtures.metrics.ImageMetrics`: Calculates PSNR, SSIM, or perceptual diffs.
- `com.yscanner.pipeline.GeometryRegressionTest`: Instrumented test suite for geometry.
- `com.yscanner.pipeline.EnhancementRegressionTest`: Instrumented test suite for image processing.
- `com.yscanner.pipeline.StressTest`: Suite for memory and stability under load.
- `com.yscanner.testfixtures.benchmark.BenchmarkRecorder`: Saves and compares test results against baselines.

## Data Flow
1. `TestCorpus` loads input images and expected golden data (JSON for geometry, reference images for enhancement).
2. Regression tests feed input images into the processing pipeline.
3. Pipeline outputs are intercepted and passed to `GeometryMetrics` or `ImageMetrics`.
4. Metric results are compared against predefined thresholds.
5. `BenchmarkRecorder` logs results to local storage (JSON/CSV) for historical comparison.

## Implementation Steps
1. Create `:test-fixtures` module and add to `settings.gradle.kts`.
2. Populate `src/main/assets/corpus` in `:test-fixtures` with sample images and `annotations.json` for geometry/reference definitions:
   - standard_a4, rotated_45, strong_perspective, business_card, receipt, handwritten, shadow, glare, multi_doc, complex, book_spread, poor_lighting.
3. Implement `GeometryMetrics` in `:test-fixtures`:
   - Functions for `calculateIoU(expected: Quad, actual: Quad)` and `calculateCornerError(expected: Quad, actual: Quad)`.
4. Implement `ImageMetrics` in `:test-fixtures`:
   - Functions for `calculatePSNR(ref: Bitmap, actual: Bitmap)` and `calculateSSIM(ref: Bitmap, actual: Bitmap)`.
5. Implement `TestCorpus` to load bitmaps and annotations from assets.
6. Create `GeometryRegressionTest` in the main module's `androidTest` directory:
   - Loop through test corpus, run detection, assert IoU > 0.90 for standard images.
7. Create `EnhancementRegressionTest` in `androidTest`:
   - Run images through enhancement pipeline, compare output to golden reference, assert PSNR/SSIM above thresholds.
8. Create `TwoPageRegressionTest` in `androidTest`:
   - Test split accuracy and dewarp quality for `book_spread` scenarios.
9. Create `PdfRegressionTest` in `androidTest`:
   - Generate PDFs from test corpus, assert valid file output and correct page dimensions.
10. Create `StressTest` in `androidTest`:
    - Loop 20 pages through the full capture->process cycle, tracking memory allocation using `Runtime.getRuntime()`.
    - Run fast switching of enhancement modes.
    - Test gallery batch import with 12+ MP images.
11. Implement `BenchmarkRecorder` in `:test-fixtures`:
    - Write results to `File(context.filesDir, "benchmarks.json")`.
    - Add utility to read previous runs and fail test if metrics drop by > 5%.

## Testing
- Execute all newly created instrumented tests (`GeometryRegressionTest`, `EnhancementRegressionTest`, `TwoPageRegressionTest`, `PdfRegressionTest`, `StressTest`) on physical devices or emulators.
- Verify `BenchmarkRecorder` correctly identifies regressions when thresholds are artificially lowered.

## Validation
- All corpus images process successfully and meet baseline metric expectations.
- Memory profile during the 20-page stress test shows no linear leak (plateaus after steady state).
- Golden image tests run deterministically (same input always produces identical or threshold-passing output).

## Performance
- The test suite execution time is bounded (ideally under 5 minutes on a device).
- Running stress tests should not trigger OOM exceptions.

## Failure Cases
- Missing reference files in corpus -> Test fails with descriptive error.
- Metric calculation throws exception (e.g., mismatched bitmap sizes) -> Handled with test failure.
- Out of memory during stress tests -> Documented as baseline failure needing fix.

## Acceptance Criteria
- `:test-fixtures` module exists and contains the 12 specified corpus categories.
- `GeometryRegressionTest` runs and validates IoU/corner errors against expectations.
- `EnhancementRegressionTest` runs and validates image quality metrics against golden references.
- `StressTest` successfully processes 20 pages in a single session without crashing.
- `BenchmarkRecorder` stores test results and can detect 5% degradations.

## Git Checkpoint
`test: add golden-image and regression testing framework`

## Risks
- Image metrics (PSNR/SSIM) might be brittle if minor algorithm changes occur, requiring frequent baseline updates.
- Emulators might lack the memory/performance profile to run stress tests reliably compared to physical devices.
- Large test images in the repository might bloat Git history (consider downscaling or using external storage for massive datasets).

## Open Questions
- Should golden images and test corpus be tracked in Git LFS or stored in a remote bucket and downloaded before tests run?
- What exact numerical thresholds should be used for PSNR and SSIM for different enhancement modes?
