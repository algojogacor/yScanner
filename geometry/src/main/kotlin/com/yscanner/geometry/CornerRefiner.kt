package com.yscanner.geometry

import com.yscanner.domain.image.ImageSource
import com.yscanner.domain.model.Quad

/**
 * Tightens an approximate document quad using the **full-resolution** capture.
 *
 * Detection runs on a downscaled analysis frame, so its corners are only accurate to a few pixels
 * *of that frame* — which is tens of pixels once scaled up to a 12 MP capture. Refinement closes
 * that gap by looking at the real pixels around each corner. It refines an existing estimate; it
 * does not re-detect the document.
 *
 * ### Coordinate contract
 *
 * Both [refine]'s input and its return value are in **source-image (capture) pixel** coordinates.
 * The caller is responsible for mapping the analysis-space quad into capture space first; passing an
 * analysis-space quad here would refine the wrong part of the image and the error would be silent.
 *
 * ### Failure contract
 *
 * Refinement is best-effort and must never make a capture worse:
 *
 * * It never throws. A decoder failure, a weak or missing edge, or a degenerate fit returns the
 *   input unchanged.
 * * It never returns a non-convex quad. A refinement that would fold the shape returns the input.
 * * It never moves a corner further than the implementation's configured bound, so a bad fit cannot
 *   drag a corner across the page.
 *
 * Callers can therefore apply the result unconditionally. Losing a refinement costs a slightly
 * looser crop; corrupting the quad costs the user's scan.
 */
interface CornerRefiner {
    /**
     * @param quad the approximate document quad, in source-image pixel coordinates.
     * @param source full-resolution image access. Not closed by this call — the caller owns it.
     * @return the refined quad in the same coordinate space, or [quad] unchanged when refinement
     *   could not be performed confidently.
     */
    fun refine(quad: Quad, source: ImageSource): Quad
}
