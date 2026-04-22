package com.asinosoft.gallery.data.storage

import javax.inject.Inject

class DropboxStorageProvider @Inject constructor() :
    NoopRemoteStorageProvider(type = StorageType.DROPBOX)
