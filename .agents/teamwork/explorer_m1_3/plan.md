# Technical Implementation Plan: Core Domain Entities, Room Persistence, App Shell & M1 Test Suite

**Target Milestone:** M1 — Project Foundation & Build System  
**Author:** `explorer_m1_3`  
**Parent Orchestrator:** `teamwork_preview_orchestrator` (`d5a8c364-b3d4-4fee-8f48-03456ac7fd55`)  
**Scope:** Specification of `:domain` entities, `:data` Room persistence, `:app` Compose scaffold, `:common` math primitives, and test suite.

---

## 1. Architectural Invariants & Constraints

1. **Pure Kotlin JVM Domain:**  
   The `:domain` module MUST be a pure Kotlin library (`id("org.jetbrains.kotlin.jvm")`). It contains **zero** Android framework dependencies (`android.*` imports are strictly forbidden). `PointF` and `Corner` are defined directly in `com.localscan.domain.model` as lightweight immutable Kotlin data classes.
2. **Inward-Pointing Dependencies:**  
   - `:domain` has no dependencies on `:data`, `:app`, `:camera`, or `:processing`.
   - `:data` depends on `:domain` and `:common`, mapping Room entities to domain models.
   - `:app` depends on all modules to assemble the final application.
3. **Non-Destructive Domain Modeling:**  
   `PageObject` is the authoritative source of truth for an editable page. It stores the file URI/path of the original captured image along with geometric quads, rotation angle, and enhancement parameters. Bitmaps are ephemeral derived artifacts.
4. **Offline & Resource Safe:**  
   Zero cloud/network calls. All data remains in app-private storage. Room database operations and mathematical transformations are fully offline.

---

## 2. Core Domain Entities (`:domain`)

All domain entities reside in package `com.localscan.domain.model`.

### 2.1 `PointF.kt`
```kotlin
package com.localscan.domain.model

import kotlin.math.hypot

/**
 * Pure Kotlin immutable 2D floating-point coordinate representation.
 * Free of android.graphics.PointF dependencies for JVM testability.
 */
data class PointF(
    val x: Float,
    val y: Float
) {
    fun distanceTo(other: PointF): Float = hypot(x - other.x, y - other.y)

    operator fun plus(other: PointF): PointF = PointF(x + other.x, y + other.y)
    operator fun minus(other: PointF): PointF = PointF(x - other.x, y - other.y)
    operator fun times(factor: Float): PointF = PointF(x * factor, y * factor)
}
```

### 2.2 `Corner.kt`
```kotlin
package com.localscan.domain.model

/**
 * A document corner vertex with location and detection confidence [0.0, 1.0].
 */
data class Corner(
    val x: Float,
    val y: Float,
    val confidence: Float = 1.0f
) {
    fun toPointF(): PointF = PointF(x, y)

    companion object {
        fun fromPointF(point: PointF, confidence: Float = 1.0f): Corner =
            Corner(point.x, point.y, confidence)
    }
}
```

