package com.yscanner.detection

import androidx.camera.core.ImageProxy
import com.yscanner.domain.model.Quad

/**
 * One document-like quadrilateral proposed by the detector, in **frame space**
 * (pixel coordinates of the analysis frame it was found in).
 *
 * @param id stable-within-a-frame identifier, unique among the candidates of one detection pass.
 * @param confidence model-derived trust in this candidate, in `[0,1]`.
 * @param area quad area in square pixels, precomputed because every consumer ranks by it.
 */
data class DocumentCandidate(
    val id: String,
    val quad: Quad,
    val confidence: Float,
    val area: Float
)

/**
 * Produces the candidate documents visible in a single frame.
 *
 * Implementations own the whole detection pipeline for one frame and must close
 * the [ImageProxy] before returning — the analyzer is configured with
 * `STRATEGY_KEEP_ONLY_LATEST`, so holding a proxy open stalls the camera.
 *
 * This interface deliberately returns *all* plausible candidates rather than a
 * single winner: choosing which candidate the user means is a separate
 * responsibility (`com.yscanner.detection.select.TargetSelector`), and keeping
 * the two apart is what lets target selection be tested without a model.
 *
 * A later milestone replaces this with the concrete segmentation-backed
 * implementation once the S01 model decision is made.
 */
interface DocumentDetector {
    suspend fun detect(imageProxy: ImageProxy): List<DocumentCandidate>
}
