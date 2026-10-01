# M22: Device Compatibility & Release Hardening

## Objective
Harden the application for release by executing device compatibility testing, ProGuard/R8 optimization, error handling audits, privacy audits, thermal/battery profiling, and edge case validation, and finalizing the release build configuration.

## Product Requirements
- §3: Platform & Device Targets (Android API 24-35, mid-range devices).
- §24.4: Thermal/Battery Performance (long sessions, 20+ page scans).
- §29: Error Handling (graceful degradation, AI fallback).
- §30: Security & Privacy (no network calls, app-private storage, no logs of content).
- §31: Edge Cases (permissions denied, storage full).

## Architecture References
- §54-60: Privacy, Security, and Error Handling Architecture.
- §66-70: Build, Signing, and Optimization.

## Current State
The application is feature-complete with regression testing implemented in M21.

## Scope
- Implement R8/ProGuard configuration for release builds.
- Configure and test Baseline Profiles for startup performance.
- Perform privacy audit verifying logs and network usage.
- Execute thermal/battery profiling for long scan sessions.
- Implement explicit handling for edge cases (storage full, permission denied, process death).
- Verify AI fallback to classical CV on unsupported/failing devices.
- Finalize release build configuration (app bundle, versioning, signing setup).

## Non-Goals
- Adding new user-facing features.
- Major UI/UX redesigns.
- Support for unsupported hardware (e.g., extremely low-end devices < 4GB RAM).

## Dependencies
- M21 (Feature Complete & Regression Testing) must be fully merged.
- OpenCV and TFLite libraries must be integrated.

## Components
- `app/build.gradle.kts`: Release configuration, versioning, ProGuard, Signing.
- `app/proguard-rules.pro`: Specific rules for TFLite, OpenCV, Room, Coroutines.
- `com.yscanner.core.error.ErrorHandler`: Global error handling and AI fallback routing.
- `com.yscanner.core.utils.Logger`: Privacy-compliant logging.
- `com.yscanner.camera.CameraManager`: Handling thermal throttling and permission edge cases.
- `com.yscanner.export.PdfExporter`: Handling storage full and process death.

## Data Flow
- Build System -> R8 minifier -> APK/AAB generation.
- App Runtime -> Error Handler -> Graceful UI Degradation / Fallback processing.
- App Runtime -> Privacy Logger (filters out sensitive PII/image data).

## Implementation Steps
1. **R8/ProGuard Configuration**:
   - Update `app/build.gradle.kts` to enable `isMinifyEnabled = true` and `isShrinkResources = true` for release.
   - Add rules to `app/proguard-rules.pro` to keep OpenCV models (`-keep class org.opencv.** { *; }`).
   - Add rules for TFLite (`-keep class org.tensorflow.lite.** { *; }`).
   - Add rules for Room and Compose as needed.
2. **Privacy & Security Audit**:
   - Review `com.yscanner.core.utils.Logger` to ensure no image byte arrays or OCR text are logged.
   - Verify `AndroidManifest.xml` has no internet permission (`android.permission.INTERNET`).
   - Ensure all working directories are inside `Context.filesDir` or `Context.cacheDir`.
3. **Error Handling & Edge Cases**:
   - Implement `CameraPermissionDenied` UI state in Compose.
   - Add `StorageFullException` catch blocks in `PdfExporter`, showing a specific error dialog.
   - Add process death recovery state saving for the active scan session in `ScanViewModel` using `SavedStateHandle`.
4. **Thermal & Battery Handling**:
   - Implement `ThermalStatusListener` in `MainActivity` to listen for thermal pressure.
   - Throttle the ML inference rate (reduce frame processing frequency in `CameraManager`) if thermal status is `THERMAL_STATUS_SEVERE`.
5. **Release Build Configuration**:
   - Set up `keystore.properties` loading in `build.gradle.kts` for signing.
   - Configure version code and version name logic.
   - Setup Baseline Profiles generation module (optional/if applicable) to speed up compose startup.

## Testing
- `ProGuardVerificationTest`: Instrumentation test on a release build to verify core pipeline (OpenCV, TFLite) does not crash via reflection.
- `PrivacyAuditTest`: Unit tests verifying that passing sensitive data to `Logger` redacts or throws.
- `ThermalThrottlingTest`: Unit test for `CameraManager` verifying frame rate drops when thermal severe intent is mocked.
- `StorageFullTest`: Mock storage exception during `PdfExporter` and verify error state emission.

## Validation
- Manual exploratory testing on at least three physical devices: API 24 (low-end), API 30 (mid-range), API 34 (high-end).
- Run 5+ minute continuous scanning sessions and verify the device does not crash from thermal limits.
- Generate an App Bundle (`.aab`), install via `bundletool`, and verify startup and basic scan flow.

## Performance
- APK/AAB size should decrease by at least 20% after R8.
- Thermal mitigation should keep the app alive indefinitely during continuous scanning by sacrificing real-time ML preview frame rate.
- App startup time should remain < 1.5s (measured via Macrobenchmark if Baseline Profiles used).

## Failure Cases
- If ProGuard strips necessary classes, the app will crash on startup or processing. Handled by release build QA.
- If storage is full during export, no data is deleted; the user is prompted to clear space.

## Acceptance Criteria
- Release build can be generated as an AAB.
- App successfully runs minified with ProGuard without crashing on OpenCV or TFLite calls.
- App degrades gracefully (throttling) under high thermal load.
- No network traffic generated by the app.
- Edge cases (no permissions, no storage, process death) are gracefully handled with informative UI.

## Git Checkpoint
`git commit -m "chore(release): implement release hardening, ProGuard, thermal mitigation, and edge cases"`

## Risks
- R8/ProGuard issues with undocumented JNI/Reflection dependencies in third-party CV/ML libraries.
- Differences in OEM camera HAL implementations causing unexpected thermal states or resolution mismatches.

## Open Questions
- Do we want to officially support and publish Baseline Profiles in this release, or defer to a later optimization patch?
