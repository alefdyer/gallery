package com.asinosoft.gallery.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.asinosoft.gallery.data.storage.Storage
import com.asinosoft.gallery.data.storage.StorageDao

@Database(
    entities = [
        Album::class,
        AlbumCategory::class,
        Media::class,
        MediaAlbum::class,
        Storage::class],
    version = 7,
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
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
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
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `media_album_backup` (
                    `mediaId` INTEGER NOT NULL,
                    `albumId` INTEGER NOT NULL,
                    PRIMARY KEY(`mediaId`, `albumId`))
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `media_album_backup` (`mediaId`, `albumId`)
                    SELECT `mediaId`, `albumId` FROM `media_album`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `media_album`")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `album_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `categoryId` INTEGER NOT NULL,
                    `count` INTEGER NOT NULL,
                    `size` INTEGER NOT NULL,
                    `date` INTEGER NOT NULL,
                    `userCreated` INTEGER NOT NULL,
                    FOREIGN KEY(`categoryId`) REFERENCES `album_category`(`id`)
                    ON UPDATE NO ACTION ON DELETE SET NULL)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `album_new` (
                    `id`, `name`, `categoryId`, `count`, `size`, `date`, `userCreated`)
                    SELECT `id`, `name`, `categoryId`, `count`, `size`, `date`, `userCreated`
                    FROM `album`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `album`")
                db.execSQL("ALTER TABLE `album_new` RENAME TO `album`")
                db.execSQL(
                    "UPDATE `sqlite_sequence` SET `name` = 'album' WHERE `name` = 'album_new'"
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_album_categoryId_name`
                    ON `album` (`categoryId`, `name`)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_album_categoryId`
                    ON `album` (`categoryId`)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `media_album` (
                    `mediaId` INTEGER NOT NULL,
                    `albumId` INTEGER NOT NULL,
                    PRIMARY KEY(`mediaId`, `albumId`),
                    FOREIGN KEY(`mediaId`) REFERENCES `media`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`albumId`) REFERENCES `album`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `media_album` (`mediaId`, `albumId`)
                    SELECT `mediaId`, `albumId` FROM `media_album_backup`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `media_album_backup`")
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_media_album_mediaId`
                    ON `media_album` (`mediaId`)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_media_album_albumId`
                    ON `media_album` (`albumId`)
                    """.trimIndent()
                )
            }
        }
    }
}
