package com.asinosoft.gallery.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.asinosoft.gallery.data.storage.Storage
import com.asinosoft.gallery.data.storage.StorageDao

@Database(
    entities = [Album::class, Media::class, MediaAlbum::class, Storage::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao

    abstract fun imageDao(): MediaDao

    abstract fun storageDao(): StorageDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: buildDatabase(context).also { instance = it }
        }

        private fun buildDatabase(context: Context): AppDatabase = Room
            .databaseBuilder(context, AppDatabase::class.java, "media.db")
            .addCallback(AppDatabaseInitializer())
            .build()
    }
}
