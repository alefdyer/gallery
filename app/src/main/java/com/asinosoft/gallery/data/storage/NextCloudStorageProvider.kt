package com.asinosoft.gallery.data.storage

import javax.inject.Inject

class NextCloudStorageProvider @Inject constructor() :
    NoopRemoteStorageProvider(type = StorageType.NEXTCLOUD)
