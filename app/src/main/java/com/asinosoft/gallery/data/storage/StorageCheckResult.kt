package com.asinosoft.gallery.data.storage

sealed class StorageCheckResult {
    object Success : StorageCheckResult()
    object ServerNotFound : StorageCheckResult()
    object AuthorizationFailed : StorageCheckResult()
    data class UnknownError(val message: String?) : StorageCheckResult()
}
