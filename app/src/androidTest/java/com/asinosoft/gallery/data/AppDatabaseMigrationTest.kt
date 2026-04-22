package com.asinosoft.gallery.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory()
        )

    @Test
    fun migrate2To3() {
        val dbName = "migration-test"
        helper.createDatabase(dbName, 2).apply {
            execSQL(
                """
                INSERT INTO media(id, uri, date, time, bucket, size, filename, mimeType, image, video)
                VALUES(1, 'content://media/external/images/media/1', 0, 0, NULL, 100, 'a.jpg', 'image/jpeg', '{"width":1,"height":1,"orientation":0}', NULL)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(dbName, 3, true, AppDatabase.MIGRATION_2_3)
    }
}
