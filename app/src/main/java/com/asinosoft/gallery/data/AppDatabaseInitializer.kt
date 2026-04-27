package com.asinosoft.gallery.data

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

class AppDatabaseInitializer : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)

        db.execSQL("INSERT INTO storage (id, type) VALUES (1, 'LOCAL')")
    }
}
