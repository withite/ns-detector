/* Modified for NS Detector: persist scans independently of lazy-list visibility. */
package com.eltavine.duckdetector.features.dashboard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.eltavine.duckdetector.features.dashboard.presentation.history.DashboardScanSnapshot
import com.eltavine.duckdetector.features.dashboard.presentation.history.completedHistorySnapshot
import com.eltavine.duckdetector.features.dashboard.presentation.model.DashboardUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class DashboardHistoryState(
    val scans: List<DashboardScanSnapshot>,
    val storageFailed: Boolean,
    val clearing: Boolean,
    val onClear: () -> Unit,
)

/** Call at the dashboard root, outside LazyColumn: an off-screen panel must not drop scan records. */
@Composable
internal fun rememberDashboardHistory(uiState: DashboardUiState): DashboardHistoryState {
    val context = LocalContext.current
    val store = remember(context) { DashboardHistoryStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val stateMutex = remember(store) { Mutex() }
    val snapshot = remember(uiState) { uiState.completedHistorySnapshot() }
    var history by remember { mutableStateOf(emptyList<DashboardScanSnapshot>()) }
    var storageFailed by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }

    LaunchedEffect(store, snapshot) {
        stateMutex.withLock {
            try {
                val revision = store.revision()
                history = withContext(Dispatchers.IO) {
                    if (snapshot == null) store.load() else store.append(snapshot, revision)
                }
                storageFailed = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                storageFailed = true
            }
        }
    }

    return DashboardHistoryState(history, storageFailed, clearing) {
        if (!clearing) {
            clearing = true
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                // Enter before returning the click handler, including any wait behind a pending save.
                withContext(NonCancellable) {
                    try {
                        stateMutex.withLock {
                            val currentId = snapshot?.completedAtEpochMillis ?: history.firstOrNull()?.completedAtEpochMillis
                            withContext(Dispatchers.IO) { store.clear(currentId) }
                            history = emptyList()
                            storageFailed = false
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        storageFailed = true
                    } finally {
                        clearing = false
                    }
                }
            }
        }
    }
}