### 2.3 `Quad.kt`
```kotlin
package com.localscan.domain.model

import kotlin.math.abs

/**
 * An ordered quadrilateral defined by four corners:
 * topLeft, topRight, bottomRight, bottomLeft (clockwise order).
 */
data class Quad(
    val topLeft: Corner,
    val topRight: Corner,
    val bottomRight: Corner,
    val bottomLeft: Corner
) {
    /**
     * Serializes coordinates into an 8-float array:
     * [tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y]
     */
    fun toArray(): FloatArray = floatArrayOf(
        topLeft.x, topLeft.y,
        topRight.x, topRight.y,
        bottomRight.x, bottomRight.y,
        bottomLeft.x, bottomLeft.y
    )

    /**
     * Computes the 2D cross product of vector OA and vector OB where:
     * OA = A - O, OB = B - O.
     * crossProduct = (A.x - O.x) * (B.y - O.y) - (A.y - O.y) * (B.x - O.x)
     */
    private fun crossProduct(o: Corner, a: Corner, b: Corner): Float {
        return (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    }

    /**
     * Determines whether the quadrilateral is strictly convex.
     * In a convex polygon, all consecutive edge cross products have the identical sign.
     * Order of vertices: topLeft -> topRight -> bottomRight -> bottomLeft.
     */
    fun isConvex(): Boolean {
        val cp1 = crossProduct(bottomLeft, topLeft, topRight)
        val cp2 = crossProduct(topLeft, topRight, bottomRight)
        val cp3 = crossProduct(topRight, bottomRight, bottomLeft)
        val cp4 = crossProduct(bottomRight, bottomLeft, topLeft)

        val epsilon = 1e-5f
        val hasPositive = (cp1 > epsilon) || (cp2 > epsilon) || (cp3 > epsilon) || (cp4 > epsilon)
        val hasNegative = (cp1 < -epsilon) || (cp2 < -epsilon) || (cp3 < -epsilon) || (cp4 < -epsilon)

        return !(hasPositive && hasNegative) && (hasPositive || hasNegative)
    }

    /**
     * Calculates the polygon area using the Shoelace formula (Gauss's area formula):
     * Area = 0.5 * |(x0*y1 - y0*x1) + (x1*y2 - y1*x2) + (x2*y3 - y2*x3) + (x3*y0 - y3*x0)|
     */
    fun area(): Float {
        val p0 = topLeft
        val p1 = topRight
        val p2 = bottomRight
        val p3 = bottomLeft
        val sum = (p0.x * p1.y - p0.y * p1.x) +
                  (p1.x * p2.y - p1.y * p2.x) +
                  (p2.x * p3.y - p2.y * p3.x) +
                  (p3.x * p0.y - p3.y * p0.x)
        return abs(sum) * 0.5f
    }

    companion object {
        fun fromArray(array: FloatArray): Quad {
            require(array.size >= 8) { "Array must contain at least 8 floats" }
            return Quad(
                topLeft = Corner(array[0], array[1]),
                topRight = Corner(array[2], array[3]),
                bottomRight = Corner(array[4], array[5]),
                bottomLeft = Corner(array[6], array[7])
            )
        }
    }
}
```

### 2.4 Domain Enums
```kotlin
package com.localscan.domain.model

enum class EnhancementMode {
    ORIGINAL,
    NATURAL,
    CLEAN
}

enum class FlashMode {
    OFF,
    ON,
    TORCH
}

enum class CaptureMode {
    ONE_PAGE,
    TWO_PAGE
}

enum class PageSize(val widthPt: Float, val heightPt: Float) {
    A4(595.28f, 841.89f),
    AUTO(0f, 0f),
    A5(419.53f, 595.28f),
    B5(498.90f, 708.66f),
    LETTER(612.00f, 792.00f),
    ORIGINAL_RATIO(0f, 0f)
}

enum class QualityProfile(
    val compressionQuality: Int,
    val maxDimension: Int?
) {
    HIGH(compressionQuality = 90, maxDimension = null),
    BALANCED(compressionQuality = 80, maxDimension = 2048),
    SMALL(compressionQuality = 65, maxDimension = 1280)
}
```

### 2.5 `QualityMetrics.kt`
```kotlin
package com.localscan.domain.model

/**
 * Diagnostic metrics evaluating the visual and geometric quality of a document capture.
 */
data class QualityMetrics(
    val blurScore: Float = 0f,
    val glareScore: Float = 0f,
    val shadowScore: Float = 0f,
    val exposureScore: Float = 0f,
    val geometryScore: Float = 0f,
    val cropConfidence: Float = 0f,
    val cornerConfidence: Float = 0f,
    val overallScore: Float = 0f
)
```

### 2.6 `PageObject.kt`
```kotlin
package com.localscan.domain.model

/**
 * Authoritative domain model representing a single scanned page.
 */
data class PageObject(
    val id: String,
    val sessionId: String,
    val pageIndex: Int,
    val sourceImageUri: String,
    val detectedQuad: Quad,
    val userQuad: Quad? = null,
    val rotationDegrees: Int = 0,
    val enhancementMode: EnhancementMode = EnhancementMode.NATURAL,
    val isDewarped: Boolean = false,
    val qualityMetrics: QualityMetrics? = null
) {
    val activeQuad: Quad get() = userQuad ?: detectedQuad
}
```

### 2.7 `ScanSession.kt`
```kotlin
package com.localscan.domain.model

/**
 * Represents a document scanning session containing an ordered collection of pages.
 */
data class ScanSession(
    val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val pages: List<PageObject> = emptyList()
) {
    val pageCount: Int get() = pages.size
}
```

