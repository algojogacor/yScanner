# M1 Module Structure, Dependency Wiring, and Build Configurations Plan

**Author:** `explorer_m1_2`  
**Parent:** `teamwork_preview_orchestrator` (`d5a8c364-b3d4-4fee-8f48-03456ac7fd55`)  
**Target Milestone:** M1 (Project Foundation & Multi-Module Architecture)  
**Status:** COMPLETE / SPECIFICATION READY  
**Primary References:** `ORIGINAL_REQUEST.md`, `PROJECT.md`, `ARCHITECTURE.md`, `plans/001-project-foundation.md`, `spec_miner_survey_2/arch_report.md`

---

## 1. Executive Summary & Architectural Invariants

This plan provides the complete, production-ready specification for the **11-module multi-module build system** of yScanner (LocalScan).

### Core Architectural Invariants Enforced by Build Setup:
1. **Pure Kotlin `:domain` Module:**
   - Applies `kotlin("jvm")` only (NEVER `com.android.library`).
   - Possesses **zero** Android SDK dependencies (`android.jar` absent from classpath).
   - Any attempt to import `android.*` inside `:domain` triggers an immediate compile-time error.
   - Enables ultra-fast JVM unit testing (<100 ms execution time) for business rules, state machines, and mathematical models without requiring Robolectric or emulator overhead.
2. **Pure Kotlin `:common` Module:**
   - Applies `kotlin("jvm")` only.
   - Provides pure Kotlin math vectors, 3x3 matrix calculation, coroutine dispatchers, and logging wrappers.
   - Because `:domain` depends on `:common`, having `:common` as a pure Kotlin JVM library guarantees that no Android framework artifacts leak into `:domain`, and prevents Gradle variant resolution failures.
3. **Strict Inward Dependency Graph (DAG):**
   - Dependencies strictly follow Clean Architecture: UI (`:app`) $\longrightarrow$ Infrastructure (`:camera`, `:detection`, `:geometry`, `:processing`, `:data`, `:pdf`, `:import`) $\longrightarrow$ Domain (`:domain`) $\longrightarrow$ Core Primitives (`:common`).
   - Zero circular dependencies. Every module boundary is physically and syntactically enforced by Gradle.
4. **Automated R8 Shrinking & Consumer Rules:**
   - Every Android library module defines a dedicated `consumer-rules.pro` file so that library-specific keep rules (OpenCV JNI, TFLite/LiteRT, Room, CameraX) are automatically consumed by `:app` during minification.
5. **Reserved Keyword Mitigation (`:import` Module):**
   - In Kotlin and Java, `import` is a reserved language keyword.
   - The Gradle module name is `:import` (matching PRD requirements), but its Android namespace and Kotlin package is designated as `com.localscan.imageimport` to eliminate the need for clumsy backtick syntax (``package com.localscan.`import` ``).

---

## 2. Master Module Inventory & Responsibilities

