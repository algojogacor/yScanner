# M00: Project Foundation

## Objective
Initialize the project structure, Git repository, build system, and module shells to establish a robust foundation for all subsequent development.

## Product Requirements
N/A (This is a foundational technical milestone required for all functional requirements).

## Architecture References
ARCHITECTURE.md §1.1 (Module Structure)
ARCHITECTURE.md §7.1 (Build System & Dependencies)

## Current State
The repository contains only documentation files (PRD.md, BRIEF.md, ARCHITECTURE.md, AGENTS.md, WORKLOG.md). No Git repository, build files, or source code exist.

## Scope
*   Initialize Git repository and `.gitignore`.
*   Create root Gradle project (Gradle KTS, Version Catalog).
*   Create `gradle/libs.versions.toml` with all necessary dependencies.
*   Create the `:app` module with a basic Compose `MainActivity` and `Application` class.
*   Create flat feature/layer module shells: `:app`, `:camera`, `:detection`, `:geometry`, `:processing`, `:domain`, `:data`, `:pdf`, `:import`, `:common`, `:test-fixtures`.
*   Configure SDK versions (compileSdk 35, targetSdk 35, minSdk 24) and Kotlin 2.0.x.

## Non-Goals
*   Implementing any actual feature logic (camera, ML, processing).
*   Setting up CI/CD pipelines.
*   Setting up complex sub-module structures (e.g., `:core:common`, `:camera:api`).

## Dependencies
*   Android Studio / JDK 17 (or compatible version for Gradle and Kotlin 2.0.x).

## Components
*   `settings.gradle.kts`
*   `build.gradle.kts` (root)
*   `gradle/libs.versions.toml`
*   `app/build.gradle.kts`
*   `app/src/main/kotlin/com/yscanner/app/YScannerApplication.kt`
*   `app/src/main/kotlin/com/yscanner/app/MainActivity.kt`
*   Module shells (`camera`, `detection`, `geometry`, `processing`, `domain`, `data`, `pdf`, `import`, `common`, `test-fixtures`) with their respective `build.gradle.kts` files.

## Data Flow
N/A (No functional data flow in this milestone).

## Implementation Steps
1.  **Git Init:** Run `git init` and create `.gitignore` (standard Android rules).
2.  **Version Catalog:** Create `gradle/libs.versions.toml` defining versions for Kotlin (2.0.x), AGP, CameraX, OpenCV, LiteRT, Compose, Material3, Coroutines, Room, Koin/Hilt, JUnit, Truth, Mockk, Turbine.
3.  **Root Build Configuration:** Create root `build.gradle.kts` and `settings.gradle.kts`. Apply necessary plugins (Android application, Android library, Kotlin Android) in the root.
4.  **Module Creation:** Create directories and `build.gradle.kts` for:
    *   `:common` (Android Library)
    *   `:domain` (Java/Kotlin Library)
    *   `:data` (Android Library)
    *   `:camera` (Android Library)
    *   `:detection` (Android Library)
    *   `:geometry` (Android Library)
    *   `:processing` (Android Library)
    *   `:pdf` (Android Library)
    *   `:import` (Android Library)
    *   `:test-fixtures` (Android Library)
5.  **Settings Configuration:** Include all created modules in `settings.gradle.kts`.
6.  **App Module Setup:** Create `:app` module (Android Application). Configure dependencies on all other modules.
7.  **Basic Code:** Create `YScannerApplication.kt` and `MainActivity.kt` in `:app`.
8.  **ProGuard:** Add basic ProGuard rules to `app/proguard-rules.pro`.

## Testing
*   Ensure Gradle syncs successfully without errors.
*   Ensure the empty app compiles and installs on an emulator/device.

## Validation
*   Run `./gradlew assembleDebug` to verify compilation.
*   Check that `libs.versions.toml` contains the required dependencies.

## Performance
*   N/A

## Failure Cases
*   N/A

## Acceptance Criteria
*   Git repository is initialized.
*   Project syncs successfully in Android Studio/Gradle.
*   All specified flat modules exist and are included in `settings.gradle.kts`.
*   SDK versions are correctly configured.
*   App runs and displays a blank/default Compose screen.

## Git Checkpoint
`chore: initialize android project structure and build configurations`

## Risks
*   Version conflicts in `libs.versions.toml` if incompatible versions of Compose, Kotlin, or AGP are chosen.

## Open Questions
*   Should we use Koin or Hilt for Dependency Injection? (Recommend Koin for simplicity in Kotlin projects, but deferring to developer preference).
