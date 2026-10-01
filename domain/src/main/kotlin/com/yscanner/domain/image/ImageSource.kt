package com.yscanner.domain.image

/**
 * An integer rectangle in **source-image pixel** coordinates.
 *
 * Named `ImageRect` rather than reusing `android.graphics.Rect` so the whole refinement pipeline
 * stays JVM-unit-testable, and to keep it distinct from `com.yscanner.geometry.RectF` (which is
 * float-valued and describes content, not decode requests). [right] and [bottom] are exclusive.
 */
data class ImageRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    /** True when the rectangle encloses at least one pixel. */
    val isEmpty: Boolean get() = width <= 0 || height <= 0

    /**
     * Intersects this rectangle with the image bounds `[0, imageWidth) x [0, imageHeight)`.
     *
     * @return the clamped rectangle, or `null` when the request lies entirely outside the image
     *   (in which case there is nothing to decode and the caller should treat it as a miss rather
     *   than as an empty region).
     */
    fun clampTo(imageWidth: Int, imageHeight: Int): ImageRect? {
        if (imageWidth <= 0 || imageHeight <= 0) return null
        val l = left.coerceIn(0, imageWidth)
        val t = top.coerceIn(0, imageHeight)
        val r = right.coerceIn(0, imageWidth)
        val b = bottom.coerceIn(0, imageHeight)
        val clamped = ImageRect(l, t, r, b)
        return if (clamped.isEmpty) null else clamped
    }

    /**
     * A rectangle of [size] pixels square centred on ([cx], [cy]), before clamping.
     *
     * The centre is rounded to whole pixels because decode requests are integral; the caller is
     * expected to [clampTo] the result.
     */
    companion object {
        fun centredOn(cx: Float, cy: Float, size: Int): ImageRect {
            val half = size / 2
            val x = cx.toInt()
            val y = cy.toInt()
            return ImageRect(x - half, y - half, x - half + size, y - half + size)
        }
    }
}

/**
 * A decoded region of a source image, in **grayscale**.
 *
 * Deliberately not an Android `Bitmap`: the refinement maths must run on the JVM in unit tests, and
 * `:domain` is a pure Kotlin leaf module. Converting whatever the platform decoder produces into
 * this shape is the platform adapter's job.
 *
 * [pixels] is row-major, `width * height` entries, nominally `0f` (black) to `1f` (white). A plain
 * `FloatArray` is used rather than `ByteArray` because the refiner works in gradients and would
 * otherwise convert on every access.
 *
 * @param originX x of this region's left edge **within the source image**.
 * @param originY y of this region's top edge within the source image. Together with [width] and
 *   [height] these let the refiner translate a local result back to absolute image coordinates,
 *   which is where a sign error would silently misplace every refined corner.
 */
class ImageRegion(
    val originX: Int,
    val originY: Int,
    val width: Int,
    val height: Int,
    val pixels: FloatArray
) {
    init {
        require(width >= 0 && height >= 0) {
            "region dimensions must be non-negative but were ${width}x$height"
        }
        require(pixels.size >= width * height) {
            "pixels has ${pixels.size} entries but ${width}x$height needs ${width * height}"
        }
    }

    /**
     * Luminance at ([x], [y]) in **region-local** coordinates, or `0f` outside the region.
     *
     * Out-of-range reads return `0f` rather than throwing so gradient and search loops can run
     * without per-pixel bounds checks. Callers that must distinguish "black" from "outside" should
     * test the coordinates themselves.
     */
    operator fun get(x: Int, y: Int): Float {
        if (x < 0 || y < 0 || x >= width || y >= height) return 0f
        return pixels[y * width + x]
    }

    /** Converts a region-local coordinate to an absolute source-image coordinate. */
    fun toSourceX(localX: Float): Float = originX + localX

    /** Converts a region-local coordinate to an absolute source-image coordinate. */
    fun toSourceY(localY: Float): Float = originY + localY

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImageRegion) return false
        return originX == other.originX &&
            originY == other.originY &&
            width == other.width &&
            height == other.height &&
            pixels.contentEquals(other.pixels)
    }

    override fun hashCode(): Int {
        var result = originX
        result = 31 * result + originY
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + pixels.contentHashCode()
        return result
    }

    override fun toString(): String =
        "ImageRegion(origin=($originX,$originY), size=${width}x$height)"
}

/**
 * Read access to a source image, capable of decoding **subsets** of it.
 *
 * The whole point of this abstraction is memory: a 12 MP capture is ~48 MB as ARGB_8888, and the
 * scanner must never hold one. Corner refinement only needs a few hundred pixels around each
 * corner, so callers ask for small regions and the implementation decodes just those.
 *
 * Implementations must be safe to call from a background thread and must release native resources
 * in [close].
 *
 * The strategy for partial decoding is owned by spike S06 (`plans/spikes/S06-roi-decoding.md`),
 * which is **still open** — its decode-time and peak-memory targets need a physical device to
 * measure. This interface is deliberately strategy-agnostic so that decision can be made later
 * without reworking the pipeline.
 */
interface ImageSource : AutoCloseable {
    /** Full width of the source image in pixels. */
    val width: Int

    /** Full height of the source image in pixels. */
    val height: Int

    /**
     * Decodes [rect], intersected with the image bounds.
     *
     * @return the decoded region, or `null` when the request lies entirely outside the image or the
     *   decoder could not produce a region. A `null` return is a **miss**, not an error: callers are
     *   expected to fall back to their unrefined geometry rather than fail the capture.
     */
    fun decodeRegion(rect: ImageRect): ImageRegion?
}