---

## 3. Room Persistence Foundation (`:data`)

All persistence code resides in `:data` under package `com.localscan.data`.

### 3.1 `SessionEntity.kt`
```kotlin
package com.localscan.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.localscan.domain.model.PageObject
import com.localscan.domain.model.ScanSession

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val createdAt: Long,
    val modifiedAt: Long
) {
    fun toDomain(pages: List<PageObject> = emptyList()): ScanSession = ScanSession(
        id = id,
        name = name,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        pages = pages
    )

    companion object {
        fun fromDomain(session: ScanSession): SessionEntity = SessionEntity(
            id = session.id,
            name = session.name,
            createdAt = session.createdAt,
            modifiedAt = session.modifiedAt
        )
    }
}
```

### 3.2 `PageEntity.kt`
```kotlin
package com.localscan.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.localscan.domain.model.EnhancementMode
import com.localscan.domain.model.PageObject
import com.localscan.domain.model.Quad
import com.localscan.domain.model.QualityMetrics

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["sessionId", "pageIndex"])
    ]
)
data class PageEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val pageIndex: Int,
    val sourceImageUri: String,
    val detectedQuad: Quad,
    val userQuad: Quad? = null,
    val rotationDegrees: Int = 0,
    val enhancementMode: EnhancementMode = EnhancementMode.NATURAL,
    val isDewarped: Boolean = false,
    val blurScore: Float? = null,
    val glareScore: Float? = null,
    val shadowScore: Float? = null,
    val exposureScore: Float? = null,
    val geometryScore: Float? = null,
    val cropConfidence: Float? = null,
    val cornerConfidence: Float? = null,
    val overallScore: Float? = null
) {
    fun toDomain(): PageObject = PageObject(
        id = id,
        sessionId = sessionId,
        pageIndex = pageIndex,
        sourceImageUri = sourceImageUri,
        detectedQuad = detectedQuad,
        userQuad = userQuad,
        rotationDegrees = rotationDegrees,
        enhancementMode = enhancementMode,
        isDewarped = isDewarped,
        qualityMetrics = if (overallScore != null || blurScore != null) {
            QualityMetrics(
                blurScore = blurScore ?: 0f,
                glareScore = glareScore ?: 0f,
                shadowScore = shadowScore ?: 0f,
                exposureScore = exposureScore ?: 0f,
                geometryScore = geometryScore ?: 0f,
                cropConfidence = cropConfidence ?: 0f,
                cornerConfidence = cornerConfidence ?: 0f,
                overallScore = overallScore ?: 0f
            )
        } else null
    )

    companion object {
        fun fromDomain(page: PageObject): PageEntity = PageEntity(
            id = page.id,
            sessionId = page.sessionId,
            pageIndex = page.pageIndex,
            sourceImageUri = page.sourceImageUri,
            detectedQuad = page.detectedQuad,
            userQuad = page.userQuad,
            rotationDegrees = page.rotationDegrees,
            enhancementMode = page.enhancementMode,
            isDewarped = page.isDewarped,
            blurScore = page.qualityMetrics?.blurScore,
            glareScore = page.qualityMetrics?.glareScore,
            shadowScore = page.qualityMetrics?.shadowScore,
            exposureScore = page.qualityMetrics?.exposureScore,
            geometryScore = page.qualityMetrics?.geometryScore,
            cropConfidence = page.qualityMetrics?.cropConfidence,
            cornerConfidence = page.qualityMetrics?.cornerConfidence,
            overallScore = page.qualityMetrics?.overallScore
        )
    }
}
```

