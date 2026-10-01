# M03: Document Detector

## Objective
Implement the local AI document segmentation module to detect document boundaries in camera frames, using a lightweight on-device ML model.

## Product Requirements
- PRD 6 (Document Detection)
- PRD 6.1 (On-device Processing)
- PRD 6.2 (Latency & Frame Rate)

## Architecture References
- ARCHITECTURE.md §9 (Detection Module)
- ARCHITECTURE.md §10 (Data Flow)
- ARCHITECTURE.md §11 (Performance Budget)

## Current State
The `:detection` module exists but is empty. The `:camera` module from M01/M02 provides `ImageAnalysis` frames and coordinate transforms via `FrameAnalyzer`.

## Scope
- Define `SegmentationModel` interface and implementations for LiteRT/TFLite.
- Implement model loading, lifecycle management, and off-main thread inference execution.
- Implement frame-to-model input conversion (ImageProxy → ByteBuffer with correct rotation).
- Define `ModelInput`, `ModelOutput`, `SegmentationOutput`, and `DocumentCandidate` data types.
- Implement `CandidateExtractor` to extract boundaries from segmentation masks (including thresholding, noise cleanup, and connected component analysis).
- Integrate with `FrameAnalyzer` from the `:camera` module.
- Implement CPU fallback for hardware acceleration failure.
- Implement Model file management (assets or downloaded).

## Non-Goals
- Real-time UI rendering of the detection mask (this will be done in the UI milestone).
- Final selection of the specific model (this depends on Spike S01).
- OCR or text extraction.
- Edge refinement or final cropping (handled by processing module).

## Dependencies
- M01/M02 Camera Module (for `ImageAnalysis` frames and `FrameAnalyzer`).
- Spike S01 (Model Selection) completion, to determine model architecture and runtime.

## Components
- `com.localscan.detection.SegmentationModel` (interface)
- `com.localscan.detection.tflite.TfLiteSegmentationModel` (implementation)
- `com.localscan.detection.CandidateExtractor` (interface)
- `com.localscan.detection.impl.OpenCvCandidateExtractor` (implementation using OpenCV for contour finding)
- `com.localscan.detection.ModelInput` (data class)
- `com.localscan.detection.SegmentationOutput` (data class)
- `com.localscan.detection.DocumentCandidate` (data class: id, boundary, confidence, area)
- `com.localscan.detection.FrameProcessor` (integrates model and extractor)
- `com.localscan.detection.ModelManager` (handles model file management)

## Data Flow
1. `FrameAnalyzer` (from `:camera`) emits an `ImageProxy`.
2. `FrameProcessor` converts `ImageProxy` to `ModelInput` (ByteBuffer/Bitmap), handling rotation.
3. `SegmentationModel.infer(ModelInput)` executes the ML model.
4. `SegmentationModel` returns `SegmentationOutput` (mask and confidence).
5. `CandidateExtractor.extract(SegmentationOutput)` processes the mask (thresholding, contour detection, noise removal).
6. Returns `List<DocumentCandidate>` to the camera/UI layers.

## Implementation Steps
1. Create data models in `com.localscan.detection`: `ModelInput`, `SegmentationOutput`, `DocumentCandidate`.
2. Define interfaces: `SegmentationModel`, `CandidateExtractor` in `com.localscan.detection`.
3. Implement `TfLiteSegmentationModel` in `com.localscan.detection.tflite`:
   - Setup LiteRT/TFLite interpreter.
   - Implement `suspend fun infer(input: ModelInput): SegmentationOutput` on an I/O or Default dispatcher.
   - Implement CPU fallback mechanisms (e.g. NNAPI/GPU delegates with try-catch).
4. Implement input conversion in `com.localscan.detection.util.ImageConverter`:
   - `ImageProxy` to `ByteBuffer` taking into account model dimensions and input format (e.g. RGB, normalization).
5. Implement `OpenCvCandidateExtractor` in `com.localscan.detection.impl`:
   - Threshold the mask.
   - Use OpenCV to find contours (`findContours`).
   - Filter contours by area and shape (connected component analysis).
   - Convert to `DocumentCandidate` objects.
6. Implement `ModelManager` in `com.localscan.detection` for loading model from assets and lifecycle management (load once, reuse, release on scope exit).
7. Create `FrameProcessor` to tie everything together.

## Testing
- `TfLiteSegmentationModelTest`: Verify model loads properly and runs inference on dummy inputs. Verify delegate fallback works.
- `OpenCvCandidateExtractorTest`: Provide a dummy binary mask and verify correct contours/boundaries are extracted.
- `ImageConverterTest`: Verify RGB conversion and scaling calculations.
- `FrameProcessorTest`: Integration test simulating a frame drop/processing pipeline.

## Validation
- Unit tests pass.
- Measure inference time on real devices (must be ≤ 50ms on mid-range).
- Memory profiling to ensure the interpreter instances do not leak and model memory budget fits within 600-700MB.

## Performance
- Expected inference latency: ≤ 50ms on mid-range devices.
- Analysis FPS: ≥ 15 FPS.
- Memory: TFLite model size expected to be 5-20MB; runtime footprint around 50-150MB. Overall budget fits within the 600-700 MB limit.
- CPU/GPU: Uses GPU/NNAPI delegate if available to reduce CPU load.

## Failure Cases
- Out of Memory (OOM): Handle gracefully, release model, fallback to lower res or notify user.
- Delegate Init Failure: Catch exceptions during GPU/NNAPI delegate initialization and fallback to CPU.
- Invalid Frame: Drop frame, log warning, do not crash.

## Acceptance Criteria
- `:detection` module builds.
- Model successfully loads and predicts a mask given a test image.
- Document candidates are extracted from the mask correctly.
- Average inference time is under 50ms on target devices.
- Tests cover core data flows.

## Git Checkpoint
`feat(detection): implement TFLite segmentation and boundary extraction`

## Risks
- OpenCV library size might bloat the APK. (Mitigation: use pre-compiled minimal OpenCV or implement custom contour finding if size is too large).
- TFLite delegate initialization is notoriously flaky on some low-end devices.

## Open Questions
- What are the exact dimensions required by the chosen model from Spike S01?
- Do we need to package multiple model resolutions for different performance tiers?
