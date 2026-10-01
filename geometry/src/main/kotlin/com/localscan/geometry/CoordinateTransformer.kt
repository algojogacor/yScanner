package com.localscan.geometry

import com.localscan.domain.model.Corner
import com.localscan.domain.model.PointF
import com.localscan.domain.model.Quad

enum class CoordinateSpace {
    SENSOR,
    IMAGE_ANALYSIS,
    PREVIEW_VIEW,
    NORMALIZED,
    IMAGE_CAPTURE,
    PROCESSED,
    PDF
}

interface CoordinateTransformer {
    fun mapPoint(point: PointF, from: CoordinateSpace, to: CoordinateSpace): PointF
    fun mapQuad(quad: Quad, from: CoordinateSpace, to: CoordinateSpace): Quad
}
