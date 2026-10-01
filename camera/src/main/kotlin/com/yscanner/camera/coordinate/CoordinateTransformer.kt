package com.yscanner.camera.coordinate

import com.yscanner.common.math.Matrix3x3
import com.yscanner.domain.model.PointF
import com.yscanner.domain.model.Quad

/**
 * Projective matrix coordinate transformation engine connecting all 7 coordinate spaces.
 */
interface CoordinateTransformer {
    /**
     * Maps a 2D [point] from [from] space to [to] space using projective matrix transformation.
     */
    fun mapPoint(point: PointF, from: CoordinateSpace, to: CoordinateSpace): PointF

    /**
     * Maps an ordered document [quad] from [from] space to [to] space.
     */
    fun mapQuad(quad: Quad, from: CoordinateSpace, to: CoordinateSpace): Quad

    /**
     * Returns the 3x3 projective transformation matrix that maps points from [from] to [to].
     */
    fun getTransformMatrix(from: CoordinateSpace, to: CoordinateSpace): Matrix3x3
}
