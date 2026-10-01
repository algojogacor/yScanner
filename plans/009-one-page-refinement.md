# M08: One Page Full-Resolution Refinement

## Objective
Refine the document boundary detection geometry using the high-resolution captured image, enhancing precision for the final crop without loading the full 12MP+ image into memory.

## Product Requirements
- PRD.md §9 (Full-Resolution Geometry)
- PRD.md §11.4 (Corner Refinement)

## Architecture References
- ARCHITECTURE.md §20-22

## Current State
The camera successfully captures high-resolution images (M07), and initial document geometry is detected on low-resolution analysis frames (M02). The baseline geometry coordinate mapping infrastructure is in place.

## Scope
- Creation of the `ImageSource` interface for abstracting image data access.
- Implementation of a file-backed `ImageSource` using `BitmapRegionDecoder`.
- Implementation of `CornerRefiner` to extract ROIs (Regions of Interest), perform edge/gradient analysis, and calculate refined corner positions.
- Full coordinate pipeline integration: mapping low-res analysis coordinates to capture coordinates, extracting ROIs, detecting edges, and producing a final refined `Quadrilateral`.

## Non-Goals
- Multi-page batch refinement (deferred to a later milestone).
- UI for manual crop adjustment (purely domain/pipeline logic in this milestone).
- Final perspective transformation/cropping (will be handled in a subsequent milestone).

## Dependencies
- Spike S06 (ROI Decoding) - strategy for accessing full-res subsets.
- M07 (High-Res Image Capture) - source image availability.
- M02 (Document Detection Engine) - base geometry and mapping.

## Components
- `com.yscanner.domain.image.ImageSource` (Interface)
- `com.yscanner.data.image.FileImageSource` (Implementation of `ImageSource`)
- `com.yscanner.domain.geometry.CornerRefiner` (Domain service)
- OpenCV Android SDK (`Imgproc`, `Core` modules)

## Data Flow
1. Mapped `Quadrilateral` (from low-res frame to high-res capture coords) is passed to `CornerRefiner`.
2. `CornerRefiner` calculates ~200-400px ROI bounding boxes around each of the 4 corners.
3. `CornerRefiner` requests decoded ROIs from `ImageSource.decodeRegion(rect)`.
4. `FileImageSource` uses `BitmapRegionDecoder` to load only the 4 small `Bitmap` regions into memory.
5. OpenCV applies Canny edge detection and line intersection math on each ROI.
6. Local refined coordinates are mapped back to the global high-res coordinate space.
7. A final refined `Quadrilateral` is returned.

## Implementation Steps
1. **Define `ImageSource` Interface:** Create `com.yscanner.domain.image.ImageSource` defining `width`, `height`, `decodeRegion(Rect)`, `decodeFull()`, and `release()`.
2. **Implement `FileImageSource`:** Create `com.yscanner.data.image.FileImageSource` using `BitmapRegionDecoder`. Ensure thread-safety and proper resource release.
3. **Implement ROI Calculation:** In `CornerRefiner`, create a method to calculate `Rect` bounds for a given `Point`, ensuring it handles image boundary constraints (clamping to `0` and `width/height`).
4. **Implement ROI Edge Detection:** In `CornerRefiner`, convert the ROI `Bitmap` to an OpenCV `Mat`, apply grayscale conversion, Gaussian blur, and Canny edge detection.
5. **Implement Corner Localization:** Use Hough Transform or contour approximation to find the two dominant intersecting lines within the ROI, calculating the intersection point.
6. **Coordinate Translation:** Translate the localized corner back to the absolute coordinate system of the full image.
7. **Refinement Pipeline Integration:** Connect the pieces so the input is an approximate `Quadrilateral` and `ImageSource`, and the output is a refined `Quadrilateral`.

## Testing
- `FileImageSourceTest`: Verify `decodeRegion` returns correctly sized bitmaps and handles boundary conditions.
- `CornerRefinerTest`: Supply mocked ROIs with obvious lines and verify intersection math calculates the correct corner.
- `CoordinateMappingTest`: Validate translation from ROI-local coordinates to global image coordinates.

## Validation
- Memory profiling (e.g., using Android Studio Profiler) to ensure memory spikes remain low (< 15MB) during refinement compared to loading a full 12MP bitmap.
- Visual validation by overlaying the refined geometry on the high-res image.

## Performance
- Memory: RAM usage should not exceed 20MB for the decoding and OpenCV processing of the 4 ROIs.
- Latency: The entire refinement pipeline should execute in under 500ms on a mid-range device.

## Failure Cases
- **Weak Edges:** If OpenCV cannot find intersecting lines in an ROI, fallback to the original mapped corner coordinate.
- **Out of Bounds ROI:** Clamped safely to the image edges.
- **Decoder Failure:** If `BitmapRegionDecoder` fails (e.g., corrupt file), fallback to returning the original geometry and log the error.

## Acceptance Criteria
- `ImageSource` successfully reads partial regions of an image file without OOM exceptions.
- `CornerRefiner` successfully tightens bounding box coordinates using ROI edge detection.
- Memory constraints are proven to be met via profiling.
- Edge cases (corners near edges, noisy backgrounds) are handled gracefully via fallbacks.

## Git Checkpoint
`feat(geometry): implement full-res corner refinement via ROI decoding`

## Risks
- `BitmapRegionDecoder` has known inconsistencies on certain OEM devices and older Android versions; fallback strategies may be required.
- High perspective distortion might cause the actual corner to lie outside the estimated 200-400px ROI.

## Open Questions
- What is the optimal static ROI size (e.g., 200px vs 400px), or should the ROI size be dynamic based on the confidence score of the low-res detection?
