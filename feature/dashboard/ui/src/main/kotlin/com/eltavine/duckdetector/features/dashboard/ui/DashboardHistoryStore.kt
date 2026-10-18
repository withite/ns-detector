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
import org.json.JSONArray
import org.json.JSONObject

/** Android persistence adapter. The minimal JSON is app-private and excluded from cloud backup. */
internal class DashboardHistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences("ns_detector_scan_history", Context.MODE_PRIVATE)

    fun load(): List<DashboardScanSnapshot> {
        val raw = preferences.getString("snapshots_v1", null) ?: return emptyList()
        val array = JSONArray(raw)
        return (0 until minOf(array.length(), DASHBOARD_HISTORY_LIMIT)).map { index ->
            val item = array.getJSONObject(index)
            val entries = item.getJSONArray("detectors")
            DashboardScanSnapshot(
                completedAtEpochMillis = item.getLong("completedAt"),
                durationMillis = item.getLong("duration"),
                detectors = (0 until minOf(entries.length(), 200)).map { detectorIndex ->
                    val entry = entries.getJSONObject(detectorIndex)
                    val severity = DetectionSeverity.valueOf(entry.getString("severity"))
                    val infoKind = if (severity == DetectionSeverity.INFO) InfoKind.valueOf(entry.getString("infoKind")) else null
                    DashboardDetectorSnapshot(
                        detectorId = entry.getString("id"),
                        status = DetectorStatus(severity, infoKind),
                        headline = entry.getString("headline"),
                    )
                },
            )
        }
    }

    fun append(snapshot: DashboardScanSnapshot): List<DashboardScanSnapshot> {
        val stored = load()
        if (snapshot.completedAtEpochMillis <= preferences.getLong("cleared_through", Long.MIN_VALUE)) return stored
        val updated = appendDashboardHistory(stored, snapshot)
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
        return updated
    }

    fun clear(completedAtEpochMillis: Long?) {
        val clearedThrough = maxOf(
            preferences.getLong("cleared_through", Long.MIN_VALUE),
            completedAtEpochMillis ?: Long.MIN_VALUE,
        )
        // Prevent a rotation or re-entering the dashboard from re-saving the scan just cleared.
        check(preferences.edit().clear().putLong("cleared_through", clearedThrough).commit())
    }
}
