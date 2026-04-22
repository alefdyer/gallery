package com.asinosoft.gallery.data.storage

import javax.inject.Inject

class WebDavStorageProvider @Inject constructor() :
    NoopRemoteStorageProvider(type = StorageType.WEBDAV)
