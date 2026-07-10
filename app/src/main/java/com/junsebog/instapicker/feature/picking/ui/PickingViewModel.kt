package com.junsebog.instapicker.feature.picking.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.junsebog.instapicker.feature.picking.data.PickingRepository
import com.junsebog.instapicker.feature.picking.domain.PickTab
import com.junsebog.instapicker.feature.picking.domain.PickingEffect
import com.junsebog.instapicker.feature.picking.domain.PickingIntent
import com.junsebog.instapicker.feature.picking.domain.PickingReducer
import com.junsebog.instapicker.feature.picking.domain.PickingUiState
import com.junsebog.instapicker.feature.picking.domain.ScannerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Wires the UI to the pure reducer and the repository. The reducer decides *what*
 * changes; this ViewModel *executes* the resulting effects (save, log) and exposes
 * one immutable [PickingUiState] as a [StateFlow].
 *
 * Domain state is rebuilt from the repository's Room-backed flow (the single source
 * of truth). Ephemeral view state — the active tab and whether the scanner is open
 * for an item — is mirrored into [SavedStateHandle] so the exact active view is
 * restored after process death.
 */
@HiltViewModel
class PickingViewModel @Inject constructor(
    private val repository: PickingRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val reducer = PickingReducer(now = System::currentTimeMillis)

    private val _state = MutableStateFlow(restoredInitialState())
    val state: StateFlow<PickingUiState> = _state.asStateFlow()

    /** Serializes intents: each is reduced, persisted (write-through) and published before the next. */
    private val intents = Channel<PickingIntent>(Channel.UNLIMITED)

    init {
        viewModelScope.launch {
            for (intent in intents) {
                val reduction = reducer.reduce(state = _state.value, intent = intent)
                val persists = reduction.effects.any {
                    it is PickingEffect.SaveItem || it is PickingEffect.AppendLog
                }
                // Write-through: commit the effects in one transaction BEFORE publishing the state.
                if (persists) repository.transaction { reduction.effects.forEach { runEffect(it) } }
                _state.value = reduction.state
                rememberViewState(reduction.state)
            }
        }
        viewModelScope.launch {
            repository.observeLog(SESSION_ID).collect { log ->
                _state.update { it.copy(log = log) }
            }
        }
        viewModelScope.launch {
            val seeded = repository.ensureSeeded(SESSION_ID)
            dispatch(PickingIntent.LoadSession(sessionId = SESSION_ID, items = seeded))
        }
    }

    fun dispatch(intent: PickingIntent) {
        intents.trySend(intent)
    }

    private suspend fun runEffect(effect: PickingEffect) {
        when (effect) {
            is PickingEffect.SaveItem -> repository.persist(sessionId = SESSION_ID, item = effect.item)
            is PickingEffect.AppendLog -> repository.append(effect.entry)
            is PickingEffect.OpenCamera, PickingEffect.CloseCamera -> Unit // handled by the scanner UI
        }
    }

    private fun rememberViewState(state: PickingUiState) {
        savedState[KEY_TAB] = state.activeTab.name
        savedState[KEY_SCAN_ITEM] = (state.scanner as? ScannerState.Active)?.itemId
        savedState[KEY_LOG_OPEN] = state.logOpen
    }

    private fun restoredInitialState(): PickingUiState {
        val tab = savedState.get<String>(KEY_TAB)?.let { PickTab.valueOf(it) } ?: PickTab.PENDING
        val scanner = savedState.get<String>(KEY_SCAN_ITEM)
            ?.let { ScannerState.Active(itemId = it) }
            ?: ScannerState.Idle
        val logOpen = savedState.get<Boolean>(KEY_LOG_OPEN) ?: false
        return PickingUiState(sessionId = SESSION_ID, activeTab = tab, scanner = scanner, logOpen = logOpen)
    }

    private companion object {
        const val SESSION_ID = "session-local"
        const val KEY_TAB = "active_tab"
        const val KEY_SCAN_ITEM = "scanner_item"
        const val KEY_LOG_OPEN = "log_open"
    }
}
