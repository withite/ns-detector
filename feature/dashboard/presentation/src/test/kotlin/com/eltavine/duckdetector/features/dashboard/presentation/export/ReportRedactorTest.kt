package com.eltavine.duckdetector.features.dashboard.presentation.export

import org.junit.Assert.*
import org.junit.Test

class ReportRedactorTest {
    @Test
    fun `common identifiers are removed even without sensitive field labels`() {
        val values = listOf(
            "person@example.org", "192.0.2.4", "2001:db8::42", "fe80::1%wlan0", "::1",
            "AA:BB:CC:DD:EE:FF", "aa-bb-cc-dd-ee-ff", "/storage/emulated/0/Download/private.txt",
            "/data/user/0/com.example/files/private", "/home/alice/file", "C:\\Users\\Alice\\private.txt",
            "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature",
        )
        values.forEach { value -> assertFalse("identifier leaked: $value", ReportRedactor.redact("detail = $value").contains(value)) }
    }

    @Test
    fun `structured secrets are redacted throughout free text`() {
        val input = "ro.boot.serialno=SN987654 serial number: XYZ123 token=secret123 android_id: aabbccdd Bearer abcd1234 https://alice:secret@example.org/path?api_key=keyvalue"
        val redacted = ReportRedactor.redact(input)
        listOf("SN987654", "XYZ123", "secret123", "aabbccdd", "abcd1234", "alice:secret", "keyvalue").forEach {
            assertFalse("secret leaked: $it", redacted.contains(it))
        }
    }

    @Test
    fun `diagnostic timestamps hex addresses and system paths remain readable`() {
        val input = "2026-10-17 03:04:05 0x1234 /system/bin/su android-6.1.20"
        assertEquals(input, ReportRedactor.redact(input))
    }
}
