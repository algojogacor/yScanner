# Handoff Report: Core Domain Entities, Room Persistence, App Shell & M1 Test Verification Suite

**Target Milestone:** M1 — Project Foundation & Build System  
**Author:** `explorer_m1_3`  
**Recipient / Parent:** `teamwork_preview_orchestrator` (`d5a8c364-b3d4-4fee-8f48-03456ac7fd55`)  
**Working Directory:** `d:\Projects\pdfscanner\.agents\teamwork\explorer_m1_3`  
**Handoff Type:** Hard (Task complete)

---

## 1. Observation

1. **Repository Initial State (Greenfield):**
   - Direct execution of `list_dir` on `d:\Projects\pdfscanner` revealed 0 Kotlin/Java source files, 0 Gradle scripts, and 0 directories for modules (`:domain`, `:data`, `:app`, `:common`, etc.).
   - File survey confirmed 35 Markdown documentation files exist, including `PRD.md` (40,848 bytes), `ARCHITECTURE.md` (46,122 bytes), `ORIGINAL_REQUEST.md` (4,963 bytes), and 30 plan files in `plans/`.

2. **Domain Architecture & Constraints:**
   - In `d:\Projects\pdfscanner\.agents\teamwork\teamwork_preview_orchestrator_1\PROJECT.md` (lines 5, 29):
     > line 5: "Pure Kotlin Domain: `:domain` module contains zero Android dependencies, enabling rapid JVM testing of all business logic, state machines, and mathematical representations."
     > line 29: "`:domain ──► :common (ZERO Android framework imports)`"
   - In `PROJECT.md` (lines 124–163), interface contracts for domain models specify:
     > `PointF(val x: Float, val y: Float)`
     > `Corner(val x: Float, val y: Float, val confidence: Float = 1.0f)`
     > `Quad(topLeft: Corner, topRight: Corner, bottomRight: Corner, bottomLeft: Corner)` with `toArray()`, `isConvex()`, `area()`.
     > `EnhancementMode`, `FlashMode`, `CaptureMode`, `PageSize`, `QualityProfile`.
     > `PageObject(id, sessionId, pageIndex, sourceImageUri, detectedQuad, userQuad, rotationDegrees, enhancementMode, isDewarped, qualityMetrics)`.

3. **Room Database & Persistence Architecture:**
   - In `plans/015-session-persistence.md` (lines 19–24, 60–65):
     > line 19: "Room database setup for session metadata and page metadata."
     > line 60: "Create `DocumentEntity` (id, timestamp, status). Create `PageEntity` (id, documentId, orderIndex, sourcePath, derivedPath)."
   - In `ARCHITECTURE.md` §36–38 (lines 1270–1350):
     > "Never delete a source asset while any PageObject still references it."
     > "Source asset reference + geometry + rotation + enhancement parameters -> rendered page."

4. **Minimal App Shell:**
   - In `plans/001-project-foundation.md` (lines 20, 37–38):
     > "Create the `:app` module with a basic Compose `MainActivity` and `Application` class."

5. **Test Fixtures & Verification:**
   - In `ORIGINAL_REQUEST.md` §Acceptance Criteria (lines 29, 81–83):
     > line 29: "Project builds with `./gradlew assembleDebug` without errors"
     > line 81: "Unit tests pass for geometry math, coordinate transforms, quad fitting"
     > line 83: "All tests pass with `./gradlew test`"

---

## 2. Logic Chain

1. **Pure Kotlin Domain Isolation:**  
   - Based on Observation 2 (`PROJECT.md` line 5 and line 29), any dependency on `android.graphics.PointF` or `android.graphics.Rect` in `:domain` would violate the zero-Android-dependency invariant and prevent rapid JVM testing without Robolectric or an emulator.
   - Therefore, `PointF(val x: Float, val y: Float)` and `Corner(val x: Float, val y: Float, val confidence: Float)` must be defined directly in `com.localscan.domain.model` as lightweight, immutable Kotlin data classes.

