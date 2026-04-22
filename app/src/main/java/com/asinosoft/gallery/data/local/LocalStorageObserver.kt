package com.asinosoft.gallery.data.local

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import com.asinosoft.gallery.GalleryApp
import com.asinosoft.gallery.data.MediaService
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class LocalStorageObserver(
    private var localStorage: LocalStorageProvider,
    private var service: MediaService
) : JobService() {

    private var job: Thread? = null

    companion object {
        private const val JOB_ID = 1
        private val MEDIA_URI = "content://${MediaStore.AUTHORITY}/".toUri()

        fun schedule(context: Context) {
            val isScheduled =
                context.getSystemService(JobScheduler::class.java)
                    .allPendingJobs.any { it.id == JOB_ID }

            if (isScheduled) {
                return
            }

            val componentName = ComponentName(context, LocalStorageObserver::class.java)
            context.getSystemService(JobScheduler::class.java).schedule(
                JobInfo.Builder(JOB_ID, componentName).apply {
                    addTriggerContentUri(
                        JobInfo.TriggerContentUri(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
                        )
                    )
                    addTriggerContentUri(JobInfo.TriggerContentUri(MEDIA_URI, 0))
                }.build()
            )
        }
    }

    override fun onStartJob(params: JobParameters): Boolean {
        job = thread { fetchAll(params) }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        job?.join()
        return true
    }

    private fun fetchAll(params: JobParameters) {
        runBlocking(Dispatchers.IO) {
            params.triggeredContentUris?.let {
                it.forEach { uri ->
                    try {
                        localStorage.fetchOne(uri)?.let { media ->
                            service.add(media)
                        }
                    } catch (ex: Throwable) {
                        Log.d(GalleryApp.Companion.TAG, "Exception: $ex")
                        // ignore
                    }
                }
            } ?: service.updateAll()

            jobFinished(params, false)
        }
    }
}
