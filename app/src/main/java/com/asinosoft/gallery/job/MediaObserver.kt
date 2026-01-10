package com.asinosoft.gallery.job

import android.app.job.JobInfo
import android.app.job.JobInfo.TriggerContentUri
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import com.asinosoft.gallery.GalleryApp
import com.asinosoft.gallery.data.ImageFetcher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import kotlin.concurrent.thread

@AndroidEntryPoint
class MediaObserver : JobService() {
    @Inject
    lateinit var fetcher: ImageFetcher

    private var job: Thread? = null

    private var isInterrupted = false

    companion object {
        private const val JOB_ID = 1
        private val MEDIA_URI = "content://${MediaStore.AUTHORITY}/".toUri()
        fun schedule(context: Context) {
            val componentName = ComponentName(context, MediaObserver::class.java)
            context.getSystemService(JobScheduler::class.java).schedule(
                JobInfo.Builder(JOB_ID, componentName).apply {
                    addTriggerContentUri(
                        TriggerContentUri(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
                        )
                    )
                    addTriggerContentUri(TriggerContentUri(MEDIA_URI, 0))
                }.build()
            )
        }

        fun isScheduled(context: Context): Boolean =
            context.getSystemService(JobScheduler::class.java).allPendingJobs.any { it.id == JOB_ID }
    }

    override fun onStartJob(params: JobParameters): Boolean {
        Log.d(GalleryApp.TAG, "onStartJob")

        isInterrupted = false
        job = thread { fetchAll(params) }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        Log.d(GalleryApp.TAG, "onStopJob")

        job?.join()
        return true
    }

    private fun fetchAll(params: JobParameters) = thread {
        runBlocking(Dispatchers.IO) {
            params.triggeredContentUris?.let {
                it.forEach { uri ->
                    try {
                        fetcher.fetchOne(uri.toString())
                    } catch (ex: Throwable) {
                        Log.d(GalleryApp.TAG, "Exception: $ex")
                        // ignore
                    }
                }
            } ?: fetcher.fetchAll()

            jobFinished(params, false)
        }
    }
}
