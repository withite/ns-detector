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

package com.eltavine.duckdetector.features.appintegrity.presentation

import com.eltavine.duckdetector.core.evidence.DetectorStatus
import com.eltavine.duckdetector.core.evidence.InfoKind
import com.eltavine.duckdetector.features.appintegrity.domain.AppIntegrityReport
import com.eltavine.duckdetector.features.appintegrity.domain.AppIntegrityStage
import com.eltavine.duckdetector.features.appintegrity.domain.AppObservation
import com.eltavine.duckdetector.features.appintegrity.domain.AppObservationState
import com.eltavine.duckdetector.features.appintegrity.domain.AppSignatureAssessment
import com.eltavine.duckdetector.features.appintegrity.domain.isFailure
import com.eltavine.duckdetector.features.appintegrity.domain.signatureAssessment
import com.eltavine.duckdetector.features.appintegrity.domain.toDetectorStatus
import com.eltavine.duckdetector.features.appintegrity.presentation.model.AppIntegrityCardModel
import com.eltavine.duckdetector.features.appintegrity.presentation.model.AppIntegrityDetailRowModel
import java.util.Locale

/** Projects local observations; a matching pin is never presented as device trust. */
class AppIntegrityCardModelMapper(locale: Locale = Locale.getDefault()) {
    private val chinese = locale.language == Locale.CHINESE.language

    fun map(report: AppIntegrityReport): AppIntegrityCardModel = AppIntegrityCardModel(
        title = text("Application Integrity", "应用完整性"),
        subtitle = text("Current signer · build flags · self-process debugging", "当前签名 · 构建标记 · 本进程调试"),
        status = report.toDetectorStatus(),
        verdict = when (report.stage) {
            AppIntegrityStage.LOADING -> text("Reading application diagnostics", "正在读取应用诊断信息")
            AppIntegrityStage.FAILED -> text("Collection failed", "采集失败")
            AppIntegrityStage.READY -> assessmentLabel(report.signatureAssessment())
        },
        summary = when (report.stage) {
            AppIntegrityStage.LOADING -> text("Reading the host application only.", "仅检查当前宿主应用。")
            AppIntegrityStage.FAILED -> report.errorMessage ?: text("Collection failed.", "采集失败。")
            AppIntegrityStage.READY -> text(
                "Local, point-in-time diagnostics for this app. A matching certificate or absent debugger cannot prove device safety or absence of hooks. Debuggers can be legitimate; this check cannot establish malicious intent. A modified runtime can alter these observations or the bundled policy.",
                "仅反映本应用当前可见状态。证书匹配或未见调试器不能证明设备安全、没有 Hook。调试可能是正常行为，此检查无法判断恶意意图；被修改的运行时可以改变观察结果或内置策略。",
            )
        },
        signatureTitle = text("Signing policy", "签名策略"),
        runtimeTitle = text("Application runtime", "应用运行状态"),
        signalRows = if (report.stage == AppIntegrityStage.READY) signingRows(report) else emptyList(),
        scanRows = if (report.stage == AppIntegrityStage.READY) runtimeRows(report) else emptyList(),
    )

    private fun signingRows(report: AppIntegrityReport): List<AppIntegrityDetailRowModel> = listOf(
        AppIntegrityDetailRowModel(
            label = text("Current certificate policy", "当前证书策略比对"),
            value = assessmentLabel(report.signatureAssessment()),
            status = report.signatureAssessment().toDetectorStatus(),
            detail = text(
                "Host-supplied ns_signing_policy.json; exact package scope, current single signer only. Rotation requires explicitly adding the new current certificate pin. Historical pins and multi-signer APKs are not accepted by this policy.",
                "使用宿主提供的 ns_signing_policy.json；精确限定包名，仅比较当前单签名证书。轮换须主动加入新当前证书指纹；本策略不以旧历史证书命中作为信任依据，不支持多签名 APK 比对。",
            ),
        ),
        row(
            text("Current signer SHA-256", "当前签名证书 SHA-256"),
            report.signing,
            report.signing.value?.currentCertificateSha256?.sorted()?.joinToString("\n"),
            text("SHA-256 of the certificate DER bytes from PackageManager; not an APK file hash or an independent signature verification.",
                "对 PackageManager 返回的证书 DER 字节计算 SHA-256；不是 APK 文件散列，也不是独立的 APK 签名验证。"),
        ),
        row(
            text("Policy package", "策略适用包名"), report.policy, report.policy.value?.allowedPackage,
            text("The policy is supplied by this host and is not a remotely authenticated trust anchor.",
                "策略由当前宿主提供，不是经远程认证的可信根。"),
        ),
    )