| Module | Plugin Type | Namespace / Group | Dependencies | Key Responsibilities |
|---|---|---|---|---|
| `:common` | Kotlin JVM (`kotlin("jvm")`) | `com.localscan.common` | `kotlinx-coroutines-core` | Math primitives (vectors, 3x3 matrix arithmetic), coroutine dispatcher interfaces, diagnostic logging abstractions. |
| `:domain` | Kotlin JVM (`kotlin("jvm")`) | `com.localscan.domain` | `:common`, `kotlinx-coroutines-core` | Pure business entities (`PageObject`, `Document`, `Quad`, `Corner`, `PointF`), enums, repository & use case interfaces. **Zero Android framework imports.** |
| `:test-fixtures` | Android Library (`com.android.library`) | `com.localscan.testing` | `:domain`, `:common`, Android Core, JUnit, Truth, MockK | Shared synthetic image generators, deterministic binary masks, test doubles (`FakeDocumentDetector`, `FakePdfRenderer`), test assertions. |
| `:geometry` | Android Library (`com.android.library`) | `com.localscan.geometry` | `:domain`, `:common`, OpenCV, Android Core | 7-space coordinate transforms, outer envelope fitting (convex hull), quadrilateral extraction, sub-pixel corner refinement, book spread/gutter curve analysis. |
| `:detection` | Android Library (`com.android.library`) | `com.localscan.detection` | `:geometry`, `:domain`, `:common`, TFLite/LiteRT, OpenCV, Camera Core | On-device ML segmentation, candidate extraction, tap-to-guide selection, temporal tracking (One Euro Filter), auto-capture readiness engine. |
| `:processing` | Android Library (`com.android.library`) | `com.localscan.processing` | `:geometry`, `:domain`, `:common`, OpenCV, Android Core | Perspective correction homography warp, non-destructive image enhancement (`Original`, `Natural`, `Clean`), quality assessment metrics. |
| `:camera` | Android Library (`com.android.library`) | `com.localscan.camera` | `:geometry`, `:domain`, `:common`, CameraX (`core`, `camera2`, `lifecycle`, `view`) | Camera lifecycle management, PreviewView binding, latest-frame ImageAnalysis, high-resolution ImageCapture, tap-to-metering, flash controls. |
| `:data` | Android Library (`com.android.library`) + KSP | `com.localscan.data` | `:domain`, `:common`, Room (`runtime`, `ktx`), Room Compiler (KSP) | Room SQLite persistence, incremental session checkpoints, app-private file storage hierarchy (`SourceAssetManager`), process death recovery. |
| `:pdf` | Android Library (`com.android.library`) | `com.localscan.pdf` | `:processing`, `:domain`, `:common`, Android Core | Streaming page-by-page PDF generation ($O(1)$ memory footprint), 6 page sizes, 3 quality profiles, predictive file size estimation. |
| `:import` | Android Library (`com.android.library`) | `com.localscan.imageimport` | `:processing`, `:geometry`, `:domain`, `:common`, Activity KTX, Exif | Android Photo Picker integration, EXIF orientation normalization, batch ingestion into identical processing pipeline. |
| `:app` | Android App (`com.android.application`) | `com.localscan.app` | All 10 modules, Jetpack Compose BOM, Material3, Navigation | Application entry point (`LocalScanApplication`, `MainActivity`), DI container, Jetpack Compose UI screens, ViewModels. |

---

## 3. Dependency Graph Verification

### Visual Directed Acyclic Graph (DAG)
```text
:app ──────► :camera, :detection, :geometry, :processing, :data, :pdf, :import, :domain, :common
│
├──► :camera ────► :geometry, :domain, :common
│
├──► :detection ─► :geometry, :domain, :common
│
├──► :processing ► :geometry, :domain, :common
│
├──► :geometry ──► :domain, :common
│
├──► :pdf ───────► :processing, :domain, :common
│
├──► :import ────► :processing, :geometry, :domain, :common
│
├──► :data ──────► :domain, :common
│
└──► :domain ────► :common (ZERO Android framework imports)

:test-fixtures ──► :domain, :common (consumed via testImplementation by Android modules)
```

### Inward Boundary Invariant Audit:
- **No Circular Dependencies:** High-level modules depend only on lower-level modules.
- **Domain Decoupling:** `:domain` has NO outward dependencies to `:app`, `:camera`, `:geometry`, `:processing`, `:data`, or `:pdf`.
- **Infrastructure Isolation:** `:camera` does NOT depend on `:detection` (camera provides analysis frames/coordinates; coordinator wires them). `:pdf` does NOT depend on `:camera` or `:detection`.

---

## 4. Module Build Scripts (`build.gradle.kts`) Detailed Specifications

### 4.1 Root Configuration

#### `settings.gradle.kts`
```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "pdfscanner"

include(":app")
include(":camera")
include(":detection")
include(":geometry")
include(":processing")
include(":domain")
include(":data")
include(":pdf")
include(":import")
include(":common")
include(":test-fixtures")
```

#### Root `build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
```

---

### 4.2 `:common` Module Build File

#### `common/build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
```

---

### 4.3 `:domain` Module Build File (CRITICAL: Pure Kotlin JVM)

#### `domain/build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":common"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

---

### 4.4 `:test-fixtures` Module Build File

