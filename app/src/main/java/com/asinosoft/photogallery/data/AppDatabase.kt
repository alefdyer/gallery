package com.asinosoft.photogallery.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.asinosoft.photogallery.data.people.Face
import com.asinosoft.photogallery.data.people.Person
import com.asinosoft.photogallery.data.people.PersonDao
import com.asinosoft.photogallery.data.storage.Storage
import com.asinosoft.photogallery.data.storage.StorageRoomDao

@Database(
    entities = [
        Album::class,
        AlbumCategory::class,
        Media::class,
        MediaAlbum::class,
        Storage::class,
        Person::class,
        Face::class,
    ],
    version = 9,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao

    abstract fun imageDao(): MediaDao

    abstract fun storageDao(): StorageRoomDao

    abstract fun personDao(): PersonDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: buildDatabase(context).also { instance = it }
        }

        private fun buildDatabase(context: Context): AppDatabase = Room
            .databaseBuilder(context, AppDatabase::class.java, "media.db")
            .addMigrations(
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
            )
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

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS person (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        displayName TEXT,
                        relationship TEXT,
                        contactUri TEXT,
                        contactId INTEGER,
                        coverFaceId INTEGER,
                        albumId INTEGER,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_person_albumId ON person(albumId)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS face (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        mediaId INTEGER NOT NULL,
                        personId INTEGER,
                        left REAL NOT NULL,
                        top REAL NOT NULL,
                        right REAL NOT NULL,
                        bottom REAL NOT NULL,
                        embedding BLOB NOT NULL,
                        thumbPath TEXT,
                        FOREIGN KEY(mediaId) REFERENCES media(id) ON DELETE CASCADE,
                        FOREIGN KEY(personId) REFERENCES person(id) ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_face_mediaId ON face(mediaId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_face_personId ON face(personId)")
            }
        }
    }
}