    private fun runtimeRows(report: AppIntegrityReport): List<AppIntegrityDetailRowModel> {
        val build = report.build.value
        val debugger = report.debugger.value
        return listOf(
            row(text("Host package", "当前宿主包名"), report.build, build?.packageName),
            row(text("Debuggable build", "可调试构建"), report.build, build?.debuggable?.let(::yesNo),
                text("A build flag; development builds do not trigger release-signing mismatch warnings.",
                    "构建标记；开发构建不会因正式签名不匹配触发警告。")),
            row(text("Test-only build", "仅供测试构建"), report.build, build?.testOnly?.let(::yesNo)),
            row(text("Java debugger attached", "Java 调试器已连接"), report.debugger, debugger?.attached?.let(::yesNo)),
            row(text("Waiting for debugger", "正在等待调试器"), report.debugger, debugger?.waiting?.let(::yesNo)),
            row(text("Self-process TracerPid", "本进程 TracerPid"), report.tracerPid, report.tracerPid.value?.toString(),
                text("Only /proc/self/status is read. A nonzero PID is a ptrace observation in this PID namespace and may be legitimate; zero does not prove absence of hooks, Java debugging or tracers on other threads.",
                    "仅读取 /proc/self/status。非零 PID 表示此 PID 命名空间中观察到 ptrace 跟踪者，也可能来自正常调试；零不能证明没有 Hook、Java 调试或其他线程的跟踪者。")),
            row(text("Rotation history entries", "证书轮换历史条目"), report.signing,
                report.signing.value?.let { if (it.multipleSigners) text("Not applicable · multiple signers", "不适用 · 多签名") else it.rotationHistoryCount.toString() }),
        )
    }

    private fun row(
        label: String,
        observation: AppObservation<*>,
        value: String?,
        detail: String? = null,
    ): AppIntegrityDetailRowModel = AppIntegrityDetailRowModel(
        label = label,
        value = value ?: stateLabel(observation.state),
        status = DetectorStatus.info(when {
            observation.state.isFailure -> InfoKind.ERROR
            observation.state == AppObservationState.OBSERVED -> InfoKind.CONTEXT
            else -> InfoKind.SUPPORT
        }),
        detail = listOfNotNull(observation.failureName, detail).joinToString(" ").ifBlank { null },
    )

    private fun stateLabel(state: AppObservationState): String = when (state) {
        AppObservationState.NOT_EVALUATED -> text("Not evaluated", "未评估")
        AppObservationState.OBSERVED -> text("Observed", "已观察")
        AppObservationState.MISSING -> text("No host policy supplied", "宿主未提供策略")
        AppObservationState.UNAVAILABLE -> text("Unavailable · unknown", "无法读取 · 状态未知")
        AppObservationState.UNSUPPORTED -> text("Unsupported", "不支持")
        AppObservationState.MALFORMED -> text("Malformed source · unknown", "来源格式错误 · 状态未知")
        AppObservationState.FAILED -> text("Read failed · unknown", "读取失败 · 状态未知")
    }

    private fun assessmentLabel(value: AppSignatureAssessment): String = when (value) {
        AppSignatureAssessment.NOT_EVALUATED -> text("Not evaluated", "未评估")
        AppSignatureAssessment.SOURCE_UNAVAILABLE -> text("Signing assessment unavailable", "签名比对不可用")
        AppSignatureAssessment.POLICY_MISSING -> text("No host signing policy · not evaluated", "无宿主签名策略 · 未评估")
        AppSignatureAssessment.POLICY_NOT_APPLICABLE -> text("Policy does not apply to this package", "策略不适用于当前包名")
        AppSignatureAssessment.MULTIPLE_SIGNERS_UNSUPPORTED -> text("Multiple-signer comparison unsupported", "不支持多签名策略比对")
        AppSignatureAssessment.DEVELOPMENT_BUILD -> text("Development/test build · information only", "开发或测试构建 · 仅供参考")
        AppSignatureAssessment.MATCHED -> text("Current certificate matches host policy", "当前证书符合宿主策略")
        AppSignatureAssessment.MISMATCHED -> text("Current certificate differs from host policy", "当前证书与宿主策略不符")
        AppSignatureAssessment.ERROR -> text("Signing assessment failed · unknown", "签名比对失败 · 状态未知")
    }

    private fun yesNo(value: Boolean): String = if (value) text("Yes", "是") else text("No", "否")
    private fun text(english: String, chinese: String): String = if (this.chinese) chinese else english
}
