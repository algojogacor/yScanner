## 2026-10-01T05:07:51Z
You are worker_m1_1.
Your working directory: d:\Projects\pdfscanner\.agents\teamwork\worker_m1_1
Your parent: teamwork_preview_orchestrator (conv ID: d5a8c364-b3d4-4fee-8f48-03456ac7fd55)

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

OBJECTIVE:
Implement Milestone M1 (Project Foundation & Build System) for yScanner, satisfying requirements R1 and Acceptance Criteria for Build and Structure.

INPUTS:
- MANDATORY: Read d:\Projects\pdfscanner\.agents\teamwork\ORIGINAL_REQUEST.md first.
- Read d:\Projects\pdfscanner\.agents\teamwork\teamwork_preview_orchestrator_1\PROJECT.md
- Read Explorer Plans:
  - d:\Projects\pdfscanner\.agents\teamwork\explorer_m1_1\plan.md (Toolchain, Gradle wrapper, gradle.properties, Version Catalog)
  - d:\Projects\pdfscanner\.agents\teamwork\explorer_m1_2\plan.md (11 modules, inward dependency graph, pure Kotlin JVM :domain and :common, OpenCV dependency)
  - d:\Projects\pdfscanner\.agents\teamwork\explorer_m1_3\plan.md (Domain models, Room database foundation, app shell, unit tests)
- Read survey reports:
  - d:\Projects\pdfscanner\.agents\teamwork\explorer_survey_3\handoff.md
  - d:\Projects\pdfscanner\.agents\teamwork\spec_miner_survey_2\arch_report.md

WRITE OWNERSHIP:
You own all build and source files in d:\Projects\pdfscanner:
- gradle.properties, settings.gradle.kts, root build.gradle.kts
- gradle/libs.versions.toml, gradle/wrapper/
- All 11 modules: :app, :common, :domain, :test-fixtures, :geometry, :detection, :processing, :camera, :data, :pdf, :import
- Your own metadata in d:\Projects\pdfscanner\.agents\teamwork\worker_m1_1\

TASKS TO EXECUTE:
1. Commit unstaged changes in AGENTS.md (e.g., git commit -am "docs: update AGENTS.md remote checkpoint rules") per AGENTS.md §6.1.
2. Install Android SDK platform 35:
   & "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" "platforms;android-35"
3. Create gradle.properties with android.useAndroidX=true, jvmargs, nonTransitiveRClass, etc.
4. Bootstrap Gradle wrapper (Gradle 8.11.1).
5. Create gradle/libs.versions.toml matching explorer_m1_1 plan.
6. Create settings.gradle.kts including all 11 modules.
7. Create root build.gradle.kts.
8. Create build.gradle.kts, consumer-rules.pro, and source code for all 11 modules:
   - :common (pure Kotlin JVM, MatrixMath.kt)
   - :domain (pure Kotlin JVM, ZERO android.* imports, PointF, Corner, Quad, PageObject, ScanSession, QualityMetrics, Enums)
   - :data (Android library, Room 2.6.1 with KSP, entities, QuadConverter, DAOs, ScanDatabase)
   - :app (Android app, Jetpack Compose, MainActivity.kt, LocalScanApplication)
   - :test-fixtures, :geometry, :detection, :processing, :camera, :pdf, :import (Android libraries with proper namespaces, e.g. com.localscan.imageimport)
9. Create comprehensive unit tests:
   - QuadTest (testing convexity and Shoelace area)
   - PageObjectTest
   - MatrixMathTest (testing projective matrix operations)
   - ConverterTest (testing Room QuadConverter)
10. Build & Test:
    - Run ./gradlew assembleDebug and verify it builds with 0 errors.
    - Run ./gradlew test and verify all tests pass.
11. Checkpoint commit:
    - git add .
    - git commit -m "feat(foundation): initialize multi-module build system and domain layer"

DELIVERABLES:
1. Write d:\Projects\pdfscanner\.agents\teamwork\worker_m1_1\implementation_report.md detailing all created files, commands executed, build outputs, and test results.
2. Write d:\Projects\pdfscanner\.agents\teamwork\worker_m1_1\handoff.md following the Handoff Protocol.
3. Send completion message via send_message to parent (conv ID: d5a8c364-b3d4-4fee-8f48-03456ac7fd55).
