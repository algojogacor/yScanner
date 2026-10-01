# M09: Perspective Correction

## Objective
Implement perspective correction using OpenCV to transform a cropped, skewed document image into a flat, front-facing document based on the refined quadrilateral coordinates. Integrate this into the One Page processing pipeline.

## Product Requirements
- PRD.md §11.5 (Perspective Correction)
- PRD.md §13 (One Page Pipeline)

## Architecture References
- ARCHITECTURE.md §23 (Perspective Correction Component)
- ARCHITECTURE.md §24 (One Page Pipeline Coordination)

## Current State
- The repository has an empty `:app` module.
- Previous milestones (conceptually) have implemented capture and corner refinement.
- A refined `Quadrilateral` object is available for the full-resolution captured image.

## Scope
- Define `PerspectiveCorrector` interface for image transformation.
- Implement homography computation from 4 source corners to 4 destination corners.
- Implement perspective transformation using OpenCV's `warpPerspective`.
- Implement dynamic output size calculation to maintain document aspect ratio and reasonable resolution.
- Complete the One Page pipeline end-to-end (capture → coordinate mapping → corner refinement → homography → perspective warp → corrected image).
- Proper memory management to release intermediate `Bitmap` and OpenCV `Mat` objects.

## Non-Goals
- Multi-page document generation or processing.
- Image enhancement (brightness/contrast adjustments, binarization) - this will be handled in subsequent milestones.
- Text recognition (OCR).

## Dependencies
- M08: Corner Refinement must be complete.
- OpenCV Android SDK integrated.
- Image capture pipeline providing high-resolution `Bitmap` and associated `Quadrilateral`.

## Components
- `com.yscanner.app.domain.model.Quadrilateral`: Existing data class for document corners.
- `com.yscanner.app.domain.processor.PerspectiveCorrector`: New interface for perspective correction.
- `com.yscanner.app.data.processor.OpenCvPerspectiveCorrector`: Implementation of `PerspectiveCorrector` using OpenCV.
- `com.yscanner.app.domain.usecase.ProcessSinglePageUseCase`: Use case coordinating the end-to-end pipeline.

## Data Flow
1. `ProcessSinglePageUseCase` receives captured `Bitmap` and `Quadrilateral`.
2. Passes `Bitmap` and `Quadrilateral` to `PerspectiveCorrector`.
3. `PerspectiveCorrector` calculates output bounds (width/height) based on `Quadrilateral` edge lengths.
4. Computes homography matrix mapping `Quadrilateral` corners to `[0,0], [width,0], [width,height], [0,height]`.
5. Applies `warpPerspective` via OpenCV.
6. Returns corrected `Bitmap` without excess background.
7. Disposes of intermediate OpenCV `Mat` resources.

## Implementation Steps
1. Create `com.yscanner.app.domain.processor.PerspectiveCorrector` interface with method `correctPerspective(image: Bitmap, corners: Quadrilateral): Bitmap`.
2. Create `com.yscanner.app.data.processor.OpenCvPerspectiveCorrector` implementing `PerspectiveCorrector`.
3. In `OpenCvPerspectiveCorrector`, implement aspect ratio and dimension calculation:
   - Calculate maximum width between top/bottom edges and maximum height between left/right edges of the `Quadrilateral`.
   - Define destination coordinates using the calculated width and height.
4. Implement OpenCV conversion:
   - Convert `Bitmap` to `Mat`.
   - Create source and destination `MatOfPoint2f` objects.
   - Use `Imgproc.getPerspectiveTransform` to compute the homography matrix.
5. Apply transformation:
   - Use `Imgproc.warpPerspective` with the source `Mat` and homography matrix.
   - Convert resulting `Mat` back to a new `Bitmap`.
6. Implement strict `Mat` release lifecycle (`Mat.release()`) in `finally` blocks for all intermediate objects to prevent memory leaks.
7. Implement `com.yscanner.app.domain.usecase.ProcessSinglePageUseCase` to orchestrate capture mapping, refinement (if applicable), and perspective correction.

## Testing
- `OpenCvPerspectiveCorrectorTest`: 
  - `testPerspectiveCorrectionOutputDimensions`: Verifies output Bitmap matches calculated aspect ratio bounds.
  - `testExtremePerspectiveHandling`: Verifies gracefully handles steep angles without crashing.
  - `testMemoryRelease`: Verifies OpenCV Mats are released properly (mocking or memory profiling).
- `ProcessSinglePageUseCaseTest`: Verifies the sequence of operations from input to corrected output.

## Validation
- Process sample images with various known skews; output must be a tightly cropped, rectangular document.
- Use Android Studio Memory Profiler to ensure no native memory leaks occur from OpenCV `Mat` allocations during repeated processing.

## Performance
- CPU/GPU: OpenCV native processing is fast, but large image dimensions can cause CPU spikes. Must run on a background dispatcher.
- RAM: Temporary allocations of full-size `Mat` objects. Proper `.release()` calls are critical to avoid OOM errors.
- Latency: Target < 500ms for high-resolution image correction.

## Failure Cases
- Invalid `Quadrilateral` (e.g., self-intersecting or collinear points): Throw `IllegalArgumentException` or return original image, do not crash native code.
- Out of Memory: Catch `OutOfMemoryError` during `Bitmap` creation and return an error state.

## Acceptance Criteria
- `PerspectiveCorrector` successfully transforms a skewed document into a flat rectangle.
- Output image contains only the document, with no unnecessary background.
- Aspect ratio of the original document is reasonably maintained without abnormal stretching.
- `ProcessSinglePageUseCase` successfully links capture and correction.
- No memory leaks detected in profiling after 50 consecutive transformations.

## Git Checkpoint
`feat: Implement OpenCV perspective correction and One Page pipeline`

## Risks
- Incorrect point ordering in `Quadrilateral` can result in flipped or distorted output.
- High memory usage during native OpenCV conversions could lead to OOM on low-end devices.

## Open Questions
- What is the maximum resolution we should support for the output bitmap? Should we scale down before correction if the input exceeds a certain dimension?
