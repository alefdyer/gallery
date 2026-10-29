package com.asinosoft.photogallery.data.storage.gdrive

import android.app.PendingIntent

/** Google Identity требует UI — Activity должна запустить PendingIntent. */
data class GoogleDriveReauthRequest(
    val storageId: Long,
    val pendingIntent: PendingIntent,
)
