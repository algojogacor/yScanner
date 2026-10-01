package com.yscanner.data.image

/**
 * Pure ARGB-to-grayscale conversion for yScanner.
 *
 * Kept free of any Android type so it can be exercised on the JVM in unit tests: a real [android.graphics.Bitmap]
 * is not available there, but the arithmetic that turns decoded pixels into luminance is the part most
 * likely to be subtly wrong, so it is isolated here and tested directly.
 *
 * ## Formula
 *
 * Rec. 601 luma is `0.299 R + 0.587 G + 0.114 B`, normalised by 255. This file uses the classic
 * **integer approximation** `(77*R + 150*G + 29*B) shr 8` (the coefficients sum to 256, so `shr 8`
 * is an exact `>> 8` divide). It is not the exact float expression: for a saturated primary channel
 * the result differs from the ideal coefficient by up to ~0.004. Callers must therefore treat the
 * output as approximate and assert a tolerance rather than bit-exact equality.
 *
 * ## Alpha
 *
 * Alpha is **ignored**. A region decoded from an opaque JPEG is opaque, and the refiner operates on
 * luminance alone where a transparent pixel has no defined value. No blending against a background
 * colour is performed, so `0x00FFFFFF` and `0xFFFFFFFF` both yield pure white.
 */

/**
 * Converts the first [count] packed ARGB pixels of [pixels] to normalised luminance in `0f..1f`,
 * writing them to [out].
 *
 * [count] exists so a caller can convert a prefix of a reusable scratch array without allocating a
 * copy. It is clamped to `min(pixels.size, out.size)`, so a caller that overstates it neither throws
 * nor reads or writes out of bounds. Entries of [out] beyond the clamped count are left untouched.
 */
fun argbToLuminance(pixels: IntArray, out: FloatArray, count: Int = pixels.size) {
    val n = count.coerceIn(0, minOf(pixels.size, out.size))
    for (i in 0 until n) {
        out[i] = argbToLuminance(pixels[i])
    }
}

/**
 * Single-pixel convenience using the same integer approximation as the batch overload.
 *
 * @return luminance in `0f..1f`, clamped defensively.
 */
fun argbToLuminance(pixel: Int): Float {
    val r = pixel ushr 16 and 0xFF
    val g = pixel ushr 8 and 0xFF
    val b = pixel and 0xFF
    // Alpha is deliberately not read: see the file KDoc.
    val luma = (77 * r + 150 * g + 29 * b) shr 8
    return (luma / 255f).coerceIn(0f, 1f)
}
