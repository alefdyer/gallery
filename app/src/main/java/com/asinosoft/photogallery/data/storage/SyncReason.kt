package com.asinosoft.photogallery.data.storage

/** Почему запросили скан хранилища. */
enum class SyncReason {
    /** Старт приложения — только [Storage.allowsAutoSync]. */
    AppOpen,

    /** Раскрыли категорию remote-хранилища. */
    EnterCategory,

    /** Кнопка «Обновить», addStorage, reauth. */
    Manual,
}
