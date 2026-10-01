# M16: Gutter / Curvature / Dewarp Pipeline

## Objective
Implement curvature estimation and image dewarping for book spreads to flatten curved pages, complete the Two Page processing pipeline, and separate the spread into two distinct, corrected pages.

## Product Requirements
- PRD.md §14.2 (Gutter Detection)
- PRD.md §14.3 (Curvature Modeling)
- PRD.md §14.4 (Dewarping Execution)
- PRD.md §12.2 (Gutter detection)
- PRD.md §12.3 (Curvature modelling)
- PRD.md §12.4 (Dewarping execution)

## Architecture References
- ARCHITECTURE.md §13 (Two Page Pipeline)
- ARCHITECTURE.md §25 (Dewarping Component)
- ARCHITECTURE.md §26 (Image Processing Pipeline)

## Current State
- Book spread detection, page boundary extraction, and basic gutter line detection are implemented from M15.
- The pipeline does not yet support curvature estimation or non-linear dewarping.
- The repository has a default Android scaffold with a single `:app` module.

## Scope
- Define `CurvatureMap` to represent page curvature.
- Implement `CurvatureEstimator` to analyze curvature for left and right pages independently.
- Create a `Dewarper` interface and an OpenCV-based implementation that generates a non-linear remap mesh and applies it using OpenCV's `remap()`.
- Integrate curvature estimation and dewarping into the Two Page pipeline.
- Separate the processed spread into two distinct `PageObject`s (left then right).
- Implement proper memory management to process pages sequentially and release intermediate buffers.

## Non-Goals
- Simple 50% split of the image.
- Advanced shadow/lighting correction in the gutter (handled in enhancement, but not the core dewarp structural correction).
- Dewarping for single-page captures (handled separately or not applicable).

## Dependencies
- M15 (Two Page Detection Pipeline) must be complete.
- Spike S04 (Dewarp Strategy) must be completed to finalize the dewarping algorithm selection.

## Components
- `com.yscanner.core.models.CurvatureMap`
- `com.yscanner.core.processor.Dewarper` (Interface)
- `com.yscanner.core.processor.OpenCvDewarper` (Implementation)
- `com.yscanner.core.processor.CurvatureEstimator`
- `com.yscanner.core.processor.TwoPagePipeline` (Updates)

## Data Flow
1. `ImageSource` (spread) + `pageBoundary` (left/right) + `gutterLine` -> `CurvatureEstimator` -> `CurvatureMap` (left), `CurvatureMap` (right).
2. `ImageSource` + `pageBoundary` (left) + `CurvatureMap` (left) + `gutterLine` -> `Dewarper` -> `Bitmap` (dewarped left).
3. `ImageSource` + `pageBoundary` (right) + `CurvatureMap` (right) + `gutterLine` -> `Dewarper` -> `Bitmap` (dewarped right).
4. Dewarped left -> perspective correction -> enhancement -> `PageObject` (Left).
5. Dewarped right -> perspective correction -> enhancement -> `PageObject` (Right).

## Implementation Steps
1. Create `com.yscanner.core.models.CurvatureMap` data class with properties `meshWidth`, `meshHeight`, and `controlPoints: FloatArray`.
2. Define the `com.yscanner.core.processor.Dewarper` interface with a `dewarp` method taking an `ImageSource`, page boundary polygon, `CurvatureMap`, and gutter line, returning a dewarped `Bitmap`.
3. Implement `com.yscanner.core.processor.CurvatureEstimator` that processes the page boundary and spread content to output a `CurvatureMap`. Handle left and right pages with independent geometry.
4. Implement `com.yscanner.core.processor.OpenCvDewarper` that:
   - Generates X and Y mapping matrices based on the `CurvatureMap`.
   - Uses OpenCV's `Imgproc.remap()` to perform non-linear dewarping.
5. Update `com.yscanner.core.processor.TwoPagePipeline` to orchestrate the new flow:
   - Detect spread, boundaries, gutter.
   - Estimate curvature for left and right pages.
   - Dewarp left page, then dewarp right page.
   - Apply perspective correction and enhancement.
   - Return two separate `PageObject` instances ordered Left then Right.
6. Implement memory management in `TwoPagePipeline` to process the left page fully and release buffers before starting the right page, minimizing peak memory.

## Testing
- `CurvatureMapTest`: Verify data structure integrity and bounds.
- `CurvatureEstimatorTest`: Supply mock spread data and boundaries, verify independent curvature models for left and right pages.
- `OpenCvDewarperTest`: Provide a test image with known curvature and verify the output is flattened using `remap`.
- `TwoPagePipelineTest`: Verify end-to-end integration, ensuring the output is exactly two `PageObject` instances (left, right). Verify memory release calls via mocks or memory tracking.

## Validation
- Feed test images of book spreads with varying degrees of curvature, imperfect opening angles (< 180°), and deep/dark gutters.
- Visual inspection of the dewarped bitmaps to ensure text lines are straightened and proportions are realistic.

## Performance
- Non-linear remapping is computationally expensive. Monitor CPU/GPU usage during OpenCV `remap()`.
- Peak RAM usage must be strictly managed by processing one page at a time. Left page intermediate buffers must be released before right page processing begins.

## Failure Cases
- If curvature estimation fails (e.g., cannot find text lines or page edges), fallback to flat perspective transform using boundaries.
- If gutter is too dark to resolve, extrapolate from visible page boundaries.

## Acceptance Criteria
- Two Page pipeline successfully inputs a curved spread and outputs two flattened, separate `PageObject`s.
- `remap()` based non-linear dewarping is implemented and functional.
- Left and right pages are modeled and dewarped independently.
- Peak memory does not exceed the budget (measured via profiler), processing one page at a time.
- Default output order is consistently left, then right.

## Git Checkpoint
`feat: implement curvature estimation and dewarping for two-page pipeline`

## Risks
- OpenCV `remap()` might be too slow for high-resolution images on lower-end devices.
- Curvature estimation may be inaccurate on pages with sparse text or complex graphics.

## Open Questions
- What resolution should the dewarp mesh have for optimal performance vs. quality balance?
- Will we need a downscaled image for curvature estimation to save time? (To be decided in Spike S04).
