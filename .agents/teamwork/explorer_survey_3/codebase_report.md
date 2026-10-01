# Empirical Codebase and Build Setup Survey Report

**Author:** explorer_survey_3 (teamwork_preview_explorer)  
**Date:** 2026-10-01T04:55:00Z  
**Target Repository:** `d:\Projects\pdfscanner`  
**Parent Orchestrator:** `teamwork_preview_orchestrator` (`d5a8c364-b3d4-4fee-8f48-03456ac7fd55`)  

---

## 1. Executive Summary

A comprehensive empirical audit of the physical filesystem, Git configuration, and host build toolchain was conducted for `d:\Projects\pdfscanner`.

### Key Findings
1. **Greenfield Implementation State:** The repository contains **zero lines of application or build code**. There are no Gradle build files (`build.gradle.kts`, `settings.gradle.kts`), no Gradle wrappers (`gradlew`, `gradlew.bat`), no Version Catalog (`gradle/libs.versions.toml`), no module directories, no Kotlin/Java source files, no Android resources, no ML model assets (`.tflite` / `.onnx`), and no sample/test image fixtures.
2. **Specification & Planning Baseline:** The repository contains 35 tracked Markdown files (totalling ~380 KB) defining complete product requirements (`PRD.md`), technical architecture (`ARCHITECTURE.md`), agent operational rules (`AGENTS.md`), project brief (`BRIEF.md`), worklog (`WORKLOG.md`), 23 milestone plans (`plans/000` through `plans/023`), and 6 experimental spike plans (`plans/spikes/S01` through `S06`).
3. **Git State:** 
   - Git repository is initialized with 1 commit: `3b95422 docs(plans): establish yScanner implementation roadmap`.
   - Current branch: `main`, tracking remote `origin` (`https://github.com/algojogacor/yScanner.git`).
   - Working tree has an unstaged change in `AGENTS.md` (addition of Section 54: "Git Remote and Checkpoint Rules").
   - Directory `.agents/` is untracked (reserved for multi-agent coordination metadata).
