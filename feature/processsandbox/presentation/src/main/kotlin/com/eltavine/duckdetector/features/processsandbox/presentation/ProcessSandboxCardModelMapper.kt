/*
 * Copyright 2026 Duck Apps Contributor
 * If you have any questions, suggestions, or other inquiries, please email Eltavine <me@eltavine.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.eltavine.duckdetector.features.processsandbox.presentation

import com.eltavine.duckdetector.core.evidence.DetectorStatus
import com.eltavine.duckdetector.features.processsandbox.domain.ProcessSandboxReport
import com.eltavine.duckdetector.features.processsandbox.domain.ProcessSandboxStage
import com.eltavine.duckdetector.features.processsandbox.domain.SandboxObservation
import com.eltavine.duckdetector.features.processsandbox.domain.SandboxObservationState
import com.eltavine.duckdetector.features.processsandbox.domain.capabilityStatus
import com.eltavine.duckdetector.features.processsandbox.domain.hasExpandedCapabilities
import com.eltavine.duckdetector.features.processsandbox.domain.hasUidInconsistency
import com.eltavine.duckdetector.features.processsandbox.domain.seccompStatus
import com.eltavine.duckdetector.features.processsandbox.domain.toDetectorStatus
import com.eltavine.duckdetector.features.processsandbox.domain.toObservationStatus
import com.eltavine.duckdetector.features.processsandbox.domain.uidStatus
import com.eltavine.duckdetector.features.processsandbox.presentation.model.ProcessSandboxCardModel
import com.eltavine.duckdetector.features.processsandbox.presentation.model.ProcessSandboxDetailRowModel
import java.util.Locale

/** Words explain the scope of the typed domain rule; no raw UIDs or proc content are exported. */
class ProcessSandboxCardModelMapper(locale: Locale = Locale.getDefault()) {
    private val chinese = locale.language == Locale.CHINESE.language

    fun map(report: ProcessSandboxReport): ProcessSandboxCardModel = ProcessSandboxCardModel(
        title = text("Process Sandbox", "进程沙箱"),
        subtitle = text("Credentials · granted capabilities · Seccomp", "进程身份 · 已授权能力 · Seccomp"),
        status = report.toDetectorStatus(),
        verdict = when {
            report.stage == ProcessSandboxStage.LOADING -> text("Reading process snapshot", "正在读取进程快照")
            report.stage == ProcessSandboxStage.FAILED -> text("Collection failed", "采集失败")
            report.hasUidInconsistency() || report.hasExpandedCapabilities() -> text("Application baseline differs", "应用基线存在差异")
            !report.probed -> text("Not evaluated", "未能完成评估")
            else -> text("Process snapshot observed", "已观察进程快照")
        },
        summary = text(
            "This app's sampling thread only. A differing credential view or extra granted capability is a diagnostic observation, not proof of Root or a hook. Zero masks and Seccomp filter mode do not attest to an intact sandbox.",
            "仅观察本应用执行采样的线程。身份视图差异或额外已授权能力属于诊断线索，不能据此断言 Root 或 Hook。能力掩码为零、处于 Seccomp 过滤模式均不能证明沙箱完整。",
        ),
        detailsTitle = text("Kernel status observations", "内核状态观察"),
        scopeTitle = text("Scope and interpretation", "范围与解释"),
        signalRows = if (report.stage == ProcessSandboxStage.READY) rows(report) else emptyList(),
        scanRows = listOf(ProcessSandboxDetailRowModel(
            label = text("Collection", "采集方式"),
            value = text("One bounded proc snapshot", "一次有界 proc 快照"),
            status = report.toDetectorStatus(),
            detail = report.errorMessage ?: text(
                "At most 64 KiB from /proc/thread-self/status; UID checked before and after. No privileges requested, no raw snapshot saved. Procfs and the public UID API remain within the observed environment's trust boundary.",
                "最多读取 64 KiB 的 /proc/thread-self/status，并在前后核对 UID。不申请额外权限，不保存原始快照。procfs 与公开 UID 接口仍处于被测环境的信任边界内。",
            ),
        )),
    )