#### `test-fixtures/build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.localscan.testing"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":common"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)

    api(libs.junit)
    api(libs.truth)
    api(libs.mockk)
}
```

#### `test-fixtures/consumer-rules.pro`
```pro
-keep class com.localscan.testing.** { *; }
```

---

### 4.5 `:geometry` Module Build File

#### `geometry/build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.localscan.geometry"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":common"))

    implementation(libs.opencv)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(project(":test-fixtures"))
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
}
```

#### `geometry/consumer-rules.pro`
```pro
# Preserve OpenCV JNI native entry points
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# Preserve geometry models
-keep class com.localscan.geometry.** { *; }
```

---

### 4.6 `:detection` Module Build File

#### `detection/build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.localscan.detection"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    implementation(project(":geometry"))
    implementation(project(":domain"))
    implementation(project(":common"))

    implementation(libs.opencv)
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.gpu)
    implementation(libs.tensorflow.lite.support)
    implementation(libs.androidx.camera.core) // for ImageProxy analysis frame adapter
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(project(":test-fixtures"))
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
}
```

#### `detection/consumer-rules.pro`
```pro
# Preserve TensorFlow Lite and LiteRT native libraries & delegates
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
-keep class com.google.ai.edge.litert.** { *; }

-keep class com.localscan.detection.** { *; }
```

---

### 4.7 `:processing` Module Build File

#### `processing/build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.localscan.processing"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    implementation(project(":geometry"))
    implementation(project(":domain"))
    implementation(project(":common"))

    implementation(libs.opencv)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(project(":test-fixtures"))
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
}
```

#### `processing/consumer-rules.pro`
```pro
-keep class org.opencv.** { *; }
-keep class com.localscan.processing.** { *; }
```

---

### 4.8 `:camera` Module Build File

#### `camera/build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.localscan.camera"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    implementation(project(":geometry"))
    implementation(project(":domain"))
    implementation(project(":common"))

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(project(":test-fixtures"))
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
}
```

#### `camera/consumer-rules.pro`
```pro
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**
-keep class com.localscan.camera.** { *; }
```

---

### 4.9 `:data` Module Build File (Room + KSP)

#### `data/build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.localscan.data"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":common"))

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(project(":test-fixtures"))
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
}
```

#### `data/consumer-rules.pro`
```pro
# Room Database reflection keep rules
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * implements androidx.room.TypeConverter
-keep class com.localscan.data.** { *; }
```

---

### 4.10 `:pdf` Module Build File

#### `pdf/build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.localscan.pdf"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    implementation(project(":processing"))
    implementation(project(":domain"))
    implementation(project(":common"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(project(":test-fixtures"))
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
}
```

#### `pdf/consumer-rules.pro`
```pro
-keep class com.localscan.pdf.** { *; }
```

---

### 4.11 `:import` Module Build File

#### `import/build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.localscan.imageimport"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    implementation(project(":processing"))
    implementation(project(":geometry"))
    implementation(project(":domain"))
    implementation(project(":common"))

    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(project(":test-fixtures"))
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
}
```

#### `import/consumer-rules.pro`
```pro
-keep class com.localscan.imageimport.** { *; }
```

---

### 4.12 `:app` Module Build File

#### `app/build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.localscan.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.localscan.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    // Project module dependencies
    implementation(project(":camera"))
    implementation(project(":detection"))
    implementation(project(":geometry"))
    implementation(project(":processing"))
    implementation(project(":data"))
    implementation(project(":pdf"))
    implementation(project(":import"))
    implementation(project(":domain"))
    implementation(project(":common"))

    // AndroidX & Architecture
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Testing
    testImplementation(project(":test-fixtures"))
    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
```

#### `app/proguard-rules.pro`
```pro
# Jetpack Compose keep rules
-keep class androidx.compose.** { *; }

