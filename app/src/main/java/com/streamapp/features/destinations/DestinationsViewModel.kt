package com.streamapp.features.destinations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamapp.core.database.dao.DestinationDao
import com.streamapp.core.database.entity.DestinationEntity
import com.streamapp.core.model.Platform
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DestinationsViewModel @Inject constructor(
    private val destinationDao: DestinationDao
) : ViewModel() {

    val destinations: StateFlow<List<DestinationEntity>> = destinationDao.getAllDestinations()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addDestination(platform: Platform, name: String, rtmpUrl: String, streamKey: String) {
        viewModelScope.launch {
            val entity = DestinationEntity(
                id = UUID.randomUUID().toString(),
                platform = platform,
                name = name,
                rtmpUrl = rtmpUrl,
                streamKey = streamKey,
                isEnabled = false
            )
            destinationDao.insertDestination(entity)
        }
    }

    fun toggleDestination(destination: DestinationEntity) {
        viewModelScope.launch {
            destinationDao.updateDestination(destination.copy(isEnabled = !destination.isEnabled))
        }
    }

    fun deleteDestination(destination: DestinationEntity) {
        viewModelScope.launch {
            destinationDao.deleteDestination(destination)
        }
    }
}
