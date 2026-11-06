package com.asinosoft.photogallery.data.people

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import org.tensorflow.lite.Interpreter
import androidx.core.graphics.createBitmap

/**
 * MobileFaceNet (112×112 → 192-d) из assets. Детект лиц остаётся на ML Kit.
 *
 * Препроцесс как у исходной mobilefacenet.tflite: `(pixel - 128) / 128`.
 * Кроп выравнивается по глазам, если они есть. Вектор на выходе L2-нормирован.
 */
@Singleton
class FaceEmbedder @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val lock = Any()

    private val interpreter: Interpreter by lazy {
        val model = context.assets.open(MODEL_ASSET).use { it.readBytes() }
        val buffer = ByteBuffer.allocateDirect(model.size).order(ByteOrder.nativeOrder())
        buffer.put(model).rewind()
        Interpreter(buffer, Interpreter.Options().apply { setNumThreads(2) }).also {
            val inT = it.getInputTensor(0)
            val outT = it.getOutputTensor(0)
            Log.i(
                TAG,
                "loaded $MODEL_ASSET in=${inT.shape().contentToString()} ${inT.dataType()} " +
                        "out=${outT.shape().contentToString()} ${outT.dataType()}",
            )
        }
    }

    fun encode(fullBitmap: Bitmap, landmarks: FaceAlign.Landmarks): FloatArray? {
        val face = warpAligned(fullBitmap, landmarks) ?: return null
        return try {
            embed(face)
        } finally {
            if (!face.isRecycled) face.recycle()
        }
    }

    private fun warpAligned(bitmap: Bitmap, landmarks: FaceAlign.Landmarks): Bitmap? {
        val sim = FaceAlign.similarityForFace(landmarks) ?: return null
        val out = createBitmap(INPUT_SIZE, INPUT_SIZE)
        return runCatching {
            val canvas = Canvas(out)
            val matrix = Matrix()
            matrix.setValues(
                floatArrayOf(
                    sim.a, -sim.b, sim.tx,
                    sim.b, sim.a, sim.ty,
                    0f, 0f, 1f,
                ),
            )
            canvas.drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
            out
        }.getOrElse {
            if (!out.isRecycled) out.recycle()
            null
        }
    }

    private fun embed(face112: Bitmap): FloatArray {
        val input = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
            .order(ByteOrder.nativeOrder())
        fillNormalizedRgb(face112, input)
        val output = Array(1) { FloatArray(DIM) }
        synchronized(lock) {
            interpreter.run(input, output)
        }
        val vector = output[0]
        FaceEmbedding.normalizeInPlace(vector)
        return vector
    }

    private fun fillNormalizedRgb(bitmap: Bitmap, buffer: ByteBuffer) {
        buffer.rewind()
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (pixel in pixels) {
            // Как в FaceRecognitionAuth / MobileFaceNet: (channel - 128) / 128
            buffer.putFloat(((pixel shr 16) and 0xFF) / 128f - 1f)
            buffer.putFloat(((pixel shr 8) and 0xFF) / 128f - 1f)
            buffer.putFloat((pixel and 0xFF) / 128f - 1f)
        }
        buffer.rewind()
    }

    companion object {
        private const val TAG = "FaceEmbedder"
        private const val MODEL_ASSET = "models/face_embedder.tflite"
        const val INPUT_SIZE = 112
        const val DIM = 192

        /** Бамп при смене preprocess/метрики — startBackfill пересчитывает эмбеддинги. */
        const val EMBEDDING_VERSION = 3
    }
}
