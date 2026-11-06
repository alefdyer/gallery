package com.asinosoft.photogallery.data.people

import org.junit.Assert.assertEquals
import org.junit.Test

class FaceEmbeddingTest {
    @Test
    fun coreAverageStaysWithTheDenseGroup() {
        val near = FloatArray(2) { if (it == 0) 1f else 0f }
        val far = FloatArray(2) { if (it == 1) 1f else 0f }
        val vectors = List(7) { near.clone() } + List(3) { far.clone() }
        val reference = floatArrayOf(0.7f, 0.3f)

        val core = FaceEmbedding.coreAverage(vectors, reference)

        assertEquals(1f, core[0], 0.05f)
        assertEquals(0f, core[1], 0.05f)
    }
}
