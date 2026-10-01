# M15: Two Page Book-Spread Geometry

## Objective
Implement the core geometry detection pipeline for two-page book spreads, identifying page boundaries and extracting the central gutter line to prepare for subsequent 3D unrolling and dewarping.

## Product Requirements
PRD.md §12, §14 (Two Page mode)

## Architecture References
ARCHITECTURE.md §13, §24-26

## Current State
The repository currently supports single-page geometry and perspective correction (from M09). There is no logic for detecting book spreads, gutters, or multiple pages within a single image.

## Scope
- Domain models for spread analysis (`SpreadAnalysis`, `GutterLine`).
- Interface and implementation for `SpreadAnalyzer`.
- Detection logic for whether an image contains a book spread (`BookSpreadDetector`).
- Boundary detection for left and right pages (`PageBoundaryDetector`).
- Gutter line/curve extraction.
- Integration into the initial Two Page pipeline steps.

## Non-Goals
- Image dewarping or 3D unrolling (reserved for M16).
- Final cropping, splitting, and saving of the individual pages.
- UI overlay rendering for the camera preview.

## Dependencies
- M09: Perspective Correction (basic OpenCV pipelines and geometry utilities).

## Components
- `com.localscan.core.geometry.SpreadAnalysis`: Data class containing `isSpread`, `gutterLine`, `leftPageBoundary`, `rightPageBoundary`, `curvatureMap`.
- `com.localscan.core.geometry.GutterLine`: Data class defining `top` point, `bottom` point, and `curvature` profile.
- `com.localscan.core.geometry.SpreadAnalyzer`: Interface defining `fun analyze(image: ImageSource): SpreadAnalysis`.
- `com.localscan.core.geometry.BookSpreadDetector`: Detects open book spreads vs. flat single pages.
- `com.localscan.core.geometry.PageBoundaryDetector`: Finds distinct left and right page polygons.
- `com.localscan.core.geometry.DefaultSpreadAnalyzer`: Implementation orchestrating the pipeline.

## Data Flow
1. `ImageSource` arrives from capture.
2. `BookSpreadDetector` evaluates the image (is it a book spread?).
3. If true, `PageBoundaryDetector` extracts `leftPageBoundary` and `rightPageBoundary`.
4. Gutter extraction identifies the `GutterLine` using center shadows/contours.
5. `SpreadAnalyzer` packages results into a `SpreadAnalysis` output.
6. The output feeds downstream to M16 (Dewarping).

## Implementation Steps
1. Create `SpreadAnalysis.kt` and `GutterLine.kt` in `com.localscan.core.geometry`.
2. Define the `SpreadAnalyzer.kt` interface.
3. Implement `BookSpreadDetector.kt` using OpenCV to identify dual large contour regions and central valley shadows.
4. Implement `PageBoundaryDetector.kt` to extract independent left and right quadrilaterals/polygons.
   - Handle independent geometries (tilt, varying widths).
   - Ensure left -> right default ordering.
5. Implement gutter detection logic, extracting the physical spine/gutter, resilient to dark shadows and non-180° openings.
6. Create `DefaultSpreadAnalyzer.kt` tying the detectors together.

## Testing
- `BookSpreadDetectorTest`: Verify true positives for spread images and true negatives for single pages.
- `PageBoundaryDetectorTest`: Provide mock contours; verify correct left/right separation and independent geometries.
- `GutterLineTest`: Verify curvature mapping on synthetic shadow gradients.

## Validation
- Feed a dataset of imperfect book images (tilted, dark gutter, non-180° open, non-identical left/right pages) into the analyzer and visualize the output contours and gutter lines.

## Performance
- Gutter and boundary detection must process in under 400ms per high-res image on average hardware to avoid pipeline bottlenecks.
- Use downscaled OpenCV Mats for structural detection, mapping coordinates back to the original resolution.

## Failure Cases
- If a spread is not detected, fallback to single-page processing.
- If the gutter cannot be found, approximate a straight line between the closest edges of the left and right page boundaries.

## Acceptance Criteria
- `SpreadAnalyzer` successfully returns a populated `SpreadAnalysis` for typical book spread photos.
- The pipeline does not assume identical geometry for left and right pages (no simple 50% split).
- Gutter line gracefully handles tilts and shadows.

## Git Checkpoint
`feat(geometry): implement two-page book-spread detection pipeline`

## Risks
- Extreme page curls near the gutter may obscure the actual spine, confusing the gutter detection.
- Very dark center lighting might cause the left and right pages to bleed into the background.

## Open Questions
- Can we achieve robust gutter detection purely with traditional OpenCV heuristics (Canny/Sobel + Hough/Contours), or will we need a lightweight TFLite model specifically for gutter segmentation?