2. **Geometric Convexity & Shoelace Area Computation:**  
   - A document quad must be strictly convex for perspective rectification homography. Based on Observation 2, `Quad` requires `isConvex()` and `area()`.
   - By calculating the 2D cross product of consecutive edge vectors around the loop (`bottomLeft -> topLeft -> topRight -> bottomRight -> bottomLeft`), a polygon is strictly convex if and only if all 4 cross products have the identical non-zero sign (all positive or all negative).
   - The polygon area is calculated via the Shoelace formula (Gauss's area formula):
     $$\text{Area} = \frac{1}{2} \left| (x_0 y_1 - y_0 x_1) + (x_1 y_2 - y_1 x_2) + (x_2 y_3 - y_2 x_3) + (x_3 y_0 - y_3 x_0) \right|$$
   - This provides $O(1)$ computation with zero allocations.

3. **Room Database Schema & Converters:**  
   - Based on Observation 3, `PageEntity` and `SessionEntity` represent the persistence schema in `:data`.
   - `PageEntity` maintains a `ForeignKey` referencing `SessionEntity(id)` with `onDelete = CASCADE` and indices on `sessionId` and `(sessionId, pageIndex)` for fast sequential queries.
   - A `Quad` requires persistence in SQLite. Rather than pulling in heavyweight reflection-based JSON serializers (Gson/Moshi) that slow down queries or require extra dependencies in M1, `QuadConverter` serializes the 4 vertices (12 floats) into a compact comma-separated String (`"tlX,tlY,tlC,trX,trY,trC,brX,brY,brC,blX,blY,blC"`). Deserialization is an allocation-light split operation.
   - `EnumConverters` maps `EnhancementMode`, `FlashMode`, `CaptureMode`, `PageSize`, and `QualityProfile` by their string names with safe default fallbacks.

4. **App Entry Point & Compose Shell:**  
   - Based on Observation 4, `:app` requires `LocalScanApplication : Application` and `MainActivity : ComponentActivity`.
   - `MainActivity` sets up edge-to-edge Compose with a standard Material3 `Scaffold`, top app bar, and a centered greeting indicating the application is initialized and ready for camera/navigation binding in M2.

5. **Common Matrix Math Foundation:**  
   - Based on Observation 5 and `PROJECT.md` line 7, coordinate mapping between the 7 spaces requires 2D projective and affine matrix math.
   - `MatrixMath` in `com.localscan.common.math` provides pure Kotlin 3x3 matrix multiplication, point mapping (normalized by $w$), and matrix inversion using cofactor expansion.

6. **M1 Test Suite Strategy:**  
   - To satisfy the acceptance criteria in Observation 5 (`./gradlew test`), tests are organized into pure JVM unit tests:
     - `QuadTest.kt` in `:domain`: validates rectangle convexity, trapezoid convexity, concave dart rejection, bowtie self-intersection rejection, Shoelace area, and array serialization.
     - `PageObjectTest.kt` in `:domain`: validates `activeQuad` fallback (userQuad vs detectedQuad), enum dimensions, and immutability.
     - `MatrixMathTest.kt` in `:common`: validates identity mapping, translation, scale, composite multiplication, and matrix inversion.
     - `ConverterTest.kt` in `:data`: validates `QuadConverter` and `EnumConverters` roundtrip serialization.

---

## 3. Caveats

1. **Gradle Dependency between `:domain` and `:common`:**  
   - If `:common` is configured as an Android library (`com.android.library`), a pure Kotlin JVM library (`org.jetbrains.kotlin.jvm`) like `:domain` cannot depend on it in standard Gradle. Therefore, `:domain` has been designed to be completely self-contained with zero dependencies on `:common`. `MatrixMath` resides in `:common` for consumption by `:geometry` and `:camera`.
2. **QualityMetrics Nullability:**  
   - `QualityMetrics` are optional on `PageObject` (null when capturing manually or when metrics evaluation is deferred). In `PageEntity`, metrics are stored as flattened nullable columns for straightforward SQLite indexing and zero migration pain.
3. **Room Database Schema Export:**  
   - `exportSchema = false` is used in the `ScanDatabase` skeleton for M1. As migration testing is introduced in M14, schema export can be enabled with a designated schema location.

---

## 4. Conclusion

The core domain entities, Room persistence layer, minimal Compose app shell, and test verification suite for Milestone M1 are fully specified with production-grade Kotlin implementations in `plan.md`. The design ensures:
- Strict decoupling of domain models from Android platform dependencies.
- Mathematically rigorous Quad convexity and area algorithms.
- Ultra-fast, zero-dependency Room type converters.
- Immediate testability via standard JUnit and Google Truth runners on JVM.

---

## 5. Verification Method

To independently verify the implementation:

1. **Verify Files and Code Structure:**
   Inspect the following files once written by the implementation agent:
   - `domain/src/main/kotlin/com/localscan/domain/model/PointF.kt`
   - `domain/src/main/kotlin/com/localscan/domain/model/Corner.kt`
   - `domain/src/main/kotlin/com/localscan/domain/model/Quad.kt`
   - `domain/src/main/kotlin/com/localscan/domain/model/PageObject.kt`
   - `domain/src/main/kotlin/com/localscan/domain/model/ScanSession.kt`
   - `domain/src/main/kotlin/com/localscan/domain/model/Enums.kt`
   - `data/src/main/kotlin/com/localscan/data/entity/PageEntity.kt`
   - `data/src/main/kotlin/com/localscan/data/entity/SessionEntity.kt`
   - `data/src/main/kotlin/com/localscan/data/db/converter/QuadConverter.kt`
   - `data/src/main/kotlin/com/localscan/data/db/converter/EnumConverters.kt`
   - `data/src/main/kotlin/com/localscan/data/db/dao/PageDao.kt`
   - `data/src/main/kotlin/com/localscan/data/db/dao/SessionDao.kt`
   - `data/src/main/kotlin/com/localscan/data/db/ScanDatabase.kt`
   - `common/src/main/kotlin/com/localscan/common/math/MatrixMath.kt`
   - `app/src/main/kotlin/com/localscan/app/MainActivity.kt`
   - `app/src/main/kotlin/com/localscan/app/LocalScanApplication.kt`

2. **Verify Pure Kotlin Invariant on `:domain`:**
   Inspect `domain/build.gradle.kts`:
   - Must contain `plugins { id("java-library"); id("org.jetbrains.kotlin.jvm") }`.
   - Must NOT contain `com.android.library` or Android dependencies.

3. **Execute Unit Tests:**
   Run Gradle test execution across all modules:
   ```bash
   ./gradlew test
   ```
   *Pass criteria:* All tests in `QuadTest`, `PageObjectTest`, `MatrixMathTest`, and `ConverterTest` pass with 0 failures.

4. **Execute Build:**
   Run debug assembly:
   ```bash
   ./gradlew assembleDebug
   ```
   *Pass criteria:* Build succeeds with 0 errors and generates valid debug APK.
