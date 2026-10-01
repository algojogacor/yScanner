# M07: Auto Capture

## Objective
Implement automatic document capture based on stability metrics, including a 2-second countdown, UI indicators, and a toggle for auto capture, while ensuring manual capture always remains available and bypasses all quality gates.

## Product Requirements
- PRD §9 (Auto Capture)

## Architecture References
- ARCHITECTURE.md §7 (Auto Capture)
- ARCHITECTURE.md §28

## Current State
The app module is a default Android scaffold. The `SmoothedTarget` and temporal tracker have been defined in M06.

## Scope
- Implement `CaptureReadinessEvaluator` to assess `SmoothedTarget` for auto-capture suitability.
- Implement `AutoCaptureController` managing the state machine of auto-capture (IDLE, EVALUATING, COUNTDOWN, CAPTURING, DISABLED).
- Implement a 2-second countdown timer.
- Integrate a countdown UI indicator into the Jetpack Compose camera screen.
- Add an auto-capture toggle (On/Off) in the UI.
- Wire CameraX `ImageCapture` to take high-resolution stills that are file-backed upon trigger.
- Guarantee that manual shutter input bypasses countdown and quality gates.

## Non-Goals
- Forced retake systems.
- Quality gates blocking manual capture (explicitly forbidden by PRD).
- Saving the captured image to the final document PDF (handled in a later milestone).
- OCR or advanced image processing post-capture.

## Dependencies
- M06: Temporal Tracking (provides `SmoothedTarget` with stability state).
- Basic CameraX `ImageCapture` setup from earlier milestones.

## Components
- `com.yscanner.app.domain.model.CaptureReadiness`: Data class with `isReady`, `stabilityScore`, `qualityEstimate`, `reason`.
- `com.yscanner.app.domain.evaluator.CaptureReadinessEvaluator`: Interface mapping `SmoothedTarget` -> `CaptureReadiness`.
- `com.yscanner.app.domain.evaluator.DefaultCaptureReadinessEvaluator`: Implementation of `CaptureReadinessEvaluator`.
- `com.yscanner.app.domain.controller.AutoCaptureState`: Enum (IDLE, EVALUATING, COUNTDOWN, CAPTURING, DISABLED).
- `com.yscanner.app.domain.controller.AutoCaptureController`: Interface with `StateFlow<AutoCaptureState>`.
- `com.yscanner.app.domain.controller.DefaultAutoCaptureController`: Implementation handling the state machine and timer.
- `com.yscanner.app.ui.camera.CameraScreen`: Updated Jetpack Compose UI to include countdown indicator and auto-capture toggle.
- `com.yscanner.app.ui.camera.CameraViewModel`: Handles bridging the controller states to UI and handling manual shutter events.

## Data Flow
1. `SmoothedTarget` -> `CaptureReadinessEvaluator` -> `CaptureReadiness`.
2. `CaptureReadiness` (if `isReady` == true) -> `AutoCaptureController` starts 2-second countdown (moves to `COUNTDOWN`).
3. During countdown, if `CaptureReadiness` becomes `isReady` == false -> `AutoCaptureController` cancels countdown (moves to `EVALUATING` or `IDLE`).
4. If countdown finishes -> `AutoCaptureController` moves to `CAPTURING` -> Triggers `ImageCapture`.
5. Manual Shutter -> Bypasses `AutoCaptureController`, directly triggers `ImageCapture`.

## Implementation Steps
1. Create `CaptureReadiness` data class and `AutoCaptureState` enum in `com.yscanner.app.domain`.
2. Create `CaptureReadinessEvaluator` interface and `DefaultCaptureReadinessEvaluator` implementation.
3. Create `AutoCaptureController` interface and `DefaultAutoCaptureController`.
4. In `DefaultAutoCaptureController`, implement the state machine using Kotlin Coroutines `StateFlow` and `delay(2000)` for the countdown. Support cancellation if `SmoothedTarget` becomes unstable.
5. In `CameraViewModel`, inject `AutoCaptureController`. Expose UI state for countdown, current `AutoCaptureState`, and a toggle for enabling/disabling auto capture.
6. In `CameraScreen`, add a visual countdown indicator (e.g., a progressing circle or text) that appears during `COUNTDOWN` state.
7. In `CameraScreen`, add an Auto Capture toggle button to enable/disable the feature.
8. Wire the manual shutter button in `CameraScreen` to explicitly call capture on `CameraViewModel` immediately, bypassing all evaluators.
9. Implement the actual capture logic using CameraX `ImageCapture` to take a picture and save it as a high-resolution file in the app's cache directory.

## Testing
- `DefaultCaptureReadinessEvaluatorTest`: Verify it returns `isReady=true` only when `SmoothedTarget` is stable.
- `AutoCaptureControllerTest`: Verify state transitions:
  - `IDLE` -> `EVALUATING` when target found.
  - `EVALUATING` -> `COUNTDOWN` when target is ready.
  - `COUNTDOWN` -> `CAPTURING` after 2 seconds if target remains ready.
  - `COUNTDOWN` -> `EVALUATING` if target becomes not ready before 2 seconds.
- `CameraViewModelTest`: Verify manual capture immediately triggers capture event regardless of `AutoCaptureState`.

## Validation
- Point camera at a document. Observe the UI showing stability and countdown.
- Move the camera during countdown. Observe the countdown cancelling.
- Wait for countdown to finish. Observe a high-res capture is taken and saved.
- Press manual shutter while auto-capture is disabled, evaluating, or counting down. Observe immediate capture.

## Performance
- Readiness evaluation must be lightweight, taking <5ms per frame to prevent blocking the frame processing pipeline.
- Coroutine-based countdown timer must be efficient and cancel cleanly without leaking jobs.

## Failure Cases
- CameraX `ImageCapture` fails: Transition state to `IDLE`, show toast/snackbar, allow manual retry.
- Target tracking lost during countdown: Immediately cancel countdown and return to `IDLE` or `EVALUATING`.

## Acceptance Criteria
- `CaptureReadinessEvaluator` and `AutoCaptureController` implemented and unit tested.
- Auto capture waits exactly 2 seconds while stable before capturing.
- Auto capture cancels if stability is lost during the 2 seconds.
- Manual capture always works, immediately bypassing the countdown and stability checks.
- Auto capture can be toggled on/off in the UI.
- Captured image is saved to disk as a high-resolution file.

## Git Checkpoint
`feat(camera): implement auto capture with stability countdown and manual bypass`

## Risks
- Coroutine cancellation race conditions during rapidly changing stability states. (Mitigation: Use structured concurrency and proper StateFlow emissions).

## Open Questions
- What exact visual design should the 2-second countdown use (e.g., circular progress on the shutter button, or central screen overlay)?
