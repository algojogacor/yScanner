# M05: Target Selection & Tap-to-Guide

## Objective
Implement intelligent target selection logic to pick the best document candidate among multiple detections, track it across frames, and allow user override via tap-to-guide which also triggers camera focus/metering.

## Product Requirements
- PRD.md §4: Core Scanning Flow (target selection)
- PRD.md §5.6: Tap-to-guide (focus/metering + document selection)
- PRD.md §6.3: Multiple Documents (prioritizing the most central/prominent unless tapped)
- PRD.md §6.4: Target Locking (persistence across frames)

## Architecture References
- ARCHITECTURE.md §5: Coordinate Systems & Transformation
- ARCHITECTURE.md §12: State Management (Target tracking state)
- ARCHITECTURE.md §13: Core Document Scanner (TargetSelector interface)
- ARCHITECTURE.md §14: Camera & Preview (Integration with focus/metering)

## Current State
- The geometry engine (M04) produces multiple `Quadrilateral` objects from image segmentation.
- The `CoordinateTransformer` (M03) can map between image/analysis coordinates and preview coordinates.
- CameraX is set up but lacks advanced tap-to-focus/metering integration.
- No tracking or temporal persistence of detected candidates exists yet.

## Scope
- Define `TargetSelector` interface and implementation.
- Define `TrackedTarget` and `TrackingState`.
- Implement selection criteria (tap > temporal > quality > containment > stability > coverage > ML confidence).
- Implement tracking persistence to prevent flickering between multiple candidates.
- Implement tap-to-guide coordinate transformation, hit-testing, and CameraX metering/focus trigger.
- Candidate identity tracking across frames.

## Non-Goals
- Manual corner editing (this is a post-capture feature).
- Multi-document simultaneous capture (only one target is selected for capture at a time).
- UI overlay rendering for the target (handled in a later milestone).

## Dependencies
- M03: Coordinate Transformations (for tap-to-guide mapping).
- M04: Geometry Engine (for `Quadrilateral` candidate generation).

## Components
- `com.localscan.scanner.domain.model.TrackedTarget` (Data class)
- `com.localscan.scanner.domain.model.TrackingState` (Enum)
- `com.localscan.scanner.domain.TargetSelector` (Interface)
- `com.localscan.scanner.domain.DefaultTargetSelector` (Implementation)
- `com.localscan.camera.CameraManager` (Updated for focus/metering)
- `com.localscan.scanner.ui.TapToGuideController` or similar for handling user input and triggering hit tests.

## Data Flow
1. **Camera/Preview**: User taps screen at `PointF` (preview coords).
2. **CoordinateTransformer**: Maps `PointF` to analysis coords.
3. **TargetSelector**: Receives list of `Quadrilateral` candidates, current `TrackedTarget`, and optional user tap point.
4. **TargetSelector**: Performs hit-test if tap exists -> assigns highest priority to hit candidate.
5. **TargetSelector**: If no tap, applies selection heuristics (temporal identity, geometry, confidence).
6. **TargetSelector**: Outputs updated `TrackedTarget` with new `TrackingState` (e.g., STABLE).
7. **CameraManager**: Triggered by tap to set Focus/Metering via CameraX `MeteringPointFactory`.

## Implementation Steps
1. **Define Models**:
   - Create `TrackingState` enum (`ACQUIRING`, `TRACKING`, `STABLE`, `LOST`).
   - Create `TrackedTarget` data class containing `candidateId: String`, `quadrilateral: Quadrilateral`, `confidence: Float`, `trackingState: TrackingState`, `stableFrameCount: Int`.
2. **Define Interface**:
   - Create `TargetSelector` with method `selectTarget(candidates: List<Quadrilateral>, previousTarget: TrackedTarget?, tapPoint: PointF?): TrackedTarget?`.
3. **Implement Selection Logic**:
   - Create `DefaultTargetSelector`.
   - Implement hit-testing: if `tapPoint` is provided, find the candidate containing this point.
   - Implement temporal tracking: calculate Intersection over Union (IoU) or centroid distance between candidates and `previousTarget` to maintain identity.
   - Implement selection heuristics for when there is no tap or previous target (largest area, most central, best geometry).
   - Implement persistence: don't switch targets if the current one is still valid, even if another has slightly higher confidence.
   - Implement loss conditions: mark as `LOST` if no matching candidate for N frames (e.g., 3-5).
4. **Implement Camera Integration**:
   - Update `CameraManager` or equivalent to accept a tap coordinate.
   - Use CameraX `DisplayOrientedMeteringPointFactory` or similar to trigger focus and metering at the tap point.
5. **Integrate Components**:
   - Wire `TargetSelector` into the main frame analysis pipeline.
   - Pass user touch events from the UI down to the scanner pipeline as tap points.

## Testing
- `DefaultTargetSelectorTest`:
  - `selectsCandidateContainingTapPoint()`: verifies tap override.
  - `maintainsTargetPersistenceAcrossFrames()`: verifies the same candidate is selected despite minor confidence fluctuations of others.
  - `transitionsToLostAfterNFramesWithoutMatch()`: verifies tracking state decay.
  - `prioritizesCentralLargestCandidateWhenNoHistory()`: verifies initial selection logic.
- `CameraMeteringTest`: (mocked) verifies CameraX focus/metering action is invoked on tap.

## Validation
- Point a camera at two documents. Ensure the system consistently locks onto one without rapidly switching.
- Tap the unselected document. Verify the system switches target to the tapped document and triggers a camera focus adjustment.
- Move the camera slightly. Verify the selected target remains locked (tracking persistence).

## Performance
- Target selection must run every frame (30fps), so IoU/distance calculations and hit-testing (point-in-polygon) must be extremely fast (< 1ms).
- Avoid object allocation during the selection process where possible.

## Failure Cases
- Tap outside any candidate: System should just trigger focus/metering at that point, but target selection defaults back to standard heuristics.
- Rapid camera movement: Target may be temporarily lost; state should transition smoothly to `LOST` and recover without flickering.

## Acceptance Criteria
- [ ] System tracks a single document identity across frames (no flickering between multiple visible docs).
- [ ] User tapping a document switches the active target to that document immediately.
- [ ] User tap triggers CameraX focus and metering at the tapped location.
- [ ] `TrackingState` correctly updates based on frame-to-frame stability.
- [ ] Target logic executes within 2ms per frame.

## Git Checkpoint
`feat: implement target selection and tap-to-guide tracking`

## Risks
- Rapid hand movement might cause tracking to drop if candidates shift too much between frames. (Mitigation: tolerant distance/IoU thresholds for temporal matching).
- Hit testing might be inaccurate if coordinate transformations from M03 have edge-case bugs.

## Open Questions
- What is the exact frame threshold `N` for dropping a lost target? (Start with 5 frames and tune based on user testing).
