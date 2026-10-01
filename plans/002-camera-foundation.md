# M01: Camera Foundation

## Objective
Establish the foundational CameraX integration to support real-time document preview, frame analysis, and high-resolution photo capture with proper lifecycle management.

## Product Requirements
- 4.2 Auto-Capture Mode
- 4.3 Manual Capture Mode
- 4.5 Flash Modes
- 5.1 Performance Requirements (Capture Latency)

## Architecture References
- 6.0 Core Components (Camera Module)
- 7.1 Camera Architecture
- 8.3 Threading Model

## Current State
The repository has an initialized multi-module Gradle structure with an empty `:camera` module and `:app` module. No CameraX dependencies or camera logic exist.

## Scope
- Integration of CameraX dependencies in the `:camera` module.
- Implementation of `CameraController`, `FrameAnalyzer`, and `CaptureManager` interfaces.
- Configuration of CameraX `Preview`, `ImageAnalysis`, and `ImageCapture` use cases.
- Camera permission handling.
- Basic Compose UI in the `:app` module for the camera screen (`PreviewView` wrapper, shutter button, flash toggle).
- Focus and metering on tap.
- Flash mode configuration (off / on / torch) and state management.

## Non-Goals
- Real-time document boundary detection or cropping (this belongs to the detection milestone).
- Image processing or PDF generation.
- Gallery or captured image review screens.

## Dependencies
- M00: Project initialization (completed).
- AndroidX CameraX libraries (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`).
- Jetpack Compose for UI.

## Components
- `com.localscan.camera.CameraController`: Interface and `CameraXControllerImpl` implementation coordinating use cases.
- `com.localscan.camera.FrameAnalyzer`: Interface for processing `ImageProxy` objects.
- `com.localscan.camera.CaptureManager`: Interface for managing high-res file-backed capture.
- `com.localscan.camera.executor.CameraExecutor`: Dedicated thread executor for image analysis to prevent blocking the main thread.
- `com.localscan.app.ui.camera.CameraScreen`: Compose UI tying together the preview, shutter, and flash controls.
- `com.localscan.app.ui.camera.CameraPreview`: Compose wrapper for `PreviewView`.

## Data Flow
1. **Preview**: Camera2 → `Preview` use case → `PreviewView` (UI).
2. **Analysis**: Camera2 → `ImageAnalysis` use case (STRATEGY_KEEP_ONLY_LATEST) → `CameraExecutor` → `FrameAnalyzer` → `ImageProxy.close()`.
3. **Capture**: UI Action (Shutter) → `CameraController` → `ImageCapture` use case → `CaptureManager` → File I/O → `CaptureResult`.

## Implementation Steps
1. Add CameraX dependencies (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`) to `d:\Projects\pdfscanner\camera\build.gradle.kts`.
2. Define `com.localscan.camera.FrameAnalyzer`, `CaptureManager`, and `CameraController` interfaces in the `:camera` module.
3. Implement `com.localscan.camera.CameraXControllerImpl` extending `CameraController`:
   - Initialize `ProcessCameraProvider`.
   - Bind `Preview`, `ImageAnalysis`, and `ImageCapture` to the provided `LifecycleOwner`.
   - Configure `ImageAnalysis` with `STRATEGY_KEEP_ONLY_LATEST` and a dedicated single-thread `Executor`.
   - Implement tap-to-focus using `MeteringPointFactory` and `FocusMeteringAction`.
   - Expose flash mode state (Off, On, Torch) and implement switching logic via `CameraControl.enableTorch()` and `ImageCapture.setFlashMode()`.
4. Implement `com.localscan.camera.FileCaptureManager` extending `CaptureManager`:
   - Use `ImageCapture.takePicture(OutputFileOptions, Executor, OnImageSavedCallback)`.
   - Write output to a temporary file.
   - Return `CaptureResult(file, rotationDegrees)` via Kotlin Coroutines (`suspendCancellableCoroutine`).
5. Create `com.localscan.app.ui.camera.CameraPreview` in `:app`:
   - Use `AndroidView` to wrap `androidx.camera.view.PreviewView`.
   - Attach the `Preview` use case surface provider.
6. Create `com.localscan.app.ui.camera.CameraScreen` in `:app`:
   - Request `android.permission.CAMERA`.
   - Render `CameraPreview`.
   - Overlay a Shutter `IconButton`.
   - Overlay a Flash toggle `IconButton`.
7. Wire `CameraScreen` to an empty `com.localscan.app.MainActivity` for demonstration.

## Testing
- `com.localscan.camera.CameraXControllerTest`: Verify lifecycle binding, use case configuration, and flash mode toggling.
- `com.localscan.camera.FileCaptureManagerTest`: Mock `ImageCapture` and verify file creation and rotation metadata handling.
- `com.localscan.app.ui.camera.CameraScreenTest`: Compose UI tests verifying permission request flow and UI component rendering (shutter, flash buttons).

## Validation
- Verify camera preview renders correctly in portrait and landscape orientations.
- Verify `ImageProxy` instances are immediately closed by checking for memory leaks in Android Studio Profiler.
- Verify captured images are saved to disk with correct EXIF orientation.
- Verify tap-to-focus triggers auto-focus (visually or via camera state).

## Performance
- CPU: `ImageAnalysis` runs on a background thread without impacting main thread UI performance (60fps target for Compose).
- Memory: No accumulation of `ImageProxy` frames (`STRATEGY_KEEP_ONLY_LATEST`). Captured images are streamed directly to disk, avoiding full-res `Bitmap` allocations in RAM.
- Latency: Shutter-to-file-save latency < 1000ms.

## Failure Cases
- Camera Permission Denied: UI displays rationale and request button.
- Camera Hardware Unavailable: Catch `CameraInfoUnavailableException` and display error state.
- File Write Failure during Capture: Propagate `ImageCaptureException` and show SnackBar in UI.

## Acceptance Criteria
- App launches to a camera screen requesting permissions.
- Live camera preview is visible.
- Tapping the screen triggers focus/metering.
- Tapping the flash button cycles through Off, On, and Torch modes.
- Tapping the shutter button successfully captures an image and saves it to the app's cache directory without OOM crashes.
- Profiling shows no memory leaks from unclosed `ImageProxy` objects during continuous preview.

## Git Checkpoint
`feat: implement CameraX foundation, preview, analysis, and capture`

## Risks
- Device-specific CameraX quirks (e.g., specific Samsung/Xiaomi models with incorrect EXIF rotation).
- Main thread blocking if `ImageAnalysis` executor is misconfigured.

## Open Questions
- Should the flash state be persisted in `DataStore` immediately, or handled as an in-memory state for this milestone?
