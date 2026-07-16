package com.asinosoft.gallery.data

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ApplicationRepository @Inject constructor(@param:ApplicationContext private val context: Context) :
    ApplicationDao {
    override suspend fun getApplications(packages: Set<String>): List<Application> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val activities = context.packageManager.queryIntentActivities(intent, 0)
        activities.sortWith(ResolveInfo.DisplayNameComparator(context.packageManager))

        activities
            .filter { packages.contains(it.activityInfo.packageName) }
            .map {
                Application(
                    it.activityInfo.loadLabel(context.packageManager).toString(),
                    it.activityInfo.packageName,
                    it.activityInfo.loadIcon(context.packageManager)
                )
            }.sortedBy { it.pkg }
    }
}