4. **Host Build Toolchain Status:**
   - **Java / JDK:** OpenJDK 21.0.11+10-LTS (Temurin) installed at `D:\android-tools\jdk21\`. `JAVA_HOME` is properly set.
   - **Gradle:** Not present on system PATH. However, Gradle 9.3.1 and 9.5.0 distributions exist in user cache (`C:\Users\Arya Rizky\.gradle\wrapper\dists\`). Project lacks its own wrapper.
   - **Android SDK:** Installed at `D:\Android\Sdk` (`ANDROID_HOME` and `ANDROID_SDK_ROOT` configured).
   - **Installed SDK Packages:**
     - Build-tools: `35.0.0`, `36.0.0`
     - Platforms: `android-36`, `android-37.0` (**CRITICAL GAP:** `platforms;android-35` is NOT installed, while R1 requires `compileSdk 35`).
     - NDK: `27.1.12297006`
     - CMake: `3.22.1`
     - Platform-tools: `37.0.1` (`adb.exe` operational)
     - Cmdline-tools: `latest` (`sdkmanager.bat` and `android.exe` operational)
     - Licenses: Already accepted in `D:\Android\Sdk\licenses`.
     - Connected Devices / Emulators: 0 connected devices. Default AVD in `.android/avd` is corrupted.

---

## 2. File and Directory Inventory

### 2.1 Physical Root Structure
```
d:\Projects\pdfscanner\
├── .agents/                 [Untracked agent coordination metadata]
├── .git/                    [Git repository database]
├── plans/                   [30 implementation and spike plans]
│   ├── 000-master-plan.md
│   ├── 001-project-foundation.md
│   ├── ...
│   ├── 023-release-hardening.md
│   └── spikes/
│       ├── S01-model-selection.md
│       ├── S02-quad-fitting.md
│       ├── S03-temporal-filter.md
│       ├── S04-dewarp-strategy.md
│       ├── S05-enhancement-pipeline.md
│       └── S06-roi-decoding.md
├── AGENTS.md                [Agent operating rules, 35,545 bytes - modified locally]
├── ARCHITECTURE.md          [Technical architecture specification, 46,122 bytes]
├── BRIEF.md                 [Project brief and execution priorities, 21,035 bytes]
├── PRD.md                   [Product Requirements Document, 40,848 bytes]
└── WORKLOG.md               [Engineering work log, 3,660 bytes]
```

### 2.2 Complete Catalog of Tracked Files (via `git ls-files`)

| # | File Path | Size (Bytes) | Category | Description |
|---|---|---|---|---|
| 1 | `AGENTS.md` | 35,545 | Governance | Agent operating rules and discipline (includes unstaged Sec 54) |
| 2 | `ARCHITECTURE.md` | 46,122 | Architecture | Full system architecture, subsystem boundaries, interface contracts |
| 3 | `BRIEF.md` | 21,035 | Product | Project brief, priorities, core workflows |
| 4 | `PRD.md` | 40,848 | Requirements | Product requirements document (35 sections, 47 ACs) |
| 5 | `WORKLOG.md` | 3,660 | Journal | Chronological engineering log |
| 6 | `plans/000-master-plan.md` | 31,527 | Master Plan | 23-milestone implementation plan, dependency graph, risks |
| 7 | `plans/001-project-foundation.md` | 4,246 | Plan M00 | Project setup, Gradle KTS, Version Catalog, 11 module shells |
| 8 | `plans/002-camera-foundation.md` | 4,874 | Plan M01 | CameraX Preview, ImageAnalysis, ImageCapture, lifecycle |
| 9 | `plans/003-coordinate-system.md` | 3,612 | Plan M02 | 7-space coordinate transformation engine |
| 10 | `plans/004-document-detector.md` | 4,921 | Plan M03 | LiteRT inference, segmentation mask, candidate extraction |
| 11 | `plans/005-geometry-engine.md` | 4,008 | Plan M04 | Outer envelope, quadrilateral fitting, corner ordering |
| 12 | `plans/006-target-selection.md` | 4,932 | Plan M05 | Multi-candidate selection, tap-to-guide, target locking |
| 13 | `plans/007-temporal-tracking.md` | 4,815 | Plan M06 | Temporal tracking, jitter reduction, One Euro filter |
| 14 | `plans/008-auto-capture.md` | 4,451 | Plan M07 | Stability detection, countdown, auto & manual shutter |
| 15 | `plans/009-one-page-refinement.md` | 3,892 | Plan M08 | Full-res ROI corner refinement |
| 16 | `plans/010-perspective-correction.md` | 4,503 | Plan M09 | Homography, perspective rectification, bounding quad |
| 17 | `plans/011-quality-assessment.md` | 4,185 | Plan M10 | Blur, glare, shadow, exposure, geometry scoring |
| 18 | `plans/012-enhancement.md` | 4,601 | Plan M11 | Non-destructive Natural and Clean enhancement filters |
| 19 | `plans/013-page-object.md` | 3,615 | Plan M12 | PageObject domain model, geometry serialization |
| 20 | `plans/014-page-management.md` | 4,152 | Plan M13 | Page reorder, crop, rotate, replace, delete, undo/redo |
| 21 | `plans/015-session-persistence.md` | 5,123 | Plan M14 | Incremental persistence, SQLite/Room, process recovery |
| 22 | `plans/016-two-page-book.md` | 3,744 | Plan M15 | Book spread boundary and gutter detection |
| 23 | `plans/017-dewarp.md` | 4,561 | Plan M16 | Curvature estimation, mesh dewarping, page splitting |
| 24 | `plans/018-gallery-import.md` | 4,213 | Plan M17 | Gallery image loading, EXIF orientation, pipeline feeding |
| 25 | `plans/019-pdf-renderer.md` | 4,402 | Plan M18 | Page-by-page streaming PDF generation, 6 page sizes |
| 26 | `plans/020-pdf-export.md` | 4,815 | Plan M19 | PDF export UI, quality profiles, size estimation |
| 27 | `plans/021-performance.md` | 4,372 | Plan M20 | Memory profiling, benchmark harness, 20-page stress test |
| 28 | `plans/022-regression-testing.md` | 4,960 | Plan M21 | Golden test image corpus, automated regression suite |
| 29 | `plans/023-release-hardening.md` | 4,710 | Plan M22 | ProGuard/R8 rules, offline check, release validation |
| 30 | `plans/spikes/S01-model-selection.md` | 4,247 | Spike S01 | ML model evaluation (U-Net MobileNetV3 vs DeepLabV3+) |
| 31 | `plans/spikes/S02-quad-fitting.md` | 2,752 | Spike S02 | Quadrilateral fitting algorithms (Ramer-Douglas-Peucker) |
| 32 | `plans/spikes/S03-temporal-filter.md` | 3,782 | Spike S03 | Jitter filter comparison (Exponential vs One Euro vs Kalman) |
| 33 | `plans/spikes/S04-dewarp-strategy.md` | 3,912 | Spike S04 | Book dewarp strategy (3D cylindrical vs 2D mesh) |
| 34 | `plans/spikes/S05-enhancement-pipeline.md` | 4,611 | Spike S05 | Illumination flattening and enhancement algorithms |
| 35 | `plans/spikes/S06-roi-decoding.md` | 3,791 | Spike S06 | Full-res ROI decoding memory and performance |

---

## 3. Current Build System Status

| Component | Status | Details / Location |
|---|---|---|
| `gradlew` (Unix script) | **Missing** | Not present in root directory |
| `gradlew.bat` (Windows script) | **Missing** | Not present in root directory |
| `gradle/wrapper/gradle-wrapper.jar` | **Missing** | Directory `gradle/` does not exist |
| `gradle/wrapper/gradle-wrapper.properties` | **Missing** | Directory `gradle/` does not exist |
| `gradle/libs.versions.toml` | **Missing** | Version catalog does not exist |
| `build.gradle.kts` (Root) | **Missing** | Root build script does not exist |
| `settings.gradle.kts` | **Missing** | Root settings script does not exist |
| Host Gradle installation | **Not in PATH** | Available in cache: `C:\Users\Arya Rizky\.gradle\wrapper\dists\gradle-9.3.1-bin` and `gradle-9.5.0-bin` |
| Host JDK | **Present** | OpenJDK 21.0.11 (Temurin), `D:\android-tools\jdk21\` (`JAVA_HOME`) |
| Host Android SDK | **Present** | `D:\Android\Sdk` (`ANDROID_HOME`, `ANDROID_SDK_ROOT`) |
| SDK Build Tools | **Present** | `35.0.0` and `36.0.0` installed in `D:\Android\Sdk\build-tools\` |
| SDK Platforms | **Partial** | `android-36` and `android-37.0` installed. `android-35` is **missing** |
| NDK | **Present** | `27.1.12297006` in `D:\Android\Sdk\ndk\` |
| CMake | **Present** | `3.22.1` in `D:\Android\Sdk\cmake\` |

---

## 4. Module Inventory: Existing vs Required

Per **ORIGINAL_REQUEST.md (§R1)** and **`plans/001-project-foundation.md`**, the project requires 11 flat modules.

| Module | Type | Status | Required Dependencies |
|---|---|---|---|
| `:app` | Android Application | **Missing** | Depends on all feature and domain modules |
| `:camera` | Android Library | **Missing** | CameraX (Core, Camera2, Lifecycle, View), `:common` |
| `:detection` | Android Library | **Missing** | LiteRT/TFLite, `:common`, `:domain` |
| `:geometry` | Android Library | **Missing** | OpenCV (or math primitives), `:common`, `:domain` |
| `:processing` | Android Library | **Missing** | OpenCV, RenderScript/Vulkan/Bitmap utils, `:common`, `:domain` |
| `:domain` | Kotlin/Java Library (Pure) | **Missing** | Pure Kotlin, Coroutines, no Android framework dependencies |
| `:data` | Android Library | **Missing** | Room / SQLite, File storage, `:domain`, `:common` |
| `:pdf` | Android Library | **Missing** | Android PdfDocument / streaming PDF engine, `:domain`, `:processing`, `:common` |
| `:import` | Android Library | **Missing** | Coil / Glide / Android Media, `:domain`, `:detection`, `:geometry`, `:processing`, `:common` |
| `:common` | Android Library | **Missing** | Core utilities, image helpers, logging, concurrency |
| `:test-fixtures` | Android Library | **Missing** | Test doubles, mock models, fixture images, shared assertions |

*(Note: Total existing modules = 0; Total modules to create = 11).*

---

## 5. Assets, Models, Test Fixtures, and Libraries

| Asset Type | Current Count | Locations Checked | Notes |
|---|---|---|---|
| Kotlin source (`*.kt`) | 0 | Root, subdirectories | Greenfield |
| Java source (`*.java`) | 0 | Root, subdirectories | Greenfield |
| C/C++ source (`*.cpp`, `*.h`) | 0 | Root, subdirectories | Greenfield |
| Android Manifests (`AndroidManifest.xml`) | 0 | Root, subdirectories | Greenfield |
| ML models (`*.tflite`, `*.onnx`) | 0 | Root, subdirectories | Requires acquisition/export via Spike S01 / M03 |
| Test image corpus (`*.jpg`, `*.png`) | 0 | Root, subdirectories | Required for golden regression suite (Spike S01, M21) |
| OpenCV binaries / AAR | 0 | Root, subdirectories | Requires dependency configuration in Version Catalog |
| ProGuard / R8 rules (`*.pro`) | 0 | Root, subdirectories | Needs initial setup in M00 |

---

## 6. Gaps, Risks, and Immediate Build Prerequisites

To reach the first working build state (`./gradlew assembleDebug` passing per R1 Acceptance Criteria), the following concrete prerequisites must be fulfilled:

### 6.1 Immediate Prerequisites
1. **Reconcile Git Working Tree:**
   - Resolve the unstaged modification in `AGENTS.md` (Section 54 was added). Either commit it or verify its status with orchestrator.
2. **Install Android SDK Platform 35 (`android-35`):**
   - R1 explicitly requires `compileSdk 35`. Currently, only `platforms;android-36` and `platforms;android-37.0` are installed.
   - Action: Run `D:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat "platforms;android-35"` or confirm if `compileSdk 36` is acceptable. (Installing `platforms;android-35` maintains strict compliance with R1).
3. **Bootstrap Gradle Wrapper:**
   - Since `gradle` is not in system PATH, the project needs `gradlew` / `gradlew.bat` and `gradle-wrapper.jar` initialized.
   - Recommended Gradle version: **Gradle 8.10.2** or **8.11.1** (ensures full compatibility with AGP 8.7.x / 8.8.x, Kotlin 2.0.x, and JDK 21).
   - Wrapper can be generated using the existing local Gradle runtime in `C:\Users\Arya Rizky\.gradle\wrapper\dists\gradle-9.3.1-bin\...` targeting Gradle 8.10.2 distribution URL, or scripted.
4. **Create Version Catalog (`gradle/libs.versions.toml`):**
   - Declare:
     - AGP: `8.7.3` or `8.8.0`
     - Kotlin: `2.0.21` (with Compose Compiler plugin `org.jetbrains.kotlin.plugin.compose`)
     - AndroidX Core, Lifecycle, Activity Compose
     - Jetpack Compose BOM & Material3
     - CameraX: `1.4.1` (core, camera2, lifecycle, view)
     - LiteRT / TensorFlow Lite: `com.google.ai.edge.litert:litert:1.0.1` or `org.tensorflow:tensorflow-lite:2.16.1`
     - OpenCV Android: Maven dependency (e.g. `com.quickbirdstudios:opencv` or official AAR)
     - Room: `2.6.1`
     - Coroutines: `1.9.0`
     - Testing: JUnit 4 (`4.13.2`), Truth (`1.4.4`), MockK (`1.13.13`), Turbine (`1.2.0`)
5. **Create Root Build & Settings Scripts:**
   - `settings.gradle.kts`: Define plugin management, dependency resolution (Google, MavenCentral), and include all 11 modules.
   - `build.gradle.kts` (root): Apply plugins with `apply false`.
6. **Initialize 11 Module Shells:**
   - Create directories and minimal `build.gradle.kts` + `src/main/AndroidManifest.xml` for each of the 11 modules.
   - Ensure pure Kotlin library for `:domain` (`plugins { id("java-library"); id("org.jetbrains.kotlin.jvm") }`).
   - Create `:app` module with `MainActivity.kt` and `LocalScanApplication.kt` displaying a basic Compose screen.
7. **Verify Baseline Build:**
   - Execute `./gradlew assembleDebug` and `./gradlew test` to ensure clean initial green build.

---

## 7. Evidence Chain Summary

| Item | Evidence Source | Verification Command / Path | Finding |
|---|---|---|---|
| Tracked files | Git index | `git ls-files` | Exactly 35 markdown files; 0 code/build files |
| Git branch/remote | Git status | `git status`, `git remote -v` | Branch `main`, tracking `https://github.com/algojogacor/yScanner.git` |
| Unstaged edits | Git diff | `git diff AGENTS.md` | Section 54 added to `AGENTS.md` |
| Java environment | JVM query | `java -version`, `$env:JAVA_HOME` | OpenJDK 21.0.11 Temurin at `D:\android-tools\jdk21\` |
| System Gradle | PATH inspection | `Get-Command gradle` | Not in PATH; dists exist in user `.gradle` cache |
| Android SDK Root | Env vars | `$env:ANDROID_HOME` | `D:\Android\Sdk` |
| Android Platforms | Filesystem & sdkmanager | `D:\Android\Sdk\platforms` | `android-36`, `android-37.0` present; `android-35` missing |
| Android Build-Tools | Filesystem | `D:\Android\Sdk\build-tools` | `35.0.0`, `36.0.0` present |
| Cmdline-tools | Filesystem | `D:\Android\Sdk\cmdline-tools\latest\bin` | `sdkmanager.bat`, `android.exe` functional |
| SDK Licenses | Filesystem | `D:\Android\Sdk\licenses` | `android-sdk-license` accepted |

---
*Report completed and stored at `d:\Projects\pdfscanner\.agents\teamwork\explorer_survey_3\codebase_report.md`.*
