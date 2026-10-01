package com.yscanner.data.image

import com.yscanner.domain.image.ImageRect
import com.yscanner.domain.image.ImageRegion

/**
 * Builds an [ImageRegion] from the raw pixels a platform decoder produced, without touching any
 * Android type.
 *
 * This exists so the one genuinely subtle step of ROI decoding — reconciling the size we *asked*
 * for with the size we *got* — can be unit-tested on the JVM, where a real [android.graphics.Bitmap]
 * is unavailable. [FileImageSource] is a thin Android adapter around this function.
 *
 * ## Shrink rather than shift
 *
 * [requested] is the **already-clamped** rectangle that was handed to the decoder, while
 * [decodedWidth] and [decodedHeight] describe the bitmap that actually came back.
 * [android.graphics.BitmapRegionDecoder] is inconsistent on some OEM devices and may return a
 * smaller region than asked. The origin therefore comes from [requested] (`left`/`top`) while the
 * region dimensions come from the decoded size — a mismatch shrinks the region. Deriving the origin
 * from the decoded size would instead shift every refined corner by the shortfall, silently. Note
 * that `requested.width`/`requested.height` are deliberately **not** consulted: the decoded size is
 * the source of truth for the region's dimensions.
 *
 * ## Total
 *
 * The function never throws. Every input that cannot yield a well-formed region — an empty decoded
 * size, an [argb] buffer too short to hold it, or a [luminance] scratch buffer too short to receive
 * it — returns `null`.
 *
 * @return the region, or `null` when the inputs cannot produce one.
 */
internal fun regionFromDecodedPixels(
    requested: ImageRect,
    decodedWidth: Int,
    decodedHeight: Int,
    argb: IntArray,
    luminance: FloatArray = FloatArray(maxOf(0, decodedWidth * decodedHeight))
): ImageRegion? {
    if (decodedWidth <= 0 || decodedHeight <= 0) return null
    val count = decodedWidth * decodedHeight
    if (argb.size < count) return null
    if (luminance.size < count) return null
    argbToLuminance(argb, luminance, count)
    return ImageRegion(requested.left, requested.top, decodedWidth, decodedHeight, luminance)
}
