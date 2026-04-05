package com.storm.classwearos.presentation

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel : ViewModel() {
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    var reminderEnabled by mutableStateOf(true)
        private set

    fun toggleService(context: Context, enabled: Boolean) {
        val intent = Intent(context, SyncService::class.java)
        if (enabled) {
            context.startForegroundService(intent)
        } else {
            context.stopService(intent)
        }
        _isServiceRunning.value = enabled
    }

    fun toggleReminder(enabled: Boolean) {
        reminderEnabled = enabled
    }

    fun refreshData() {
        // This is a placeholder for triggering data refresh
    }
    
    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }
}