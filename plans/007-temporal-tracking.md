# M06: Temporal Tracking & Stable Overlay

## Objective
Stabilize detected document quadrilaterals across frames to eliminate visual jitter and provide a responsive, real-time AR overlay on the camera preview that reflects the current tracking state.

## Product Requirements
- PRD.md §7: Temporal tracking (smoothing raw detection).
- PRD.md §8: Live overlay (UI mapping to physical document).

## Architecture References
- ARCHITECTURE.md §6: Temporal Tracking.
- ARCHITECTURE.md §15: UI / Compose Integration.

## Current State
The app (from M05) selects a `TrackedTarget` from raw contour candidates, but output is evaluated independently per frame, resulting in high jitter and sudden geometry jumps. No stabilization or visual overlay exists.

## Scope
- Define `TemporalTracker` interface and `SmoothedTarget` data class.
- Implement tracking state machine (`NO_TARGET`, `ACQUIRING`, `TRACKING`, `STABLE`, `LOST`).
- Implement corner-by-corner temporal smoothing using the algorithm selected in Spike S03 (e.g., One Euro Filter or Exponential Moving Average).
- Build a Compose Canvas-based AR overlay (`DocumentOverlay`).
- Implement coordinate transformation to map from the image analysis resolution to the Compose preview resolution.
- Create a Jitter Measurement Utility for benchmark validation.

## Non-Goals
- Auto-capture triggering (this will be handled in M07 based on the `isReadyForCapture` flag).
- Dense optical flow tracking (using simple corner-based scalar filtering instead).
- Perspective cropping of the image (reserved for post-capture).

## Dependencies
- M05: Target Selection (provides `TrackedTarget`).
- Spike S03: Temporal Filter Comparison (determines the exact filter math used).

## Components
- `com.localscan.camera.tracking.TemporalTracker` (Interface)
- `com.localscan.camera.tracking.SmoothedTarget` (Data class)
- `com.localscan.camera.tracking.TrackingState` (Enum)
- `com.localscan.camera.tracking.filters.CornerFilter` (Math implementation, e.g., `OneEuroFilter`)
- `com.localscan.camera.ui.DocumentOverlay` (Compose `@Composable`)
- `com.localscan.camera.utils.CoordinateMapper` (Maps ML coordinates to UI coordinates)
- `com.localscan.camera.utils.JitterMetrics` (Utility)

## Data Flow
1. `TargetSelector` emits `TrackedTarget?` (or `null` if no valid candidate).
2. `TemporalTracker.update(TrackedTarget?, timestamp)` is called.
3. If `null`, state machine advances towards `LOST` or `NO_TARGET`.
4. If present, corners are fed into `CornerFilter`.
5. `TemporalTracker` evaluates stabilization (velocity of corners) and updates `TrackingState`.
6. `TemporalTracker` emits `SmoothedTarget`.
7. `CameraScreen` observes `SmoothedTarget`, passes it to `CoordinateMapper`.
8. `DocumentOverlay` renders the mapped quadrilateral onto the Compose Canvas.

## Implementation Steps
1. **Define Core Models:** 
   - Create `TrackingState` enum (`NO_TARGET`, `ACQUIRING`, `TRACKING`, `STABLE`, `LOST`).
   - Create `SmoothedTarget` data class containing `corners` (List<PointF>), `confidence` (Float), `state` (TrackingState), and `isReadyForCapture` (Boolean).
2. **Implement Smoothing Math:**
   - Create `CornerFilter` interface and implement the algorithm chosen in S03 (e.g., 1 Euro Filter).
   - Ensure the filter applies to each of the 4 corners independently (x and y coordinates = 8 independent 1D filters).
3. **Build TemporalTracker:**
   - Implement `DefaultTemporalTracker : TemporalTracker`.
   - Add state machine logic: transitions from `NO_TARGET` -> `ACQUIRING` (on first sight) -> `TRACKING` -> `STABLE` (when corner velocity is below threshold for N frames).
   - Add dropout tolerance: transitioning from `STABLE` to `LOST` if `TrackedTarget` is null for < M frames (retaining last known coordinates), then to `NO_TARGET` if null persists.
4. **Coordinate Transformation:**
   - Implement `CoordinateMapper` that takes `ImageProxy` dimensions, Preview dimensions, and scaling mode (e.g., FIT/FILL) to generate a transformation matrix.
5. **Build Compose Overlay:**
   - Create `@Composable fun DocumentOverlay(target: SmoothedTarget?, matrix: Matrix)`.
   - Render polygon paths on a `Canvas`.
   - Animate colors/stroke width based on `target.state` (e.g., White for `TRACKING`, Green for `STABLE`).
6. **Integration:**
   - Connect `TemporalTracker` into the `DocumentAnalyzer` pipeline.
   - Surface the `Flow<SmoothedTarget>` to the UI layer.
7. **Jitter Utility:**
   - Create `JitterMetrics` to measure inter-frame corner delta and log output for testing.

## Testing
- `TemporalTrackerTest`: Verify state transitions (e.g., target missing for 2 frames doesn't reset state, target missing for 10 frames resets to `NO_TARGET`).
- `CornerFilterTest`: Feed a noisy sine wave of coordinates and verify output smoothness and phase lag.
- `CoordinateMapperTest`: Verify mapping from 640x480 analysis buffer to 1080x1920 UI preview.

## Validation
- **Visual:** Jitter is substantially reduced compared to raw output.
- **Metrics:** `JitterMetrics` shows average inter-frame corner delta reduced by at least 70% during static hold.
- **UI:** The overlay accurately matches the physical document under the camera and smoothly transitions colors when stable.

## Performance
- Tracking math (8x scalar filters) and state machine must execute in < 1ms per frame.
- Total latency from raw detection to `SmoothedTarget` emission must be negligible.
- Compose Canvas rendering overhead < 4ms.
- End-to-end overlay latency ≤ 2 frames.

## Failure Cases
- Camera motion blur causes brief loss of detection: Tracker enters `LOST` state, overlay persists at last known location for a short duration.
- Target moves drastically: Filter must not lag significantly (dynamic beta in 1 Euro filter or reset threshold handles this).

## Acceptance Criteria
- `TemporalTracker` outputs `SmoothedTarget` using selected filter.
- `DocumentOverlay` draws over the camera preview accurately.
- `TrackingState` accurately reflects NO_TARGET, TRACKING, STABLE, and LOST modes with distinct visual indicators.
- Overlay visually anchors to the real-world document without distracting jitter.

## Git Checkpoint
`feat: Implement temporal tracking and AR document overlay`

## Risks
- Coordinate mismatch between CameraX `ImageProxy` (e.g., 4:3 rotated) and Compose `Preview` (e.g., 16:9 full screen) can cause the overlay to misalign.
- Aggressive filtering may cause visual "lag" when panning the camera.

## Open Questions
- What are the precise threshold parameters for transitioning into the `STABLE` state (e.g., max pixel velocity per frame)?
- What is the exact dropout tolerance duration (in frames or milliseconds) before transitioning to `NO_TARGET`?
