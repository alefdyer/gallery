package com.asinosoft.gallery.data

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppAnalytics @Inject constructor(private val firebase: FirebaseAnalytics) {
    fun scanCompleted() = log(SCAN_COMPLETED)

    fun filterApps() = log(FILTER_APPS)

    fun filterDate() = log(FILTER_DATE)

    fun filterCounters() = log(FILTER_COUNTERS)

    fun filterDateSlider() = log(FILTER_DATE_SLIDER)

    fun categoryCreated() = log(CATEGORY_CREATED)

    fun albumCreated() = log(ALBUM_CREATED)

    fun categoryRenamed() = log(CATEGORY_RENAMED)

    fun categoryMoved() = log(CATEGORY_MOVED)

    fun albumRenamed() = log(ALBUM_RENAMED)

    fun albumMoved() = log(ALBUM_MOVED)

    fun albumMediaAdded() = log(ALBUM_MEDIA_ADDED)

    fun favoriteAdded() = log(FAVORITE_ADDED)

    fun storageAdded(type: String) = log(STORAGE_ADDED, PARAM_STORAGE_TYPE to type)

    fun recommendFriends() = log(RECOMMEND_FRIENDS)

    fun mediaDeleted(source: String) = log(MEDIA_DELETED, PARAM_SOURCE to source)

    fun googleSignedIn() = log(GOOGLE_SIGNED_IN)

    fun backupSaved() = log(BACKUP_SAVED)

    fun backupRestored() = log(BACKUP_RESTORED)

    private fun log(name: String, vararg params: Pair<String, String>) {
        val bundle = if (params.isEmpty()) {
            null
        } else {
            Bundle(params.size).apply {
                params.forEach { (key, value) -> putString(key, value) }
            }
        }
        firebase.logEvent(name, bundle)
    }

    companion object {
        const val SOURCE_LIBRARY = "library"
        const val SOURCE_TRASH = "trash"

        private const val SCAN_COMPLETED = "scan_completed"
        private const val FILTER_APPS = "filter_apps"
        private const val FILTER_DATE = "filter_date"
        private const val FILTER_COUNTERS = "filter_counters"
        private const val FILTER_DATE_SLIDER = "filter_date_slider"
        private const val CATEGORY_CREATED = "category_created"
        private const val ALBUM_CREATED = "album_created"
        private const val CATEGORY_RENAMED = "category_renamed"
        private const val CATEGORY_MOVED = "category_moved"
        private const val ALBUM_RENAMED = "album_renamed"
        private const val ALBUM_MOVED = "album_moved"
        private const val ALBUM_MEDIA_ADDED = "album_media_added"
        private const val FAVORITE_ADDED = "favorite_added"
        private const val STORAGE_ADDED = "storage_added"
        private const val RECOMMEND_FRIENDS = "recommend_friends"
        private const val MEDIA_DELETED = "media_deleted"
        private const val GOOGLE_SIGNED_IN = "google_signed_in"
        private const val BACKUP_SAVED = "backup_saved"
        private const val BACKUP_RESTORED = "backup_restored"
        private const val PARAM_STORAGE_TYPE = "storage_type"
        private const val PARAM_SOURCE = "source"
    }
}