### 3.3 `QuadConverter.kt`
Zero-dependency, high-speed string serialization for Room:
```kotlin
package com.localscan.data.db.converter

import androidx.room.TypeConverter
import com.localscan.domain.model.Corner
import com.localscan.domain.model.Quad

class QuadConverter {
    @TypeConverter
    fun fromQuad(quad: Quad?): String? {
        if (quad == null) return null
        return buildString {
            append(quad.topLeft.x).append(',').append(quad.topLeft.y).append(',').append(quad.topLeft.confidence).append(',')
            append(quad.topRight.x).append(',').append(quad.topRight.y).append(',').append(quad.topRight.confidence).append(',')
            append(quad.bottomRight.x).append(',').append(quad.bottomRight.y).append(',').append(quad.bottomRight.confidence).append(',')
            append(quad.bottomLeft.x).append(',').append(quad.bottomLeft.y).append(',').append(quad.bottomLeft.confidence)
        }
    }

    @TypeConverter
    fun toQuad(value: String?): Quad? {
        if (value.isNullOrBlank()) return null
        val parts = value.split(',')
        if (parts.size < 8) return null

        val tlC = if (parts.size >= 12) parts[2].toFloatOrNull() ?: 1.0f else 1.0f
        val trC = if (parts.size >= 12) parts[5].toFloatOrNull() ?: 1.0f else 1.0f
        val brC = if (parts.size >= 12) parts[8].toFloatOrNull() ?: 1.0f else 1.0f
        val blC = if (parts.size >= 12) parts[11].toFloatOrNull() ?: 1.0f else 1.0f

        return if (parts.size >= 12) {
            Quad(
                topLeft = Corner(parts[0].toFloat(), parts[1].toFloat(), tlC),
                topRight = Corner(parts[3].toFloat(), parts[4].toFloat(), trC),
                bottomRight = Corner(parts[6].toFloat(), parts[7].toFloat(), brC),
                bottomLeft = Corner(parts[9].toFloat(), parts[10].toFloat(), blC)
            )
        } else {
            Quad(
                topLeft = Corner(parts[0].toFloat(), parts[1].toFloat(), 1f),
                topRight = Corner(parts[2].toFloat(), parts[3].toFloat(), 1f),
                bottomRight = Corner(parts[4].toFloat(), parts[5].toFloat(), 1f),
                bottomLeft = Corner(parts[6].toFloat(), parts[7].toFloat(), 1f)
            )
        }
    }
}
```

### 3.4 `EnumConverters.kt`
```kotlin
package com.localscan.data.db.converter

import androidx.room.TypeConverter
import com.localscan.domain.model.*

class EnumConverters {
    @TypeConverter
    fun fromEnhancementMode(mode: EnhancementMode?): String? = mode?.name

    @TypeConverter
    fun toEnhancementMode(value: String?): EnhancementMode? =
        value?.let { runCatching { EnhancementMode.valueOf(it) }.getOrDefault(EnhancementMode.NATURAL) }

    @TypeConverter
    fun fromFlashMode(mode: FlashMode?): String? = mode?.name

    @TypeConverter
    fun toFlashMode(value: String?): FlashMode? =
        value?.let { runCatching { FlashMode.valueOf(it) }.getOrDefault(FlashMode.OFF) }

    @TypeConverter
    fun fromCaptureMode(mode: CaptureMode?): String? = mode?.name

    @TypeConverter
    fun toCaptureMode(value: String?): CaptureMode? =
        value?.let { runCatching { CaptureMode.valueOf(it) }.getOrDefault(CaptureMode.ONE_PAGE) }

    @TypeConverter
    fun fromPageSize(size: PageSize?): String? = size?.name

    @TypeConverter
    fun toPageSize(value: String?): PageSize? =
        value?.let { runCatching { PageSize.valueOf(it) }.getOrDefault(PageSize.A4) }

    @TypeConverter
    fun fromQualityProfile(profile: QualityProfile?): String? = profile?.name

    @TypeConverter
    fun toQualityProfile(value: String?): QualityProfile? =
        value?.let { runCatching { QualityProfile.valueOf(it) }.getOrDefault(QualityProfile.BALANCED) }
}
```

### 3.5 DAOs & ScanDatabase
```kotlin
package com.localscan.data.db.dao

import androidx.room.*
import com.localscan.data.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: String): SessionEntity?

    @Query("SELECT * FROM sessions ORDER BY modifiedAt DESC")
    fun observeAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY modifiedAt DESC LIMIT 1")
    suspend fun getLatestSession(): SessionEntity?

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: String)

    @Query("DELETE FROM sessions")
    suspend fun deleteAllSessions()
}
```

