package com.asinosoft.gallery.data

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ApplicationRepository @Inject constructor(@param:ApplicationContext private val context: Context) :
    ApplicationDao {
    override suspend fun getApplications(packages: Set<String>): List<Application> = withContext(Dispatchers.IO) {
        if (packages.isEmpty()) return@withContext emptyList()

        val pm = context.packageManager
        packages.mapNotNull { pkg ->
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val name = pm.getApplicationLabel(appInfo).toString()
                val icon = pm.getApplicationIcon(appInfo)
                Application(name, pkg, icon)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }.sortedBy { it.name }
    }
}
