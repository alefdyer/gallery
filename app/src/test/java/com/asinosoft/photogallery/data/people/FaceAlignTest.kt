package com.asinosoft.photogallery.data.people

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FaceAlignTest {
    @Test
    fun knownEyesLandOnTemplate() {
        val pose = FaceAlign.Similarity(
            a = 1.6f * kotlin.math.cos(0.4f),
            b = 1.6f * kotlin.math.sin(0.4f),
            tx = 30f,
            ty = -12f,
        )
        fun photo(point: FaceAlign.Point) = pose.apply(point.x, point.y)
        val landmarks = FaceAlign.Landmarks(
            rightEye = photo(FaceAlign.templateRightEye),
            leftEye = photo(FaceAlign.templateLeftEye),
            nose = photo(FaceAlign.templateNose),
            mouthRight = photo(FaceAlign.templateMouthRight),
            mouthLeft = photo(FaceAlign.templateMouthLeft),
        )

        val solved = FaceAlign.similarityForFace(landmarks)
        assertNotNull(solved)
        val right = solved!!.apply(landmarks.rightEye!!.x, landmarks.rightEye.y)
        val left = solved.apply(landmarks.leftEye!!.x, landmarks.leftEye.y)
        assertEquals(FaceAlign.templateRightEye.x, right.x, 0.05f)
        assertEquals(FaceAlign.templateRightEye.y, right.y, 0.05f)
        assertEquals(FaceAlign.templateLeftEye.x, left.x, 0.05f)
        assertEquals(FaceAlign.templateLeftEye.y, left.y, 0.05f)
    }

    @Test
    fun coincidentEyesAreRejected() {
        val same = FaceAlign.Point(10f, 10f)
        val solved = FaceAlign.similarityForFace(
            FaceAlign.Landmarks(rightEye = same, leftEye = same),
        )
        assertNull(solved)
    }
}
