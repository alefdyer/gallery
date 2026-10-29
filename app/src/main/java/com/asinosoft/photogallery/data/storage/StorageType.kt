package com.asinosoft.photogallery.data.storage

enum class StorageType {
    LOCAL {
        override val isEditable = false
        override val isDeletable = false
    },
    DROPBOX {
        override val isEditable = false
        override val isDeletable = true
    },
    WEBDAV {
        override val isEditable = true
        override val isDeletable = true
    },
    NEXTCLOUD {
        override val isEditable = true
        override val isDeletable = true
    },
    YANDEX {
        override val isEditable = false
        override val isDeletable = true
    },
    GOOGLE_DRIVE {
        override val isEditable = false
        override val isDeletable = true
    },
    ONEDRIVE {
        override val isEditable = false
        override val isDeletable = true
    };

    abstract val isEditable: Boolean
    abstract val isDeletable: Boolean
}
