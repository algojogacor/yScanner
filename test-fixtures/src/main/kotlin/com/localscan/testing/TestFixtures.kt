package com.localscan.testing

import com.localscan.domain.model.Corner
import com.localscan.domain.model.Quad

object TestFixtures {
    fun createSampleQuad(
        width: Float = 100f,
        height: Float = 200f
    ): Quad = Quad(
        topLeft = Corner(0f, 0f),
        topRight = Corner(width, 0f),
        bottomRight = Corner(width, height),
        bottomLeft = Corner(0f, height)
    )
}
