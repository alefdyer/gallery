package com.asinosoft.photogallery.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.asinosoft.photogallery.data.storage.Storage
import com.asinosoft.photogallery.data.storage.StorageDao

@Database(
    entities = [
        Album::class,
        AlbumCategory::class,
        Media::class,
        MediaAlbum::class,
        Storage::class],
    version = 8,
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
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
            .fallbackToDestructiveMigration(true)
            .addCallback(AppDatabaseInitializer())
            .build()

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE album ADD COLUMN userCreated INTEGER NOT NULL DEFAULT 0"
                )
                // Альбомы в пользовательских категориях (не :other / :messengers)
                db.execSQL(
                    """
                    UPDATE album SET userCreated = 1
                    WHERE categoryId IN (
                        SELECT id FROM album_category WHERE name NOT LIKE ':%'
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media ADD COLUMN takenAt INTEGER")
                db.execSQL("ALTER TABLE media ADD COLUMN createdAt INTEGER")
                db.execSQL("ALTER TABLE media ADD COLUMN modifiedAt INTEGER")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE album ADD COLUMN photoCount INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE album ADD COLUMN videoCount INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    """
                    UPDATE album SET
                        photoCount = (
                            SELECT COUNT(*) FROM media_album ma
                            INNER JOIN media m ON m.id = ma.mediaId
                            WHERE ma.albumId = album.id AND m.video IS NULL
                        ),
                        videoCount = (
                            SELECT COUNT(*) FROM media_album ma
                            INNER JOIN media m ON m.id = ma.mediaId
                            WHERE ma.albumId = album.id AND m.video IS NOT NULL
                        )
                    """.trimIndent()
                )
            }
        }
    }
}