```kotlin
package com.localscan.data.db.dao

import androidx.room.*
import com.localscan.data.entity.PageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: PageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<PageEntity>)

    @Update
    suspend fun updatePage(page: PageEntity)

    @Query("SELECT * FROM pages WHERE id = :pageId")
    suspend fun getPageById(pageId: String): PageEntity?

    @Query("SELECT * FROM pages WHERE sessionId = :sessionId ORDER BY pageIndex ASC")
    fun observePagesForSession(sessionId: String): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE sessionId = :sessionId ORDER BY pageIndex ASC")
    suspend fun getPagesForSession(sessionId: String): List<PageEntity>

    @Query("SELECT COUNT(*) FROM pages WHERE sessionId = :sessionId")
    suspend fun getPageCountForSession(sessionId: String): Int

    @Query("DELETE FROM pages WHERE id = :pageId")
    suspend fun deletePageById(pageId: String)

    @Query("DELETE FROM pages WHERE sessionId = :sessionId")
    suspend fun deletePagesForSession(sessionId: String)

    @Transaction
    suspend fun updatePageIndices(reorderedPages: List<PageEntity>) {
        for (page in reorderedPages) {
            updatePage(page)
        }
    }
}
```

```kotlin
package com.localscan.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.localscan.data.db.converter.EnumConverters
import com.localscan.data.db.converter.QuadConverter
import com.localscan.data.db.dao.PageDao
import com.localscan.data.db.dao.SessionDao
import com.localscan.data.entity.PageEntity
import com.localscan.data.entity.SessionEntity

@Database(
    entities = [
        SessionEntity::class,
        PageEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(
    QuadConverter::class,
    EnumConverters::class
)
abstract class ScanDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun pageDao(): PageDao

    companion object {
        const val DATABASE_NAME = "localscan_database.db"
    }
}
```

---

## 4. Minimal Compose App Shell (`:app`)

### 4.1 `LocalScanApplication.kt`
```kotlin
package com.localscan.app

import android.app.Application

class LocalScanApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
```

### 4.2 `MainActivity.kt`
```kotlin
package com.localscan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocalScanTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("LocalScan") },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "LocalScan Initialized",
                                style = MaterialTheme.typography.headlineMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LocalScanTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
```

---

## 5. Common Matrix Math Helper (`:common`)

File: `common/src/main/kotlin/com/localscan/common/math/MatrixMath.kt`
Pure Kotlin implementation of 3x3 projective and affine transformations to support coordinate mapping between spaces:

```kotlin
package com.localscan.common.math

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Pure Kotlin 3x3 matrix math helpers for 2D coordinate spaces and projective homography.
 * A 3x3 matrix is represented as a FloatArray of size 9 in row-major order:
 * [ m00, m01, m02,
 *   m10, m11, m12,
 *   m20, m21, m22 ]
 */
object MatrixMath {

    fun identity(): FloatArray = floatArrayOf(
        1f, 0f, 0f,
        0f, 1f, 0f,
        0f, 0f, 1f
    )

    fun translation(tx: Float, ty: Float): FloatArray = floatArrayOf(
        1f, 0f, tx,
        0f, 1f, ty,
        0f, 0f, 1f
    )

    fun scale(sx: Float, sy: Float): FloatArray = floatArrayOf(
        sx, 0f, 0f,
        0f, sy, 0f,
        0f, 0f, 1f
    )

    fun rotation(degrees: Float): FloatArray {
        val rad = Math.toRadians(degrees.toDouble()).toFloat()
        val c = cos(rad)
        val s = sin(rad)
        return floatArrayOf(
            c, -s, 0f,
            s,  c, 0f,
            0f, 0f, 1f
        )
    }

    fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        require(a.size == 9 && b.size == 9) { "Matrices must be 3x3 (size 9)" }
        val result = FloatArray(9)
        for (row in 0..2) {
            for (col in 0..2) {
                var sum = 0f
                for (k in 0..2) {
                    sum += a[row * 3 + k] * b[k * 3 + col]
                }
                result[row * 3 + col] = sum
            }
        }
        return result
    }

    /**
     * Transforms a 2D point (x, y) through the 3x3 projective matrix.
     * Normalized by w = m20*x + m21*y + m22.
     */
    fun mapPoint(m: FloatArray, x: Float, y: Float): Pair<Float, Float> {
        require(m.size == 9) { "Matrix must be 3x3" }
        var w = m[6] * x + m[7] * y + m[8]
        if (abs(w) < 1e-7f) w = 1f
        val px = (m[0] * x + m[1] * y + m[2]) / w
        val py = (m[3] * x + m[4] * y + m[5]) / w
        return Pair(px, py)
    }

    /**
     * Inverts a 3x3 matrix. Returns null if matrix is singular (det ≈ 0).
     */
    fun invert(m: FloatArray): FloatArray? {
        require(m.size == 9) { "Matrix must be 3x3" }
        val a00 = m[0]; val a01 = m[1]; val a02 = m[2]
        val a10 = m[3]; val a11 = m[4]; val a12 = m[5]
        val a20 = m[6]; val a21 = m[7]; val a22 = m[8]

        val det = a00 * (a11 * a22 - a12 * a21) -
                  a01 * (a10 * a22 - a12 * a20) +
                  a02 * (a10 * a21 - a11 * a20)

        if (abs(det) < 1e-9f) return null

        val invDet = 1.0f / det
        return floatArrayOf(
            (a11 * a22 - a12 * a21) * invDet,
            (a02 * a21 - a01 * a22) * invDet,
            (a01 * a12 - a02 * a11) * invDet,
            (a12 * a20 - a10 * a22) * invDet,
            (a00 * a22 - a02 * a20) * invDet,
            (a02 * a10 - a00 * a12) * invDet,
            (a10 * a21 - a11 * a20) * invDet,
            (a01 * a20 - a00 * a21) * invDet,
            (a00 * a11 - a01 * a10) * invDet
        )
    }

    fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float = hypot(x2 - x1, y2 - y1)

    fun clamp(value: Float, min: Float, max: Float): Float = value.coerceIn(min, max)
}
```

