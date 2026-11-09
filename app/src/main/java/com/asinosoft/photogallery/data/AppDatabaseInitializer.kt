package com.asinosoft.photogallery.data

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

class AppDatabaseInitializer : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)

        db.execSQL("INSERT INTO storage (id, type, lastFetchedAt) VALUES (1, 'LOCAL', 0)")

        db.execSQL("INSERT INTO album_category (id, name) VALUES (1, ':other')")
    }
}
