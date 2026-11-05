package com.asinosoft.photogallery.data.storage

import com.asinosoft.photogallery.data.Media

/** Изменение при инкрементальном sync. */
sealed class StorageChange {
    data class Upsert(val media: Media) : StorageChange()
    data class Delete(val storageItemId: String) : StorageChange()

    /** Dropbox deleted-entry часто без id — удаляем по path+filename. */
    data class DeleteByPath(val path: String, val filename: String) : StorageChange()
}

/** Курсор sync протух — нужен полный проход. */
class SyncCursorExpiredException(message: String? = null) : Exception(message)
