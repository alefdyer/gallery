package com.asinosoft.gallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.scale
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.logging.Logger
import javax.inject.Inject
import kotlin.system.measureTimeMillis

class PreviewFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val logger = Logger.getLogger("PreviewFactory")
    fun preview(image: Image): String {
        val preview: File = context.cacheDir.resolve(image.uuid)

        return if (preview.exists()) {
            preview.path
        } else {
            image.path.also {
                CoroutineScope(Dispatchers.IO).launch {
                    measureTimeMillis {
                        save(preview, create(image))
                    }.also {
                        logger.info("compressed: ${preview.path} -> ${preview.length()} at ${it}ms")
                    }
                }
            }
        }
    }

    fun create(image: Image): Bitmap {
        context.contentResolver.openInputStream(Uri.parse(image.path)).use { input ->
            val bitmap = BitmapFactory.decodeStream(input)

            return if (bitmap.width * bitmap.height > 128 * 128) {
                val scale = Math.min(128f / bitmap.width, 128f / bitmap.height)
                bitmap.scale(Math.round(bitmap.width * scale), Math.round(bitmap.height * scale))
            } else {
                bitmap
            }
        }
    }

    suspend fun save(file: File, preview: Bitmap) {
        withContext(Dispatchers.IO) {
            FileOutputStream(file).use { output ->
                preview.compress(Bitmap.CompressFormat.JPEG, 100, output)
            }
        }
    }
}
