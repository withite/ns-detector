/* Modified for NS Detector: privacy-preserving local history and report exports. */
package com.eltavine.duckdetector.features.dashboard.presentation.export

import com.eltavine.duckdetector.core.report.ReportBlock

/** Versioned JSON export using the same redaction policy as the readable text report. */
object DashboardJsonReportRenderer {
    fun render(export: DashboardExport): String = buildString {
        append("{\n  \"schemaVersion\": 1,\n  \"redacted\": true,\n  \"app\": {")
        field("versionName", export.header.versionName)
        append(",\"versionCode\":${export.header.versionCode},")
        field("buildHash", export.header.buildHash)
        append("},\n  ")
        field("reportTime", export.header.reportTime)
        append(",\n  \"overview\":{")
        field("verdict", export.overview.verdict.name)
        append(',')
        field("severity", export.overview.status.severity.name)
        append(',')
        field("infoKind", export.overview.status.infoKind?.name)
        append(',')
        field("headline", export.overview.headline)
        append(',')
        field("summary", export.overview.summary)
        append(",\"metrics\":{")
        export.overview.metrics.forEachIndexed { index, metric ->
            if (index > 0) append(',')
            field(metric.metric.name, metric.value)
        }
        append("}},\n  \"findings\":[")
        export.topFindings.forEachIndexed { index, finding ->
            if (index > 0) append(',')
            append('{')
            field("detectorId", finding.detectorId?.value)
            append(',')
            field("severity", finding.status.severity.name)
            append(',')
            field("headline", finding.headline)
            append(',')
            field("detail", finding.detail)
            append('}')
        }
        append("],\n  \"detectors\":[")
        export.detectors.forEachIndexed { index, report ->
            if (index > 0) append(',')
            append('{')
            field("title", report.title)
            append(',')
            field("severity", report.severity.name)
            append(',')
            field("verdict", report.verdict)
            append(",\"facts\":[")
            report.quickFacts.forEachIndexed { factIndex, fact ->
                if (factIndex > 0) append(',')
                append('{'); field("label", fact.label); append(','); field("value", ReportRedactor.redactField(fact.label, fact.value)); append('}')
            }
            append("],\"blocks\":[")
            report.blocks.forEachIndexed { blockIndex, block ->
                if (blockIndex > 0) append(',')
                appendBlock(block)
            }
            append("]}")
        }
        append("],\n  \"device\":{")
        field("brand", export.device.identity.brand)
        append(','); field("model", export.device.identity.model)
        append(','); field("androidRelease", export.device.identity.androidRelease)
        append(','); field("sdk", export.device.identity.sdk)
        append(",\"facts\":[")
        export.device.quickFacts.forEachIndexed { index, fact ->
            if (index > 0) append(',')
            append('{'); field("label", fact.label); append(','); field("value", ReportRedactor.redactField(fact.label, fact.value)); append('}')
        }
        append("],\"sections\":[")
        export.device.sections.forEachIndexed { index, block ->
            if (index > 0) append(',')
            appendBlock(block)
        }
        append("]}\n}\n")
    }

    private fun StringBuilder.appendBlock(block: ReportBlock) {
        append('{'); field("title", block.title)
        when (block) {
            is ReportBlock.Rows -> {
                append(",\"rows\":[")
                block.rows.forEachIndexed { index, row ->
                    if (index > 0) append(',')
                    append('{'); field("label", row.label); append(',')
                    field("value", ReportRedactor.redactField(row.label, row.value)); append(','); field("detail", row.detail?.let { ReportRedactor.redactField(row.label, it) }); append('}')
                }
            }
            is ReportBlock.Bullets -> {
                append(",\"items\":[")
                block.items.forEachIndexed { index, item -> if (index > 0) append(','); quoted(item) }
            }
            is ReportBlock.Verbatim -> {
                append(",\"lines\":[")
                block.lines.forEachIndexed { index, line -> if (index > 0) append(','); quoted(line) }
            }
        }
        append("]}")
    }

    private fun StringBuilder.field(key: String, value: String?) {
        quoted(key); append(':')
        if (value == null) append("null") else quoted(value)
    }

    private fun StringBuilder.quoted(value: String) {
        append('"')
        ReportRedactor.redact(value).forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
            }
        }
        append('"')
    }
}
