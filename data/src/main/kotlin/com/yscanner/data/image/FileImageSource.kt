package com.yscanner.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import com.yscanner.domain.image.ImageRect
import com.yscanner.domain.image.ImageRegion
import com.yscanner.domain.image.ImageSource
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream

/**
 * An [ImageSource] backed by [BitmapRegionDecoder], decoding only the requested sub-rectangle of a
 * JPEG.
 *
 * This is the platform half of yScanner's ROI decoding. A full ARGB_8888 decode of a 12 MP capture
 * (4032x3024) is roughly 48 MB, which the scanner must never hold; corner refinement needs only a
 * few hundred pixels around each corner, so callers ask for small regions and this class decodes
 * exactly those, recycles the intermediate [Bitmap] immediately, and returns grayscale floats.
 *
 * ## Not measured on hardware
 *
 * No device is available in this environment, so this class has never executed against a real
 * decoder. No decode-time or peak-memory figure is claimed here; spike `S06-roi-decoding.md` remains
 * open precisely because those numbers require physical hardware.
 *
 * ## Test coverage
 *
 * The [BitmapRegionDecoder] call path — [open], [decodeRegion], [close] — is **not covered by any
 * executable test in this environment**: it needs a real `Bitmap`, which a JVM unit test cannot
 * provide and which the SDK's (absent) emulator cannot either. The risky, error-prone step (mapping
 * the decoded pixels back to a region) is isolated in [regionFromDecodedPixels] and *is* unit-tested;
 * the surrounding Android glue remains device-gated.
 *
 * ## Thread safety
 *
 * [BitmapRegionDecoder] is not documented as thread-safe, so every decode is serialised on an
 * internal lock. [close] is idempotent, and any call made after [close] returns `null` rather than
 * throwing, so a late refinement racing a capture teardown degrades to a miss instead of a crash.
 */
class FileImageSource private constructor(
    private val decoder: BitmapRegionDecoder,
    override val width: Int,
    override val height: Int
) : ImageSource {

    private val lock = Any()

    @Volatile
    private var closed = false

    /** True once [close] has run; exposed so tests can pin the close contract. */
    val isClosed: Boolean get() = closed

    /**
     * Decodes [rect], intersected with the image bounds.
     *
     * @return the decoded grayscale region, or `null` when the request is outside the image, the
     *   decoder returned nothing, or any decode step failed. A `null` return is a recoverable miss.
     *
     * The [ImageRegion] is built from the **bitmap's actual size**, not from the requested size:
     * [BitmapRegionDecoder] is inconsistent on some OEM devices and may hand back a smaller region
     * than asked. The origin is taken from the clamped request, so a size mismatch shrinks the
     * region rather than silently shifting every refined corner.
     */
    override fun decodeRegion(rect: ImageRect): ImageRegion? {
        if (closed) return null
        val clamped = rect.clampTo(width, height) ?: return null

        synchronized(lock) {
            if (closed) return null
            return try {
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bitmap = decoder.decodeRegion(
                    Rect(clamped.left, clamped.top, clamped.right, clamped.bottom),
                    options
                ) ?: return null

                try {
                    val w = bitmap.width
                    val h = bitmap.height
                    if (w <= 0 || h <= 0) return null

                    val argb = IntArray(w * h)
                    bitmap.getPixels(argb, 0, w, 0, 0, w, h)
                    // Requested rect for the origin, decoded size for the dimensions: see
                    // regionFromDecodedPixels for why.
                    regionFromDecodedPixels(clamped, w, h, argb)
                } finally {
                    // Releasing eagerly is the entire point: four live 320x320 ARGB bitmaps is
                    // exactly the memory blow-up this class exists to avoid.
                    bitmap.recycle()
                }
            } catch (t: Throwable) {
                // A refinement miss is recoverable; a crash mid-capture is not.
                null
            }
        }
    }

    /** Releases the native decoder. Idempotent; safe to call from any thread. */
    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            decoder.recycle()
        }
    }

    companion object {
        /**
         * Opens [file] for region decoding, reading only the JPEG header.
         *
         * The file path is handed to the platform decoder rather than slurping the bytes into a
         * `ByteArray`: a 12 MP JPEG is several megabytes and reading it whole would defeat the
         * purpose of partial decoding.
         *
         * @return a source, or `null` when the file cannot be decoded.
         */
        @Suppress("DEPRECATION")
        fun open(file: File): FileImageSource? = try {
            val decoder = BitmapRegionDecoder.newInstance(file.path, false)
            if (decoder == null || decoder.width <= 0 || decoder.height <= 0) {
                decoder?.recycle()
                null
            } else {
                FileImageSource(decoder, decoder.width, decoder.height)
            }
        } catch (t: Throwable) {
            null
        }

        /**
         * Opens [stream] for region decoding.
         *
         * [BitmapRegionDecoder.newInstance] requires an [InputStream] that supports `mark`/`reset`
         * for partial reads, so a stream that does not is wrapped in a [BufferedInputStream]. The
         * stream is not closed by this class; ownership stays with the caller.
         *
         * @return a source, or `null` when the stream cannot be decoded.
         */
        @Suppress("DEPRECATION")
        fun open(stream: InputStream): FileImageSource? = try {
            val seekable = if (stream.markSupported()) stream else BufferedInputStream(stream)
            val decoder = BitmapRegionDecoder.newInstance(seekable, false)
            if (decoder == null || decoder.width <= 0 || decoder.height <= 0) {
                decoder?.recycle()
                null
            } else {
                FileImageSource(decoder, decoder.width, decoder.height)
            }
        } catch (t: Throwable) {
            null
        }
    }
}
