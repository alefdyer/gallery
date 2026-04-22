package com.asinosoft.gallery.data.storage

import javax.inject.Inject

class YandexStorageProvider @Inject constructor() :
    NoopRemoteStorageProvider(type = StorageType.YANDEX)