---

## 6. M1 Verification Suite

### 6.1 `:domain` Unit Tests (`domain/src/test/kotlin/com/localscan/domain/model/QuadTest.kt`)
```kotlin
package com.localscan.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QuadTest {

    @Test
    fun `isConvex returns true for regular rectangle`() {
        val quad = Quad(
            topLeft = Corner(0f, 0f),
            topRight = Corner(100f, 0f),
            bottomRight = Corner(100f, 200f),
            bottomLeft = Corner(0f, 200f)
        )
        assertThat(quad.isConvex()).isTrue()
    }

    @Test
    fun `isConvex returns true for rotated trapezoid`() {
        val quad = Quad(
            topLeft = Corner(20f, 10f),
            topRight = Corner(80f, 15f),
            bottomRight = Corner(95f, 190f),
            bottomLeft = Corner(5f, 185f)
        )
        assertThat(quad.isConvex()).isTrue()
    }

    @Test
    fun `isConvex returns false for concave dart shape`() {
        val quad = Quad(
            topLeft = Corner(0f, 0f),
            topRight = Corner(100f, 0f),
            bottomRight = Corner(50f, 50f), // indented inward corner
            bottomLeft = Corner(0f, 100f)
        )
        assertThat(quad.isConvex()).isFalse()
    }

    @Test
    fun `isConvex returns false for self-intersecting bowtie quad`() {
        val quad = Quad(
            topLeft = Corner(0f, 0f),
            topRight = Corner(100f, 100f), // crossed edges
            bottomRight = Corner(100f, 0f),
            bottomLeft = Corner(0f, 100f)
        )
        assertThat(quad.isConvex()).isFalse()
    }

    @Test
    fun `area computes exact rectangular area`() {
        val quad = Quad(
            topLeft = Corner(0f, 0f),
            topRight = Corner(100f, 0f),
            bottomRight = Corner(100f, 200f),
            bottomLeft = Corner(0f, 200f)
        )
        assertThat(quad.area()).isEqualTo(20000.0f)
    }

    @Test
    fun `area computes trapezoidal area accurately`() {
        // Trapezoid with top edge 60, bottom edge 100, height 100
        val quad = Quad(
            topLeft = Corner(20f, 0f),
            topRight = Corner(80f, 0f),
            bottomRight = Corner(100f, 100f),
            bottomLeft = Corner(0f, 100f)
        )
        // Area = 0.5 * (60 + 100) * 100 = 8000.0f
        assertThat(quad.area()).isEqualTo(8000.0f)
    }

    @Test
    fun `toArray and fromArray serialize and deserialize symmetrically`() {
        val original = Quad(
            topLeft = Corner(12.5f, 34.2f),
            topRight = Corner(56.7f, 78.1f),
            bottomRight = Corner(90.3f, 12.8f),
            bottomLeft = Corner(34.9f, 98.4f)
        )
        val array = original.toArray()
        assertThat(array.size).isEqualTo(8)

        val restored = Quad.fromArray(array)
        assertThat(restored.topLeft.x).isEqualTo(original.topLeft.x)
        assertThat(restored.topLeft.y).isEqualTo(original.topLeft.y)
        assertThat(restored.topRight.x).isEqualTo(original.topRight.x)
        assertThat(restored.bottomRight.x).isEqualTo(original.bottomRight.x)
        assertThat(restored.bottomLeft.x).isEqualTo(original.bottomLeft.x)
    }
}
```

