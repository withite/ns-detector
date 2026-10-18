/* Modified for NS Detector: privacy-preserving local history and report exports. */
package com.eltavine.duckdetector.features.dashboard.presentation.export

/**
 * Best-effort removal of common personal identifiers from every rendered value, including verbatim
 * evidence. This never changes a detector's typed verdict. Unknown identifier formats may remain;
 * users must review a report before sharing it.
 */
object ReportRedactor {
    private val email = Regex("(?i)(?<![\\w.+-])[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}(?![\\w.-])")
    private val mac = Regex("(?i)(?<![0-9a-f])(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}(?![0-9a-f])")
    private val ipv4 = Regex("(?<![\\d.])(?:25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)){3}(?![\\d.])")
    private val ipv6 = Regex("(?i)(?<![\\w:])(?:[0-9a-f]{0,4}:){2,}[0-9a-f:.]*(?:%[a-z0-9]+)?(?![\\w:])")
    private val compressedIpv6 = Regex("::")
    private val userPath = Regex("(?i)(?:/(?:storage/emulated/\\d+|sdcard|home|Users|data/(?:user(?:_de)?/\\d+|data))/[^\\s\\\"'<>|,;)]*|[A-Z]:\\\\Users\\\\[^\\s\\\"'<>|,;)]*)")
    private val credential = Regex("(?i)(https?://)[^/\\s:@]+:[^/\\s@]+@")
    private val bearer = Regex("(?i)\\b(Bearer|Basic)\\s+[A-Za-z0-9._~+/=-]+")
    private val jwt = Regex("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b")
    private val sensitiveField = Regex("(?i)(?:serial|android[ _-]?id|imei|imsi|iccid|token|api[ _-]?key|password|passwd|authorization|序列号|设备标识|令牌|密码)")
    private val serialLine = Regex("(?im)((?:serial(?:no|number|[ _-]number)?|序列号)[\\t ]*[:=][\\t ]*)([^\\r\\n]+)")
    private val keyedSecret = Regex(
        "(?i)((?:\\b(?:ro\\.(?:boot\\.)?)?(?:serial(?:no|number|[ _-]number)?|android[ _-]?id|imei|imsi|iccid|token|access[ _-]?token|refresh[ _-]?token|api[ _-]?key|password|passwd|authorization)|序列号|设备标识|令牌|密码)[\\\"']?[\\t ]*[:=][\\t ]*[\\\"']?)([^\\s\\\"',;|}]+)",
    )

    fun redactField(label: String, value: String): String =
        if (sensitiveField.containsMatchIn(label) && value.isNotBlank()) "[REDACTED]" else redact(value)

    fun redact(value: String): String {
        var result = credential.replace(value) { "${it.groupValues[1]}[REDACTED]@" }
        result = userPath.replace(result, "[REDACTED_PATH]")
        result = email.replace(result, "[REDACTED_EMAIL]")
        result = mac.replace(result, "[REDACTED_MAC]")
        result = ipv4.replace(result, "[REDACTED_IP]")
        result = ipv6.replace(result) {
            val candidate = it.value
            if (candidate.count { character -> character == ':' } >= 7 || compressedIpv6.containsMatchIn(candidate)) {
                "[REDACTED_IP]"
            } else {
                candidate
            }
        }
        result = bearer.replace(result) { "${it.groupValues[1]} [REDACTED]" }
        result = jwt.replace(result, "[REDACTED_TOKEN]")
        result = serialLine.replace(result) { "${it.groupValues[1]}[REDACTED]" }
        return keyedSecret.replace(result) { "${it.groupValues[1]}[REDACTED]" }
    }
}
