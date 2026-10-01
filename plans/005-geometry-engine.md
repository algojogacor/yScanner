# M04: Geometry Engine

## Objective
Implement the core geometry pipeline that translates boundary points and segmentation masks into a best-fit convex enclosing quadrilateral. This standardizes all document shapes into a crop-ready format, handling complex/concave documents by finding their convex outer envelope first.

## Product Requirements
PRD §11

## Architecture References
ARCHITECTURE.md §4, §20-23

## Current State
The `:geometry` module exists but is empty. The detection module produces `List<DocumentCandidate>` from segmentation masks (completed in M03).

## Scope
- Implement `BoundaryExtractor` to convert segmentation masks to clean boundary points.
- Implement `EnvelopeComputer` to calculate the convex outer envelope from boundary points using OpenCV.
- Implement `QuadrilateralFitter` to derive the best-fit enclosing `Quadrilateral` from the envelope.
- Create `Quadrilateral` data class with math operations (area, isConvex, contains, toFloatArray).
- Create a basic `CornerRefiner` interface (stub implementation).
- Integrate OpenCV for contour operations and convex hull calculation.

## Non-Goals
- Full implementation of `CornerRefiner` (planned for M08).
- Image cropping or perspective transformation (planned for M05).
- Machine learning model execution (done in M03).

## Dependencies
- M03: Machine Learning Pipeline (segmentation output).
- Spike S02: Quadrilateral Fitting (algorithm selection).

## Components
- `com.yscanner.geometry.models.Quadrilateral`
- `com.yscanner.geometry.interfaces.BoundaryExtractor`
- `com.yscanner.geometry.interfaces.EnvelopeComputer`
- `com.yscanner.geometry.interfaces.QuadrilateralFitter`
- `com.yscanner.geometry.interfaces.CornerRefiner`
- `com.yscanner.geometry.impl.OpenCvBoundaryExtractor`
- `com.yscanner.geometry.impl.OpenCvEnvelopeComputer`
- `com.yscanner.geometry.impl.StandardQuadrilateralFitter`
- `com.yscanner.geometry.impl.StubCornerRefiner`

## Data Flow
Segmentation Mask (from M03) -> `BoundaryExtractor` -> Boundary Points (`List<PointF>`) -> `EnvelopeComputer` -> Convex Outer Envelope (`List<PointF>`) -> `QuadrilateralFitter` -> `Quadrilateral`.

## Implementation Steps
1. Create `com.yscanner.geometry.models.Quadrilateral` data class with properties `topLeft`, `topRight`, `bottomRight`, `bottomLeft` and methods `area()`, `isConvex()`, `contains()`, `toFloatArray()`.
2. Define interfaces: `BoundaryExtractor`, `EnvelopeComputer`, `QuadrilateralFitter`, `CornerRefiner` in `com.yscanner.geometry.interfaces`.
3. Implement `OpenCvBoundaryExtractor` for contour extraction and noise filtering of segmentation masks.
4. Implement `OpenCvEnvelopeComputer` utilizing OpenCV's `convexHull` to find the outer envelope.
5. Implement `StandardQuadrilateralFitter` using the algorithm selected from Spike S02 to map the envelope to an enclosing `Quadrilateral`.
6. Implement `StubCornerRefiner` that simply returns the approximate `Quadrilateral` untouched.
7. Create the main geometry pipeline facade that wires these components together to process a mask to a `Quadrilateral`.

## Testing
- `QuadrilateralMathTest`: Unit tests for `area()`, `isConvex()`, `contains()`, and bounds edge cases.
- `OpenCvEnvelopeComputerTest`: Verify convex hull extraction on synthetic point sets (normal, degenerate, collinear).
- `StandardQuadrilateralFitterTest`: Verify best-fit quad on perfect rectangles, trapezoids, and irregular convex shapes.
- `GeometryPipelineIntegrationTest`: End-to-end test from a mock segmentation mask through boundary extraction to final `Quadrilateral`.

## Validation
- Unit test pass rate.
- Manual verification of generated quads on sample masks with concave/complex document shapes to ensure the "enclosing quad" invariant is maintained.

## Performance
- Geometry operations (extraction, hull, fitting) must execute in under 15ms per frame on average mid-range hardware to support real-time preview tracking.
- Memory allocations during processing should be minimized (reuse OpenCV Mat objects).

## Failure Cases
- No boundary/contour found: Return a fallback quad (e.g., full image bounds or empty/null depending on pipeline design).
- Degenerate boundary (< 3 points): Return fallback quad.
- Very small documents (area < threshold): Filter out or return fallback.
- Collinear envelope points: Handled gracefully by `QuadrilateralFitter` without crashing.

## Acceptance Criteria
- `Quadrilateral` class exists with passing math tests.
- Complex/concave document boundaries correctly resolve to a convex enclosing quadrilateral.
- Pipeline can process a segmentation mask into a `Quadrilateral` without crashing on edge cases.
- Integration tests pass.

## Git Checkpoint
`feat(geometry): implement core geometry pipeline and quadrilateral fitting`

## Risks
- OpenCV JNI overhead might impact performance if called too frequently per frame.
- The fitted quadrilateral for highly concave shapes might include excessive background, which could be visually confusing, though expected by product design.

## Open Questions
- What fallback strategy should be used when the geometry pipeline completely fails on a frame?
- Should the `Quadrilateral` coordinates be normalized [0,1] or absolute image pixels? (Assume absolute pixels for now unless specified).