### 6.2 `:domain` PageObject & Enum Tests (`domain/src/test/kotlin/com/localscan/domain/model/PageObjectTest.kt`)
```kotlin
package com.localscan.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PageObjectTest {

    private val sampleQuad = Quad(
        topLeft = Corner(0f, 0f),
        topRight = Corner(100f, 0f),
        bottomRight = Corner(100f, 150f),
        bottomLeft = Corner(0f, 150f)
    )

    @Test
    fun `activeQuad defaults to detectedQuad when userQuad is null`() {
        val page = PageObject(
            id = "p1",
            sessionId = "s1",
            pageIndex = 0,
            sourceImageUri = "file:///tmp/img1.jpg",
            detectedQuad = sampleQuad
        )
        assertThat(page.activeQuad).isEqualTo(sampleQuad)
    }

    @Test
    fun `activeQuad prefers userQuad when present`() {
        val userModifiedQuad = sampleQuad.copy(
            topLeft = Corner(10f, 10f)
        )
        val page = PageObject(
            id = "p1",
            sessionId = "s1",
            pageIndex = 0,
            sourceImageUri = "file:///tmp/img1.jpg",
            detectedQuad = sampleQuad,
            userQuad = userModifiedQuad
        )
        assertThat(page.activeQuad).isEqualTo(userModifiedQuad)
    }

    @Test
    fun `PageSize constants have correct point dimensions`() {
        assertThat(PageSize.A4.widthPt).isWithin(0.01f).of(595.28f)
        assertThat(PageSize.A4.heightPt).isWithin(0.01f).of(841.89f)
        assertThat(PageSize.LETTER.widthPt).isWithin(0.01f).of(612.00f)
        assertThat(PageSize.LETTER.heightPt).isWithin(0.01f).of(792.00f)
    }

    @Test
    fun `QualityProfile compression values adhere to spec`() {
        assertThat(QualityProfile.HIGH.compressionQuality).isEqualTo(90)
        assertThat(QualityProfile.HIGH.maxDimension).isNull()

        assertThat(QualityProfile.BALANCED.compressionQuality).isEqualTo(80)
        assertThat(QualityProfile.BALANCED.maxDimension).isEqualTo(2048)

        assertThat(QualityProfile.SMALL.compressionQuality).isEqualTo(65)
        assertThat(QualityProfile.SMALL.maxDimension).isEqualTo(1280)
    }
}
```

