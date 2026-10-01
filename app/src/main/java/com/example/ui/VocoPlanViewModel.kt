package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.calendar.CalendarHelper
import com.example.calendar.CalendarOperationResult
import com.example.model.CalendarEventData
import com.example.parser.GermanVoiceParser
import com.example.speech.VoiceInputManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VocoPlanUiState(
    val eventData: CalendarEventData = CalendarEventData(),
    val displayText: String = "",
    val isListening: Boolean = false,
    val rmsLevel: Float = 0f,
    val charCount: Int = 0,
    val isDarkTheme: Boolean = true
)

class VocoPlanViewModel(application: Application) : AndroidViewModel(application) {

    private val voiceInputManager = VoiceInputManager(application)

    private val _uiState = MutableStateFlow(
        VocoPlanUiState(
            eventData = CalendarEventData(),
            displayText = CalendarEventData().toDisplayText()
        )
    )
    val uiState: StateFlow<VocoPlanUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        voiceInputManager.onFinalResult = { recognizedText ->
            processVoiceInput(recognizedText)
        }

        viewModelScope.launch {
            voiceInputManager.isListening.collect { listening ->
                _uiState.value = _uiState.value.copy(isListening = listening)
            }
        }

        viewModelScope.launch {
            voiceInputManager.rmsLevel.collect { rms ->
                _uiState.value = _uiState.value.copy(rmsLevel = rms)
            }
        }

        viewModelScope.launch {
            voiceInputManager.charCount.collect { count ->
                _uiState.value = _uiState.value.copy(charCount = count)
            }
        }

        viewModelScope.launch {
            voiceInputManager.lastError.collect { error ->
                if (error != null) {
                    _messages.emit(error)
                }
            }
        }
    }

    fun toggleTheme() {
        _uiState.value = _uiState.value.copy(isDarkTheme = !_uiState.value.isDarkTheme)
    }

    fun onDisplayTextChanged(newText: String) {
        val currentEvent = _uiState.value.eventData
        val updatedEvent = CalendarEventData.fromDisplayText(newText, currentEvent)
        _uiState.value = _uiState.value.copy(
            displayText = newText,
            eventData = updatedEvent
        )
    }

    fun onReminderSelected(minutes: Int, label: String) {
        val current = _uiState.value.eventData
        val updated = current.copy(
            reminderMinutes = minutes,
            reminderLabel = label
        )
        _uiState.value = _uiState.value.copy(
            eventData = updated,
            displayText = updated.toDisplayText()
        )
    }

    fun clearText() {
        val empty = CalendarEventData(reminderMinutes = 60, reminderLabel = "1 Std. vorher")
        _uiState.value = _uiState.value.copy(
            eventData = empty,
            displayText = empty.toDisplayText()
        )
    }

    fun startListening() {
        voiceInputManager.startListening()
    }

    fun stopListening() {
        voiceInputManager.stopListening()
    }

    fun processVoiceInput(spokenText: String) {
        val current = _uiState.value.eventData
        val parsed = GermanVoiceParser.parse(spokenText, current)
        _uiState.value = _uiState.value.copy(
            eventData = parsed,
            displayText = parsed.toDisplayText()
        )
        viewModelScope.launch {
            _messages.emit("Sprache erkannt & analysiert")
        }
    }

    fun saveToDefaultCalendar(context: Context) {
        val event = _uiState.value.eventData
        if (event.title.isBlank() && event.dateTime == null) {
            viewModelScope.launch {
                _messages.emit("Bitte geben Sie zuerst einen Anlass oder Termin an")
            }
            return
        }

        viewModelScope.launch {
            when (val result = CalendarHelper.saveToDefaultCalendar(context, event)) {
                is CalendarOperationResult.Success -> _messages.emit(result.message)
                is CalendarOperationResult.LaunchedIntent -> _messages.emit(result.message)
                is CalendarOperationResult.Error -> _messages.emit(result.message)
            }
        }
    }

    fun saveToGoogleCalendar(context: Context) {
        val event = _uiState.value.eventData
        if (event.title.isBlank() && event.dateTime == null) {
            viewModelScope.launch {
                _messages.emit("Bitte geben Sie zuerst einen Anlass oder Termin an")
            }
            return
        }

        viewModelScope.launch {
            when (val result = CalendarHelper.saveToGoogleCalendar(context, event)) {
                is CalendarOperationResult.Success -> _messages.emit(result.message)
                is CalendarOperationResult.LaunchedIntent -> _messages.emit(result.message)
                is CalendarOperationResult.Error -> _messages.emit(result.message)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceInputManager.destroy()
    }
}
