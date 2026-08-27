package com.streamapp.features.destinations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamapp.core.broadcaster.stream.BroadcastManager
import com.streamapp.core.broadcaster.stream.BroadcastState
import com.streamapp.core.database.dao.DestinationDao
import com.streamapp.core.database.entity.DestinationEntity
import com.streamapp.core.model.DestinationConnectionState
import com.streamapp.core.model.DestinationValidator
import com.streamapp.core.model.Platform
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class DestinationUiItem(
    val entity: DestinationEntity,
    val connectionState: DestinationConnectionState
)

@HiltViewModel
class DestinationsViewModel @Inject constructor(
    private val destinationDao: DestinationDao,
    private val broadcastManager: BroadcastManager
) : ViewModel() {

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val destinations: StateFlow<List<DestinationUiItem>> = combine(
        destinationDao.getAllDestinations(),
        broadcastManager.broadcastState
    ) { entities: List<DestinationEntity>, broadcastState: BroadcastState ->
        entities.map { entity ->
            val connState = when {
                !entity.isEnabled -> DestinationConnectionState.DISABLED
                broadcastState == BroadcastState.STREAMING -> DestinationConnectionState.LIVE
                broadcastState == BroadcastState.CONNECTING -> DestinationConnectionState.CONNECTING
                broadcastState == BroadcastState.ERROR -> DestinationConnectionState.ERROR
                else -> DestinationConnectionState.READY
            }
            DestinationUiItem(entity = entity, connectionState = connState)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun addDestination(platform: Platform, name: String, rtmpUrl: String, streamKey: String) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            _errorMessage.value = "Имя профиля не может быть пустым"
            return
        }

        val normalizedUrl = DestinationValidator.validateAndNormalizeRtmpUrl(rtmpUrl)
        if (normalizedUrl == null) {
            _errorMessage.value = "Некорректный RTMP URL. Требуется rtmp:// или rtmps:// без пробелов"
            return
        }

        val sanitizedKey = DestinationValidator.sanitizeStreamKey(streamKey)
        if (sanitizedKey == null) {
            _errorMessage.value = "Недопустимый ключ стрима. Ключ не должен содержать пробелы и спецсимволы переноса строки"
            return
        }

        viewModelScope.launch {
            try {
                val entity = DestinationEntity(
                    id = UUID.randomUUID().toString(),
                    platform = platform,
                    name = trimmedName,
                    rtmpUrl = normalizedUrl,
                    streamKey = sanitizedKey,
                    isEnabled = false
                )
                destinationDao.upsertDestination(entity)
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка сохранения платформы: ${e.localizedMessage ?: "Неизвестная ошибка"}"
            }
        }
    }

    fun toggleDestination(destination: DestinationEntity) {
        viewModelScope.launch {
            try {
                destinationDao.updateDestination(destination.copy(isEnabled = !destination.isEnabled))
            } catch (e: Exception) {
                _errorMessage.value = "Не удалось изменить статус платформы: ${e.localizedMessage ?: "Ошибка базы данных"}"
            }
        }
    }

    fun deleteDestination(destination: DestinationEntity) {
        viewModelScope.launch {
            try {
                destinationDao.deleteDestination(destination)
            } catch (e: Exception) {
                _errorMessage.value = "Не удалось удалить платформу: ${e.localizedMessage ?: "Ошибка базы данных"}"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
