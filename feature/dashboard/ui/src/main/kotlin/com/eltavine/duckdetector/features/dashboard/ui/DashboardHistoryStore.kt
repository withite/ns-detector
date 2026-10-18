/* Modified for NS Detector: privacy-preserving local history and report exports. */
package com.eltavine.duckdetector.features.dashboard.ui

import android.content.Context
import com.eltavine.duckdetector.core.evidence.DetectionSeverity
import com.eltavine.duckdetector.core.evidence.DetectorStatus
import com.eltavine.duckdetector.core.evidence.InfoKind
import com.eltavine.duckdetector.features.dashboard.presentation.history.DASHBOARD_HISTORY_LIMIT
import com.eltavine.duckdetector.features.dashboard.presentation.history.DashboardDetectorSnapshot
import com.eltavine.duckdetector.features.dashboard.presentation.history.DashboardScanSnapshot
import com.eltavine.duckdetector.features.dashboard.presentation.history.appendDashboardHistory
import com.eltavine.duckdetector.features.dashboard.presentation.history.visibleDashboardHistory
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/** Android persistence adapter. The minimal JSON is app-private and excluded from cloud backup. */
internal class DashboardHistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences("ns_detector_scan_history", Context.MODE_PRIVATE)

    // Controllers from different activity instances must share transaction ordering.
    // This is a process-lifetime cancellation generation, so reading it never waits on disk or a
    // pending commit. Persisted tombstones cover restarts; no old IO job survives process death.
    fun revision(): Long = transactionRevision.get()

    fun load(): List<DashboardScanSnapshot> = synchronized(transactionLock) { loadLocked() }

    private fun loadLocked(): List<DashboardScanSnapshot> {
        val raw = preferences.getString("snapshots_v1", null) ?: return emptyList()
        val array = JSONArray(raw)
        val snapshots = (0 until minOf(array.length(), DASHBOARD_HISTORY_LIMIT)).map { index ->
            val item = array.getJSONObject(index)
            val entries = item.getJSONArray("detectors")
            DashboardScanSnapshot(
                completedAtEpochMillis = item.getLong("completedAt"),
                durationMillis = item.getLong("duration"),
                detectors = (0 until minOf(entries.length(), 200)).map { detectorIndex ->
                    val entry = entries.getJSONObject(detectorIndex)
                    val severity = DetectionSeverity.valueOf(entry.getString("severity"))
                    val infoKind = if (severity == DetectionSeverity.INFO && !entry.isNull("infoKind")) {
                        InfoKind.valueOf(entry.getString("infoKind"))
                    } else null
                    DashboardDetectorSnapshot(
                        detectorId = entry.getString("id"),
                        status = DetectorStatus(severity, infoKind),
                        headline = entry.getString("headline"),
                    )
                },
            )
        }
        return visibleDashboardHistory(snapshots, clearedIdsLocked().toSet())
    }

    /** The revision was captured before dispatching IO, so a queued old write cannot undo Clear. */
    fun append(snapshot: DashboardScanSnapshot, expectedRevision: Long): List<DashboardScanSnapshot> = synchronized(transactionLock) {
        val stored = loadLocked()
        if (expectedRevision != transactionRevision.get()) return@synchronized stored
        val updated = appendDashboardHistory(stored, snapshot, clearedIdsLocked().toSet())
        val array = JSONArray()
        updated.forEach { scan ->
            val entries = JSONArray()
            scan.detectors.forEach { detector ->
                entries.put(JSONObject().apply {
                    put("id", detector.detectorId)
                    put("severity", detector.status.severity.name)
                    put("infoKind", detector.status.infoKind?.name)
                    put("headline", detector.headline)
                })
            }
            array.put(JSONObject().apply {
                put("completedAt", scan.completedAtEpochMillis)
                put("duration", scan.durationMillis)
                put("detectors", entries)
            })
        }
        check(preferences.edit().putString("snapshots_v1", array.toString()).commit())
        updated
    }

    fun clear(currentScanId: Long?) = synchronized(transactionLock) {
        // Deletion remains available even if the stored snapshot JSON is corrupt.
        val storedIds = try { loadLocked().map { it.completedAtEpochMillis } } catch (_: Exception) { emptyList() }
        val previousIds = try { clearedIdsLocked() } catch (_: Exception) { emptyList() }
        val clearedIds = (listOfNotNull(currentScanId) + storedIds + previousIds).distinct().take(MAX_CLEARED_IDS)
        transactionRevision.incrementAndGet()
        check(preferences.edit()
            .remove("snapshots_v1")
            .remove("cleared_through")
            .putString("cleared_scan_ids_v1", JSONArray(clearedIds).toString())
            .remove("clear_revision")
            .commit())
    }

    private fun clearedIdsLocked(): List<Long> {
        val raw = preferences.getString("cleared_scan_ids_v1", null)
        if (raw == null) {
            // Earlier development builds used a time watermark. Retain only its exact scan id.
            return if (preferences.contains(LEGACY_CLEARED_KEY)) listOf(preferences.getLong("cleared_through", Long.MIN_VALUE)) else emptyList()
        }
        val array = JSONArray(raw)
        return (0 until minOf(array.length(), MAX_CLEARED_IDS)).map { array.getLong(it) }
    }

    private companion object {
        val transactionLock = Any()
        val transactionRevision = AtomicLong()
        const val MAX_CLEARED_IDS = 256
        const val LEGACY_CLEARED_KEY = "cleared_through"
    }
}
