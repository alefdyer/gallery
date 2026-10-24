package com.asinosoft.photogallery.data.backup

import android.app.Activity
import androidx.activity.result.ActivityResult
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsBackupRepository @Inject constructor(
    private val exporter: SettingsBackupExporter,
    private val importer: SettingsBackupImporter,
    private val drive: GoogleDriveBackupStore,
) {
    private val gson = Gson()

    val isDriveConnected: Boolean
        get() = drive.hasToken

    fun disconnectDrive() = drive.clearToken()

    suspend fun beginDriveAuth(activity: Activity): GoogleDriveAuthStart =
        drive.beginAuthorize(activity)

    suspend fun completeDriveAuth(
        activity: Activity,
        result: ActivityResult,
    ): GoogleDriveAuthOutcome =
        drive.handleAuthorizeResult(activity, result)

    suspend fun listDriveBackups(): List<DriveBackupFile> = drive.listBackups()

    suspend fun deleteDriveBackup(fileId: String) = drive.deleteBackup(fileId)

    /**
     * [fileId] задан — заменяет существующую копию, не задан — создаёт новую с именем [name].
     */
    suspend fun backupToDrive(
        fileId: String?,
        name: String,
        onProgress: (current: Int, total: Int) -> Unit,
    ) {
        val backup = exporter.export { current, total ->
            // локальный сбор — 0..total, загрузка будет +1 шаг
            onProgress(current, total + 1)
        }
        val localTotal = backup.albums.size + 2
        onProgress(localTotal, localTotal + 1)
        drive.uploadJson(gson.toJson(backup), fileId, name)
        onProgress(localTotal + 1, localTotal + 1)
    }

    suspend fun restoreFromDrive(
        fileId: String,
        mode: SettingsRestoreMode,
        onProgress: (current: Int, total: Int) -> Unit,
    ): SettingsBackupResult {
        onProgress(0, 1)
        val json = drive.downloadJson(fileId)
        val backup = gson.fromJson(json, GallerySettingsBackup::class.java)
            ?: error("Invalid backup file")
        require(backup.version == 1) { "Unsupported backup version ${backup.version}" }
        // download = шаг 1, дальше import со своими шагами
        val importSteps = 2 + backup.albums.size + 1
        val total = 1 + importSteps
        onProgress(1, total)
        return importer.import(backup, mode) { current, importTotal ->
            onProgress(1 + current, 1 + importTotal)
        }
    }
}
