package com.asinosoft.gallery.model

import android.app.Activity
import android.app.PendingIntent
import androidx.activity.result.ActivityResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.gallery.data.AppAnalytics
import com.asinosoft.gallery.data.backup.DriveBackupFile
import com.asinosoft.gallery.data.backup.GoogleDriveAuthOutcome
import com.asinosoft.gallery.data.backup.GoogleDriveAuthStart
import com.asinosoft.gallery.data.backup.SettingsBackupRepository
import com.asinosoft.gallery.data.backup.SettingsBackupResult
import com.asinosoft.gallery.data.backup.SettingsRestoreMode
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class BackupUiEvent {
    data class RequestAuth(val pendingIntent: PendingIntent) : BackupUiEvent()
    data object Connected : BackupUiEvent()
    data object AuthCancelled : BackupUiEvent()
    data object Saved : BackupUiEvent()
    data object Deleted : BackupUiEvent()
    data class Restored(val result: SettingsBackupResult) : BackupUiEvent()
    data class Error(val message: String) : BackupUiEvent()
}

enum class BackupJob {
    Saving,
    Restoring,
    Deleting,
}

data class BackupUiState(
    val connected: Boolean = false,
    val busy: Boolean = false,
    val job: BackupJob? = null,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val backups: List<DriveBackupFile> = emptyList(),
    val backupsLoading: Boolean = false,
) {
    val progress: Float
        get() = if (progressTotal > 0) {
            (progressCurrent.toFloat() / progressTotal.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

@HiltViewModel
class SettingsBackupViewModel @Inject constructor(
    private val repository: SettingsBackupRepository,
    private val analytics: AppAnalytics,
) : ViewModel() {
    private val _state = MutableStateFlow(BackupUiState(connected = repository.isDriveConnected))
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<BackupUiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<BackupUiEvent> = _events.asSharedFlow()

    fun refreshConnection() {
        _state.update { it.copy(connected = repository.isDriveConnected) }
    }

    fun connect(activity: Activity) = viewModelScope.launch {
        if (_state.value.busy) return@launch
        _state.update { it.copy(busy = true, job = null) }
        runCatching {
            when (val start = repository.beginDriveAuth(activity)) {
                GoogleDriveAuthStart.AlreadyAuthorized -> {
                    _state.update { it.copy(connected = true) }
                    _events.emit(BackupUiEvent.Connected)
                }

                is GoogleDriveAuthStart.NeedsUi ->
                    _events.emit(BackupUiEvent.RequestAuth(start.pendingIntent))
            }
        }.onFailure { e ->
            _events.emit(BackupUiEvent.Error(authErrorMessage(e)))
        }
        _state.update {
            it.copy(busy = false, connected = repository.isDriveConnected, job = null)
        }
    }

    fun onAuthResult(activity: Activity, result: ActivityResult) = viewModelScope.launch {
        _state.update { it.copy(busy = true, job = null) }
        runCatching {
            when (val outcome = repository.completeDriveAuth(activity, result)) {
                GoogleDriveAuthOutcome.Success -> {
                    _state.update { it.copy(connected = true) }
                    _events.emit(BackupUiEvent.Connected)
                    analytics.googleSignedIn()
                }

                GoogleDriveAuthOutcome.Cancelled ->
                    _events.emit(BackupUiEvent.AuthCancelled)

                is GoogleDriveAuthOutcome.Failed ->
                    _events.emit(BackupUiEvent.Error(outcome.message))
            }
        }.onFailure { e ->
            _events.emit(BackupUiEvent.Error(authErrorMessage(e)))
        }
        _state.update {
            it.copy(busy = false, connected = repository.isDriveConnected, job = null)
        }
    }

    fun disconnect() {
        repository.disconnectDrive()
        _state.update { it.copy(connected = false, backups = emptyList()) }
    }

    /** Обновляет список сохранённых копий для диалогов сохранения/загрузки. */
    fun loadBackups() = viewModelScope.launch {
        if (!repository.isDriveConnected || _state.value.backupsLoading) return@launch
        _state.update { it.copy(backupsLoading = true) }
        runCatching { repository.listDriveBackups() }
            .onSuccess { backups -> _state.update { it.copy(backups = backups) } }
            .onFailure { _events.emit(BackupUiEvent.Error(it.message ?: "error")) }
        _state.update {
            it.copy(backupsLoading = false, connected = repository.isDriveConnected)
        }
    }

    private suspend fun refreshBackups() {
        runCatching { repository.listDriveBackups() }
            .onSuccess { backups -> _state.update { it.copy(backups = backups) } }
    }

    /** Сохранить новую копию с именем [name]. */
    fun saveNew(name: String) = backup(fileId = null, name = name)

    /** Заменить существующую копию текущими настройками. */
    fun replaceBackup(file: DriveBackupFile) = backup(fileId = file.id, name = file.displayName)

    private fun backup(fileId: String?, name: String) = viewModelScope.launch {
        if (_state.value.busy) return@launch
        _state.update {
            it.copy(busy = true, job = BackupJob.Saving, progressCurrent = 0, progressTotal = 0)
        }
        runCatching {
            if (!repository.isDriveConnected) error("not_connected")
            repository.backupToDrive(fileId, name, ::reportProgress)
            refreshBackups()
            _events.emit(BackupUiEvent.Saved)
            analytics.backupSaved()
        }.onFailure {
            _events.emit(BackupUiEvent.Error(it.message ?: "error"))
        }
        _state.update {
            it.copy(
                busy = false,
                connected = repository.isDriveConnected,
                job = null,
                progressCurrent = 0,
                progressTotal = 0,
            )
        }
    }

    fun deleteBackup(file: DriveBackupFile) = viewModelScope.launch {
        if (_state.value.busy) return@launch
        _state.update {
            it.copy(busy = true, job = BackupJob.Deleting, progressCurrent = 0, progressTotal = 0)
        }
        runCatching {
            if (!repository.isDriveConnected) error("not_connected")
            repository.deleteDriveBackup(file.id)
            refreshBackups()
            _events.emit(BackupUiEvent.Deleted)
        }.onFailure {
            _events.emit(BackupUiEvent.Error(it.message ?: "error"))
        }
        _state.update {
            it.copy(
                busy = false,
                connected = repository.isDriveConnected,
                job = null,
                progressCurrent = 0,
                progressTotal = 0,
            )
        }
    }

    /** Загрузить выбранную копию: слияние с текущими настройками или полная перезапись ими. */
    fun restore(file: DriveBackupFile, mode: SettingsRestoreMode) = viewModelScope.launch {
        if (_state.value.busy) return@launch
        _state.update {
            it.copy(busy = true, job = BackupJob.Restoring, progressCurrent = 0, progressTotal = 0)
        }
        runCatching {
            if (!repository.isDriveConnected) error("not_connected")
            val result = repository.restoreFromDrive(file.id, mode, ::reportProgress)
            _events.emit(BackupUiEvent.Restored(result))
            analytics.backupRestored()
        }.onFailure {
            _events.emit(BackupUiEvent.Error(it.message ?: "error"))
        }
        _state.update {
            it.copy(
                busy = false,
                connected = repository.isDriveConnected,
                job = null,
                progressCurrent = 0,
                progressTotal = 0,
            )
        }
    }

    private fun reportProgress(current: Int, total: Int) {
        _state.update {
            it.copy(progressCurrent = current, progressTotal = total.coerceAtLeast(1))
        }
    }

    private fun authErrorMessage(e: Throwable): String {
        val api = e as? ApiException
            ?: (e.cause as? ApiException)
        val unregistered = api?.message?.contains("UNREGISTERED_ON_API_CONSOLE", true) == true
        if (null != api && (api.statusCode == CommonStatusCodes.DEVELOPER_ERROR || unregistered)) {
            return "Приложение не зарегистрировано в Google Cloud. Создайте OAuth-клиент " +
                "типа Android: package com.asinosoft.gallery и SHA-1 ключа, которым " +
                "подписана эта сборка, и включите Drive API."
        }
        return e.message?.takeIf { it.isNotBlank() } ?: "error"
    }
}
