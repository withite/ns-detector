/* Copyright 2026 NS Detector contributors. Licensed under the Apache License, Version 2.0. */
package com.eltavine.duckdetector.features.dashboard.ui

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.UUID

/** Stores already-redacted report bytes outside backup; only an opaque basename enters saved state. */
internal class DashboardReportCache(private val directory: File) {
    fun newName(json: Boolean): String =
        "ns_report_${UUID.randomUUID().toString().replace("-", "")}.${if (json) "json" else "txt"}"

    fun write(name: String, redactedContent: String) {
        val destination = ownedFile(name)
        check(directory.isDirectory || directory.mkdirs()) { "Report cache unavailable" }
        val temporary = File(directory, "$name.partial")
        try {
            FileOutputStream(temporary).use { stream ->
                stream.write(redactedContent.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            check(temporary.renameTo(destination)) { "Report cache write failed" }
        } finally {
            temporary.delete()
        }
    }

    fun copyTo(name: String, output: OutputStream) {
        ownedFile(name).inputStream().use { it.copyTo(output) }
    }

    fun delete(name: String) {
        // Malformed restored state cannot turn cleanup into a crash or an arbitrary deletion.
        if (ownedReport.matches(name)) File(directory, name).delete()
    }

    fun cleanupExpired(nowEpochMillis: Long, activeName: String? = null) {
        directory.listFiles()?.forEach { file ->
            if (file.isFile && ownedCacheFile.matches(file.name) && file.name != activeName &&
                nowEpochMillis - file.lastModified() > MAX_AGE_MILLIS
            ) {
                file.delete()
            }
        }
    }

    private fun ownedFile(name: String): File {
        require(ownedReport.matches(name)) { "Invalid report cache name" }
        return File(directory, name)
    }

    private companion object {
        val ownedReport = Regex("ns_report_[0-9a-f]{32}\\.(txt|json)")
        val ownedCacheFile = Regex("ns_report_[0-9a-f]{32}\\.(txt|json)(\\.partial)?")
        const val MAX_AGE_MILLIS = 24L * 60 * 60 * 1_000
    }
}
