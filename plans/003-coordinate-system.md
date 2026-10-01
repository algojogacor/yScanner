# M02: Coordinate System

## Objective
Implement a robust, matrix-based coordinate transformation system to map points between the various camera, view, and document coordinate spaces. This ensures that coordinates detected on a low-resolution analysis frame can be accurately translated to the high-resolution capture frame and the screen viewport, regardless of device orientation or aspect ratio mismatches.

## Product Requirements
PRD §10-11

## Architecture References
ARCHITECTURE.md §16-19

## Current State
The `:camera` module exists with a basic CameraX implementation including Preview, ImageAnalysis, and ImageCapture (M01). However, there is no logic to translate coordinates between these different resolution and aspect-ratio streams.

## Scope
- Define the `CoordinateTransformer` interface for mapping points between spaces.
- Implement matrix-based transformations for 7 coordinate spaces: Sensor, ImageAnalysis, Preview/View, Normalized (0..1), ImageCapture, Processed-image, and PDF.
- Handle device rotations (0°, 90°, 180°, 270°) and device orientations (portrait, landscape).
- Handle mismatched aspect ratios (e.g., 4:3 analysis vs 16:9 capture) and viewport crop rectangles.
- Integrate CameraX `TransformationInfo` and crop rects where available.
- Create comprehensive unit tests verifying coordinate mappings across all rotation and aspect ratio combinations.

## Non-Goals
- Real-time UI rendering of the detection polygon (will be handled in a later UI milestone).
- Actual ML edge detection execution.
- Image processing or perspective warping (cropping the actual bitmap).

## Dependencies
- M01: Core Camera implementation must be complete to provide the CameraX use cases and their respective resolutions/crop rects.

## Components
- `com.localscan.camera.transform.CoordinateTransformer`: Interface defining the transformation contracts.
- `com.localscan.camera.transform.CameraCoordinateTransformer`: Implementation using Android `Matrix` and CameraX metadata.
- `com.localscan.camera.transform.PointF`: Data class or alias for points.

## Data Flow
1. ML model produces points in ImageAnalysis coordinate space (e.g., 640x480).
2. Points are passed to `CoordinateTransformer.analysisToNormalized()`.
3. Normalized points can be passed to `CoordinateTransformer.normalizedToPreview()` for UI drawing.
4. Upon capture, normalized points are passed to `CoordinateTransformer.normalizedToCapture()` for cropping the high-resolution image.

## Implementation Steps
1. **Define Core Interfaces**: Create `com.localscan.camera.transform.CoordinateTransformer` containing methods like `analysisToSensor`, `sensorToPreview`, `sensorToCapture`, `analysisToCapture`, and normalized conversions.
2. **Implement Matrix Utilities**: Create helper functions for generating Android `Matrix` objects that apply rotation, scaling (Center-Crop/Fit), and translation based on source and destination `Size` and `rotationDegrees`.
3. **Implement Transformer**: Create `com.localscan.camera.transform.CameraCoordinateTransformer` implementing the interface. Use CameraX's `ImageProxy.cropRect`, `ImageProxy.imageInfo.rotationDegrees`, and view `TransformationInfo` if available, or fallback to manual matrix calculations.
4. **Normalized Coordinates**: Implement the mapping to/from normalized coordinates (0.0 to 1.0) as an intermediary space to simplify analysis-to-capture transformations.
5. **PDF Coordinates**: Implement mapping to standardized PDF dimensions (e.g., A4 at 72 PPI) for final output space calculations.
6. **Write Unit Tests**: Create `CameraCoordinateTransformerTest` covering specific known input/output pairs for 0, 90, 180, and 270-degree rotations, and 4:3 to 16:9 aspect ratio conversions.

## Testing
- `CameraCoordinateTransformerTest`:
  - `testAnalysisToCapture_sameAspectRatio_0deg`: Verifies simple scaling.
  - `testAnalysisToCapture_differentAspectRatio_90deg`: Verifies scaling, rotation, and translation (crop offset).
  - `testNormalizedConversions`: Verifies that `spaceA -> Normalized -> spaceA` returns the original points.
  - `testPreviewToSensor`: Verifies UI touch coordinates map back to sensor coordinates correctly.

## Validation
- Mathematical correctness of the `Matrix` transformations verified by parameterized unit tests.
- Visual validation in subsequent milestones will confirm alignment, but for this milestone, unit test assertions are the primary validation.

## Performance
- Negligible CPU/RAM impact. Coordinate transformations involve simple 3x3 matrix multiplications on 4 points (polygon corners), running in less than 1ms.

## Failure Cases
- Missing crop rect or rotation metadata from CameraX: Transformer should fallback to default assumptions (full frame, 0 degree rotation) and log a warning.
- Invalid geometries (e.g., zero width/height): Should fail gracefully, returning 0,0 or throwing `IllegalArgumentException` depending on context.

## Acceptance Criteria
- `CoordinateTransformer` interface and implementation are complete.
- All 7 coordinate spaces are represented in the API.
- Unit tests pass for all device rotations and aspect ratio mismatches.
- No naive `x * (captureWidth / analysisWidth)` scaling exists in the codebase.

## Git Checkpoint
`feat: implement matrix-based coordinate transformation system`

## Risks
- CameraX OEM quirks: Some devices report incorrect crop rects or sensor rotations. The implementation may need to be defensive or rely on standard Matrix math based on output sizes rather than internal CameraX metadata in edge cases.

## Open Questions
- Should we use CameraX's experimental `CoordinateTransform` API or stick to our own `Matrix` based implementation? (Recommendation: Stick to our own for tighter control and less reliance on experimental APIs, unless CameraX perfectly fits all our spaces).
