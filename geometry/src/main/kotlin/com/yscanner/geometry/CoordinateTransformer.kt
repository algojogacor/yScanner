package com.yscanner.geometry

import com.yscanner.domain.model.Corner
import com.yscanner.domain.model.PointF
import com.yscanner.domain.model.Quad

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
