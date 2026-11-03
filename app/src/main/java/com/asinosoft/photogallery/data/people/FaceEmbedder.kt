package com.asinosoft.photogallery.data.people

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import org.tensorflow.lite.Interpreter

/**
 * MobileFaceNet (112×112 → 192-d) из assets. Детект лиц остаётся на ML Kit.
 *
 * Препроцесс как у исходной mobilefacenet.tflite: `(pixel - 128) / 128`.
 * Сравнение — euclidean по сырому вектору (см. [PersonClusterer]).
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

    fun encode(fullBitmap: Bitmap, box: Rect): FloatArray {
        val square = FaceEmbedding.expandSquare(box, fullBitmap.width, fullBitmap.height, 0.35f)
        val crop = cropSquare(fullBitmap, square) ?: return FloatArray(DIM)
        val input = Bitmap.createScaledBitmap(crop, INPUT_SIZE, INPUT_SIZE, true)
        if (crop !== input && !crop.isRecycled) crop.recycle()
        return try {
            embed(input)
        } finally {
            if (!input.isRecycled) input.recycle()
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
        return output[0]
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

    private fun cropSquare(bitmap: Bitmap, box: Rect): Bitmap? {
        val left = box.left.coerceIn(0, bitmap.width - 1)
        val top = box.top.coerceIn(0, bitmap.height - 1)
        val width = (box.right - left).coerceAtMost(bitmap.width - left).coerceAtLeast(1)
        val height = (box.bottom - top).coerceAtMost(bitmap.height - top).coerceAtLeast(1)
        return runCatching { Bitmap.createBitmap(bitmap, left, top, width, height) }.getOrNull()
    }

    companion object {
        private const val TAG = "FaceEmbedder"
        private const val MODEL_ASSET = "models/face_embedder.tflite"
        const val INPUT_SIZE = 112
        const val DIM = 192

        /** Бамп при смене preprocess/метрики — форсирует re-embed. */
        const val EMBEDDING_VERSION = 2
    }
}
