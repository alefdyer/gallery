package com.asinosoft.gallery.job

import android.app.job.JobInfo
import android.app.job.JobInfo.TriggerContentUri
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.asinosoft.gallery.data.ImageFetcher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import kotlin.concurrent.thread

@AndroidEntryPoint
class MediaObserver @Inject constructor(
    private val fetcher: ImageFetcher,
) : JobService() {
    private var job: Thread? = null
    private var isInterrupted = false

    companion object {
        private const val JOB_ID = 1
        private val MEDIA_URI = Uri.parse("content://${MediaStore.AUTHORITY}/")
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
            context.getSystemService(JobScheduler::class.java)
                .allPendingJobs
                .any { it.id == JOB_ID }
    }

    override fun onStartJob(params: JobParameters): Boolean {
        isInterrupted = false
        job = thread { fetchAll(params) }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        job?.join()
        return true
    }

    private fun fetchAll(params: JobParameters) = runBlocking(Dispatchers.IO) {
        params.triggeredContentUris?.forEach {
            fetcher.fetchOne(it.toString())
        }

        jobFinished(params, false)
    }
}