### 6.3 `:common` Unit Tests (`common/src/test/kotlin/com/localscan/common/math/MatrixMathTest.kt`)
```kotlin
package com.localscan.common.math

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MatrixMathTest {

    @Test
    fun `identity matrix maps point to exact coordinates`() {
        val identity = MatrixMath.identity()
        val (x, y) = MatrixMath.mapPoint(identity, 42.5f, 108.2f)
        assertThat(x).isWithin(1e-5f).of(42.5f)
        assertThat(y).isWithin(1e-5f).of(108.2f)
    }

    @Test
    fun `translation shifts coordinates by specified offsets`() {
        val translation = MatrixMath.translation(15f, -25f)
        val (x, y) = MatrixMath.mapPoint(translation, 10f, 50f)
        assertThat(x).isWithin(1e-5f).of(25f)
        assertThat(y).isWithin(1e-5f).of(25f)
    }

    @Test
    fun `scale scales coordinates by specified factors`() {
        val scale = MatrixMath.scale(2.5f, 0.5f)
        val (x, y) = MatrixMath.mapPoint(scale, 100f, 200f)
        assertThat(x).isWithin(1e-5f).of(250f)
        assertThat(y).isWithin(1e-5f).of(100f)
    }

    @Test
    fun `multiply correctly composes scale and translation`() {
        val t = MatrixMath.translation(50f, 50f)
        val s = MatrixMath.scale(2f, 2f)
        // Composite M = T * S -> scales first, then translates
        val m = MatrixMath.multiply(t, s)
        val (x, y) = MatrixMath.mapPoint(m, 10f, 20f)
        assertThat(x).isWithin(1e-5f).of(70f) // 10*2 + 50 = 70
        assertThat(y).isWithin(1e-5f).of(90f) // 20*2 + 50 = 90
    }

    @Test
    fun `invert inverts invertible matrix`() {
        val s = MatrixMath.scale(4f, 5f)
        val inv = MatrixMath.invert(s)
        assertThat(inv).isNotNull()

        val (x, y) = MatrixMath.mapPoint(inv!!, 100f, 100f)
        assertThat(x).isWithin(1e-5f).of(25f)
        assertThat(y).isWithin(1e-5f).of(20f)
    }

    @Test
    fun `invert returns null for singular matrix`() {
        val singular = FloatArray(9) { 0f }
        assertThat(MatrixMath.invert(singular)).isNull()
    }
}
```

### 6.4 `:data` Unit Tests (`data/src/test/kotlin/com/localscan/data/db/converter/ConverterTest.kt`)
```kotlin
package com.localscan.data.db.converter

import com.google.common.truth.Truth.assertThat
import com.localscan.domain.model.Corner
import com.localscan.domain.model.EnhancementMode
import com.localscan.domain.model.Quad
import org.junit.Test

class ConverterTest {

    private val quadConverter = QuadConverter()
    private val enumConverters = EnumConverters()

    @Test
    fun `QuadConverter roundtrip serialization preserves values`() {
        val original = Quad(
            topLeft = Corner(1.1f, 2.2f, 0.95f),
            topRight = Corner(3.3f, 4.4f, 0.90f),
            bottomRight = Corner(5.5f, 6.6f, 0.85f),
            bottomLeft = Corner(7.7f, 8.8f, 0.80f)
        )
        val serialized = quadConverter.fromQuad(original)
        assertThat(serialized).isNotNull()

        val deserialized = quadConverter.toQuad(serialized)
        assertThat(deserialized).isEqualTo(original)
    }

    @Test
    fun `QuadConverter gracefully handles null and malformed strings`() {
        assertThat(quadConverter.fromQuad(null)).isNull()
        assertThat(quadConverter.toQuad(null)).isNull()
        assertThat(quadConverter.toQuad("")).isNull()
        assertThat(quadConverter.toQuad("1.0,2.0")).isNull() // Less than 8 parts
    }

    @Test
    fun `EnumConverters correctly serializes and deserializes EnhancementMode`() {
        val mode = EnhancementMode.CLEAN
        val name = enumConverters.fromEnhancementMode(mode)
        assertThat(name).isEqualTo("CLEAN")
        assertThat(enumConverters.toEnhancementMode(name)).isEqualTo(EnhancementMode.CLEAN)
        assertThat(enumConverters.toEnhancementMode("INVALID_NAME")).isEqualTo(EnhancementMode.NATURAL)
    }
}
```

---

## 7. Verification & Build Commands

After implementation of files across `:domain`, `:common`, `:data`, and `:app`:

1. **Clean and Build Root Debug APK:**
   ```bash
   ./gradlew assembleDebug
   ```
   *Expected outcome:* Build finishes successfully with zero compilation or packaging errors.

2. **Execute Full Test Suite:**
   ```bash
   ./gradlew test
   ```
   *Expected outcome:* All unit tests in `:domain`, `:common`, and `:data` pass without failures.

3. **Verify Pure Kotlin Invariant on `:domain`:**
   Inspect `domain/build.gradle.kts` and ensure only:
   ```kotlin
   plugins {
       id("java-library")
       id("org.jetbrains.kotlin.jvm")
   }
   ```
   and zero references to `com.android.library`.