    private fun rows(report: ProcessSandboxReport): List<ProcessSandboxDetailRowModel> = listOf(
        row(
            text("Host baseline", "宿主基线"), report.host,
            report.host.value?.let {
                if (it.ordinaryApplication) text("Ordinary installed application", "普通安装应用")
                else text("Special or system host · context only", "特殊身份或系统宿主 · 仅供参考")
            }, report.host.toObservationStatus(),
            text("System applications and identities outside the ordinary multiuser application range are not scored against the application baseline.",
                "系统应用及不属于多用户普通应用范围的身份，不参与普通应用基线评分。"),
        ),
        row(
            text("Credential views", "身份视图"), report.snapshot.uids,
            report.snapshot.uids.value?.let {
                when {
                    report.hasUidInconsistency() -> text("Credential views differ", "身份视图存在差异")
                    report.host.value?.ordinaryApplication == true -> text("All four IDs match the sampled UID", "四类身份均与采样 UID 一致")
                    else -> text("Observed · baseline not applied", "已观察 · 未应用普通基线")
                }
            }, report.uidStatus(),
            text("Real, effective, saved and filesystem UIDs versus the same sampling thread's public UID API. Intentional native credential changes can differ; this is not independent hook verification or a claim about other threads.",
                "比较同一采样线程的真实、有效、保存和文件系统身份与公开接口的 UID。原生代码有意修改身份也可造成差异；此项不是独立 Hook 验证，也不代表其他线程。"),
        ),
        capabilityRow(report, "CapEff", text("Effective capabilities", "有效能力"), report.snapshot.effectiveCapabilities),
        capabilityRow(report, "CapPrm", text("Permitted capabilities", "许可能力"), report.snapshot.permittedCapabilities),
        capabilityRow(report, "CapAmb", text("Ambient capabilities", "环境能力"), report.snapshot.ambientCapabilities),
        row(
            "Seccomp", report.snapshot.seccomp,
            report.snapshot.seccomp.value?.let { mode -> when (mode) {
                0 -> text("Disabled for the sampling thread", "采样线程未启用")
                1 -> text("Strict mode", "严格模式")
                2 -> text("Filter mode", "过滤模式")
                else -> text("Unknown mode", "未知模式")
            } }, report.seccompStatus(),
            text("Mode 0 is informational: AOSP can skip filters under permissive security policy. Mode 2 does not verify filter contents, all threads or whole-device policy. Missing fields can reflect kernel configuration.",
                "模式 0 仅供参考：AOSP 在宽容安全策略下可跳过过滤器。模式 2 不验证过滤器内容、其他线程或整机策略。缺失字段可能与内核配置有关。"),
        ),
    )

    private fun capabilityRow(
        report: ProcessSandboxReport, field: String, label: String, observation: SandboxObservation<ULong>,
    ): ProcessSandboxDetailRowModel = row(
        "$label ($field)", observation, observation.value?.let { "0x" + it.toString(16).padStart(16, '0') },
        report.capabilityStatus(observation),
        text("Granted set only. CAP_BLOCK_SUSPEND (bit 36), legitimately granted by AOSP to the wakelock GID, is excluded from scoring. Bounding capabilities and NoNewPrivs are not scored. Custom privileged SDK hosts may intentionally retain extra capabilities.",
            "仅检查已授权集合。AOSP 可向 wakelock 组合法授予 CAP_BLOCK_SUSPEND（第 36 位），该位不参与评分。不对能力边界集及 NoNewPrivs 评分。定制特权 SDK 宿主可能有意保留额外能力。"),
    )

    private fun row(
        label: String, observation: SandboxObservation<*>, value: String?, status: DetectorStatus, detail: String,
    ): ProcessSandboxDetailRowModel = ProcessSandboxDetailRowModel(
        label, value ?: stateLabel(observation.state), status,
        listOfNotNull(observation.failureName, detail).joinToString(" "),
    )

    private fun stateLabel(state: SandboxObservationState): String = when (state) {
        SandboxObservationState.NOT_EVALUATED -> text("Not evaluated", "未评估")
        SandboxObservationState.OBSERVED -> text("Observed", "已观察")
        SandboxObservationState.MISSING -> text("Field missing", "字段缺失")
        SandboxObservationState.MALFORMED -> text("Malformed snapshot field", "快照字段格式异常")
        SandboxObservationState.PERMISSION_DENIED -> text("Read denied", "读取被拒绝")
        SandboxObservationState.UNAVAILABLE -> text("Snapshot unavailable or access denied", "快照不可用或访问被拒绝")
        SandboxObservationState.UNSUPPORTED -> text("Unsupported", "不支持")
        SandboxObservationState.IDENTITY_CHANGED -> text("UID changed during sampling", "采样期间 UID 发生变化")
        SandboxObservationState.FAILED -> text("Read failed", "读取失败")
    }

    private fun text(english: String, chinese: String): String = if (this.chinese) chinese else english
}