# Kotlin Coroutines reflection rules
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Authoritative Domain Models for JSON / State persistence
-keep class com.localscan.domain.model.** { *; }
```

---

## 5. OpenCV Integration Strategy

### 5.1 Evaluated Options

1. **Option A: Pre-built Maven AAR (`com.quickbirdstudios:opencv:4.5.3.0`) [RECOMMENDED FOR M1]**
   - **Distribution:** Hosted directly on Maven Central. No manual zip extraction or external NDK setup required.
   - **Native ABIs:** Automatically packages `.so` binaries for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.
   - **Feature Completeness:** Provides all required OpenCV APIs:
     - `Imgproc.convexHull` (for outer envelope fitting in `:geometry`)
     - `Imgproc.findContours` (for boundary extraction)
     - `Imgproc.getPerspectiveTransform` and `Imgproc.warpPerspective` (for homography in `:processing`)
     - `Imgproc.remap` (for non-linear book spread dewarping)
     - `Imgproc.createCLAHE` and `Imgproc.bilateralFilter` (for Natural & Clean enhancement)
   - **Build Configuration:**
     `gradle/libs.versions.toml`:
     ```toml
     [versions]
     opencv = "4.5.3.0"
     [libraries]
     opencv = { module = "com.quickbirdstudios:opencv", version.ref = "opencv" }
     ```

2. **Option B: Official OpenCV Android SDK (Local AAR / Module Wrapper) [EXTENSIBILITY FALLBACK]**
   - If an engineer wishes to upgrade to OpenCV 4.10.0+ in a later milestone:
     - Place `opencv-release.aar` in `geometry/libs/` or a dedicated `:opencv` module.
     - Add repository `flatDir { dirs("libs") }` to the consuming module.
     - Because all OpenCV calls are strictly isolated behind `:geometry` and `:processing` interfaces, upgrading from Option A to Option B touches zero domain or UI code!

3. **Native Packaging Invariants in `:app`:**
   - To keep APK size bounded, `:app/build.gradle.kts` enforces:
     ```kotlin
     ndk {
         abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
     }
     ```
   - All OpenCV `Mat` objects must be freed via `try / finally { mat.release() }` as mandated by ARCHITECTURE.md §58 and AGENTS.md §58.

---

## 6. Detailed Implementation Checklist for Implementer

| Step | Action | Target Location | Verification |
|---|---|---|---|
| 1 | Create `settings.gradle.kts` | Root directory | Validates all 11 modules included |
| 2 | Create root `build.gradle.kts` | Root directory | Validates plugins applied with `apply false` |
| 3 | Create `:common` build file | `common/build.gradle.kts` | Validates `kotlin("jvm")` and toolchain 21 |
| 4 | Create `:domain` build file | `domain/build.gradle.kts` | Validates `kotlin("jvm")`, zero Android imports |
| 5 | Create `:test-fixtures` build file | `test-fixtures/build.gradle.kts` | Validates Android library, exports test doubles |
| 6 | Create `:geometry` build file & rules | `geometry/build.gradle.kts`, `consumer-rules.pro` | Validates OpenCV dependency & JNI keep rules |
| 7 | Create `:detection` build file & rules | `detection/build.gradle.kts`, `consumer-rules.pro` | Validates TFLite/LiteRT & CameraCore dependency |
| 8 | Create `:processing` build file & rules | `processing/build.gradle.kts`, `consumer-rules.pro` | Validates OpenCV dependency & keep rules |
| 9 | Create `:camera` build file & rules | `camera/build.gradle.kts`, `consumer-rules.pro` | Validates CameraX dependencies & keep rules |
| 10 | Create `:data` build file & rules | `data/build.gradle.kts`, `consumer-rules.pro` | Validates Room + KSP and Room schema config |
| 11 | Create `:pdf` build file & rules | `pdf/build.gradle.kts`, `consumer-rules.pro` | Validates Android library with `:processing` dependency |
| 12 | Create `:import` build file & rules | `import/build.gradle.kts`, `consumer-rules.pro` | Validates namespace `com.localscan.imageimport` |
| 13 | Create `:app` build file & rules | `app/build.gradle.kts`, `proguard-rules.pro` | Validates Compose application wiring all 10 modules |
| 14 | Create module placeholder directories | Each module `src/main/kotlin/com/localscan/...` | Clean source tree ready for code |
| 15 | Verify project assembly | `./gradlew assembleDebug` | Zero compilation errors |
| 16 | Verify project tests | `./gradlew test` | All unit tests pass across all modules |

---

## 7. Risks & Mitigations

1. **Risk:** JVM library `:domain` failing to resolve dependency on Android libraries.
   - **Mitigation:** `:domain` depends *only* on `:common` (which is also configured as a pure Kotlin JVM library). No Android libraries are referenced by `:domain`.
2. **Risk:** Keyword collision with `:import` module.
   - **Mitigation:** Namespace configured as `com.localscan.imageimport`. Kotlin source files will use `package com.localscan.imageimport`, completely eliminating keyword escaping issues.
3. **Risk:** Room KSP annotation processor version mismatch with Kotlin 2.0.x.
   - **Mitigation:** Ensure KSP plugin version strictly matches Kotlin version (e.g., Kotlin 2.0.21 $\longrightarrow$ KSP 2.0.21-1.0.28).
4. **Risk:** OpenCV native library crashes or ABI missing on test devices / emulators.
   - **Mitigation:** Option A (`com.quickbirdstudios:opencv:4.5.3.0`) embeds all 4 standard ABIs (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`), ensuring compatibility across real phones and desktop emulators.
