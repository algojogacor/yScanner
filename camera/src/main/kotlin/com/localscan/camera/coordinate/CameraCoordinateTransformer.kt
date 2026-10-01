package com.localscan.camera.coordinate

import com.localscan.common.math.Matrix3x3
import com.localscan.domain.model.Corner
import com.localscan.domain.model.PointF
import com.localscan.domain.model.Quad

/**
 * Robust 7-space matrix coordinate transformation engine.
 *
 * Implements Hub-and-Spoke topology with NORMALIZED [0.0, 1.0]^2 space at center:
 * Any conversion between Space A and Space B is computed as:
 * T(A -> B) = T(NORM -> B) * T(A -> NORM)
 */
class CameraCoordinateTransformer(
    val config: CameraCoordinateConfig
) : CoordinateTransformer {

    private val toNormMatrices = arrayOfNulls<Matrix3x3>(CoordinateSpace.entries.size)
    private val fromNormMatrices = arrayOfNulls<Matrix3x3>(CoordinateSpace.entries.size)

    init {
        buildMatrices()
    }

    private fun buildMatrices() {
        // 1. SENSOR
        val sNorm = Matrix3x3.scale(1f / config.sensorWidth, 1f / config.sensorHeight)
        val sRot = Matrix3x3.rotationUnitSquare(config.sensorRotationDegrees)
        val sensorToNorm = sRot * sNorm
        registerSpace(CoordinateSpace.SENSOR, sensorToNorm)

        // 2. IMAGE_CAPTURE (Authoritative Scene)
        val cNorm = Matrix3x3.scale(1f / config.captureWidth, 1f / config.captureHeight)
        val cRot = Matrix3x3.rotationUnitSquare(config.captureRotationDegrees)
        val captureToNorm = cRot * cNorm
        registerSpace(CoordinateSpace.IMAGE_CAPTURE, captureToNorm)

        // Upright scene dimensions from capture
        val uprightSceneWidth = if (config.captureRotationDegrees % 180 == 0) {
            config.captureWidth.toFloat()
        } else {
            config.captureHeight.toFloat()
        }
        val uprightSceneHeight = if (config.captureRotationDegrees % 180 == 0) {
            config.captureHeight.toFloat()
        } else {
            config.captureWidth.toFloat()
        }
        val arScene = uprightSceneWidth / uprightSceneHeight

        // 3. IMAGE_ANALYSIS
        val cropW = (config.analysisCropRight - config.analysisCropLeft).toFloat().coerceAtLeast(1f)
        val cropH = (config.analysisCropBottom - config.analysisCropTop).toFloat().coerceAtLeast(1f)
        val aTrans = Matrix3x3.translation(-config.analysisCropLeft.toFloat(), -config.analysisCropTop.toFloat())
        val aScale = Matrix3x3.scale(1f / cropW, 1f / cropH)
        val aNorm = aScale * aTrans
        val aRot = Matrix3x3.rotationUnitSquare(config.analysisRotationDegrees)

        val uprightAnalysisW = if (config.analysisRotationDegrees % 180 == 0) cropW else cropH
        val uprightAnalysisH = if (config.analysisRotationDegrees % 180 == 0) cropH else cropW
        val arAnalysis = uprightAnalysisW / uprightAnalysisH

        val aFov = computeFovAlignment(arAnalysis, arScene)
        val analysisToNorm = aFov * (aRot * aNorm)
        registerSpace(CoordinateSpace.IMAGE_ANALYSIS, analysisToNorm)

        // 4. PREVIEW_VIEW
        val previewScale = when (config.previewScaleType) {
            PreviewScaleType.FILL_CENTER -> maxOf(
                config.previewWidth.toFloat() / uprightSceneWidth,
                config.previewHeight.toFloat() / uprightSceneHeight
            )
            PreviewScaleType.FIT_CENTER -> minOf(
                config.previewWidth.toFloat() / uprightSceneWidth,
                config.previewHeight.toFloat() / uprightSceneHeight
            )
        }
        val scaledW = uprightSceneWidth * previewScale
        val scaledH = uprightSceneHeight * previewScale
        val tx = (config.previewWidth - scaledW) / 2f
        val ty = (config.previewHeight - scaledH) / 2f
        val normToPreview = Matrix3x3(floatArrayOf(
            scaledW, 0f, tx,
            0f, scaledH, ty,
            0f, 0f, 1f
        ))
        val previewToNorm = normToPreview.invert() ?: Matrix3x3.IDENTITY
        toNormMatrices[CoordinateSpace.PREVIEW_VIEW.ordinal] = previewToNorm
        fromNormMatrices[CoordinateSpace.PREVIEW_VIEW.ordinal] = normToPreview

        // 5. NORMALIZED
        toNormMatrices[CoordinateSpace.NORMALIZED.ordinal] = Matrix3x3.IDENTITY
        fromNormMatrices[CoordinateSpace.NORMALIZED.ordinal] = Matrix3x3.IDENTITY

        // 6. PROCESSED & 7. PDF
        val quad = config.processedQuad
        if (quad != null) {
            val hCapToProc = Matrix3x3.homographyFromQuadToRect(
                quad.topLeft.x, quad.topLeft.y,
                quad.topRight.x, quad.topRight.y,
                quad.bottomRight.x, quad.bottomRight.y,
                quad.bottomLeft.x, quad.bottomLeft.y,
                config.processedWidth.toFloat(), config.processedHeight.toFloat()
            )
            val hProcToCap = hCapToProc.invert() ?: Matrix3x3.IDENTITY
            val procToNorm = captureToNorm * hProcToCap
            registerSpace(CoordinateSpace.PROCESSED, procToNorm)

            // PDF Page Space
            val boxW = config.pdfPageWidthPt - config.pdfMarginLeftPt - config.pdfMarginRightPt
            val boxH = config.pdfPageHeightPt - config.pdfMarginTopPt - config.pdfMarginBottomPt
            val pdfScale = minOf(boxW / config.processedWidth, boxH / config.processedHeight)
            val pdfTx = config.pdfMarginLeftPt + (boxW - config.processedWidth * pdfScale) / 2f
            val pdfTy = config.pdfMarginTopPt + (boxH - config.processedHeight * pdfScale) / 2f
            val procToPdf = Matrix3x3(floatArrayOf(
                pdfScale, 0f, pdfTx,
                0f, pdfScale, pdfTy,
                0f, 0f, 1f
            ))
            val pdfToProc = procToPdf.invert() ?: Matrix3x3.IDENTITY
            val pdfToNorm = procToNorm * pdfToProc
            registerSpace(CoordinateSpace.PDF, pdfToNorm)
        }
    }

    private fun registerSpace(space: CoordinateSpace, toNorm: Matrix3x3) {
        toNormMatrices[space.ordinal] = toNorm
        fromNormMatrices[space.ordinal] = toNorm.invert() ?: Matrix3x3.IDENTITY
    }

    private fun computeFovAlignment(arContent: Float, arTarget: Float): Matrix3x3 {
        return if (arContent > arTarget) {
            // Content is wider than target: center crop vertically
            val sy = arTarget / arContent
            val ty = (1f - sy) / 2f
            Matrix3x3.translation(0f, ty) * Matrix3x3.scale(1f, sy)
        } else if (arContent < arTarget) {
            // Content is narrower than target: center crop horizontally
            val sx = arContent / arTarget
            val tx = (1f - sx) / 2f
            Matrix3x3.translation(tx, 0f) * Matrix3x3.scale(sx, 1f)
        } else {
            Matrix3x3.IDENTITY
        }
    }

    override fun getTransformMatrix(from: CoordinateSpace, to: CoordinateSpace): Matrix3x3 {
        if (from == to) return Matrix3x3.IDENTITY
        val toNorm = toNormMatrices[from.ordinal]
            ?: error("Transform from space $from is not configured (check if processedQuad is set for PROCESSED/PDF)")
        val fromNorm = fromNormMatrices[to.ordinal]
            ?: error("Transform to space $to is not configured (check if processedQuad is set for PROCESSED/PDF)")
        return fromNorm * toNorm
    }

    override fun mapPoint(point: PointF, from: CoordinateSpace, to: CoordinateSpace): PointF {
        if (from == to) return point
        val m = getTransformMatrix(from, to)
        val (x, y) = m.mapPoint(point.x, point.y)
        return PointF(x, y)
    }

    override fun mapQuad(quad: Quad, from: CoordinateSpace, to: CoordinateSpace): Quad {
        if (from == to) return quad
        val m = getTransformMatrix(from, to)
        val (tlX, tlY) = m.mapPoint(quad.topLeft.x, quad.topLeft.y)
        val (trX, trY) = m.mapPoint(quad.topRight.x, quad.topRight.y)
        val (brX, brY) = m.mapPoint(quad.bottomRight.x, quad.bottomRight.y)
        val (blX, blY) = m.mapPoint(quad.bottomLeft.x, quad.bottomLeft.y)
        return Quad(
            topLeft = Corner(tlX, tlY, quad.topLeft.confidence),
            topRight = Corner(trX, trY, quad.topRight.confidence),
            bottomRight = Corner(brX, brY, quad.bottomRight.confidence),
            bottomLeft = Corner(blX, blY, quad.bottomLeft.confidence)
        )
    }
}
