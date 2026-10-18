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

package com.eltavine.duckdetector.features.networkenvironment.presentation

import com.eltavine.duckdetector.core.evidence.DetectorStatus
import com.eltavine.duckdetector.core.evidence.InfoKind
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkEnvironmentReport
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkEnvironmentStage
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkObservationState
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkPrivateDnsMode
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkProxyMode
import com.eltavine.duckdetector.features.networkenvironment.domain.toDetectorStatus
import com.eltavine.duckdetector.features.networkenvironment.presentation.model.NetworkEnvironmentCardModel
import com.eltavine.duckdetector.features.networkenvironment.presentation.model.NetworkEnvironmentDetailRowModel
import java.util.Locale

/** Localized wording projects typed observations; configuration never becomes a risk finding. */
class NetworkEnvironmentCardModelMapper(locale: Locale = Locale.getDefault()) {
    private val chinese = locale.language == Locale.CHINESE.language
    private val trustStoreRows = NetworkTrustStoreRowMapper(chinese)

    fun map(report: NetworkEnvironmentReport): NetworkEnvironmentCardModel = NetworkEnvironmentCardModel(
        title = text("Network Environment", "网络环境"),
        subtitle = text("VPN · Proxy · Private DNS · CA store", "VPN · 代理 · 私人 DNS · CA 存储"),
        status = report.toDetectorStatus(),
        verdict = verdict(report),
        summary = when (report.stage) {
            NetworkEnvironmentStage.LOADING -> text("Reading public Android connectivity and CA-store APIs.", "正在读取 Android 公开网络与 CA 存储接口。")
            NetworkEnvironmentStage.FAILED -> report.errorMessage ?: text("Collection failed.", "采集失败。")
            NetworkEnvironmentStage.READY -> text(
                "A point-in-time snapshot for this app. VPN, proxies, Private DNS and visible CA entries are contextual configurations and do not raise the risk level. Network endpoints, DNS hostnames, CA aliases and certificate contents are not retained.",
                "这是本应用可见的即时快照。VPN、代理、私人 DNS 与可见 CA 条目均为环境配置信息，不增加风险等级。报告不保留网络端点、DNS 主机名、CA 别名与证书内容。",
            )
        },
        detailsTitle = text("Connectivity observations", "网络配置观察"),
        notesTitle = text("Scope and interpretation", "范围与解释"),
        rows = if (report.stage == NetworkEnvironmentStage.READY) rows(report) else emptyList(),
    )

    private fun verdict(report: NetworkEnvironmentReport): String = when (report.stage) {
        NetworkEnvironmentStage.LOADING -> text("Reading network environment", "正在读取网络环境")
        NetworkEnvironmentStage.FAILED -> text("Collection failed", "采集失败")
        NetworkEnvironmentStage.READY -> when {
            !report.probed -> text("Not evaluated", "未能完成评估")
            report.partiallyEvaluated -> text("Partially observed · information only", "部分可见 · 仅供参考")
            else -> text("Network snapshot · information only", "网络快照 · 仅供参考")
        }
    }

    private fun rows(report: NetworkEnvironmentReport): List<NetworkEnvironmentDetailRowModel> = listOf(
        row(
            label = "VPN",
            state = report.network.vpn.state,
            value = report.network.vpn.usesVpn?.let { usesVpn ->
                if (usesVpn) text("VPN transport reported", "报告 VPN 传输")
                else text("No VPN transport reported", "未报告 VPN 传输")
            },
            scope = text(
                "NetworkCapabilities.TRANSPORT_VPN on this app's default network only; other apps, work profiles and excluded routes may differ.",
                "仅检查本应用默认网络的 TRANSPORT_VPN；其他应用、工作资料及分流路由可能不同。",
            ),
            failureName = report.network.vpn.failureName,
        ),
        row(
            label = text("Default HTTP proxy", "默认 HTTP 代理"),
            state = report.proxy.state,
            value = report.proxy.mode?.let(::proxyLabel),
            scope = text(
                "Android's recommendation: global proxy, then process-bound network, then default network. Apps can ignore it; actual traffic routing is not tested.",
                "Android 推荐配置的优先级为全局代理、进程绑定网络、默认网络。应用可以忽略该配置；未验证实际流量路径。",
            ),
            failureName = report.proxy.failureName,
        ),
        row(
            label = text("Private DNS", "私人 DNS"),
            state = report.network.privateDns.state,
            value = report.network.privateDns.mode?.let(::dnsLabel),
            scope = text(
                "LinkProperties runtime state on the same default network (Android 9+). Not in use does not distinguish Off from Automatic without an active encrypted resolver; app-managed DNS is outside scope.",
                "同一默认网络的 LinkProperties 运行状态（Android 9 及以上）。未使用不能区分“关闭”和暂未启用加密解析的“自动”模式；不检查应用自建 DNS。",
            ),
            failureName = report.network.privateDns.failureName,
        ),
        trustStoreRows.map(report.trustStore),
    )

    private fun row(
        label: String,
        state: NetworkObservationState,
        value: String?,
        scope: String,
        failureName: String?,
    ): NetworkEnvironmentDetailRowModel {
        val availability = availabilityDetail(state)
        return NetworkEnvironmentDetailRowModel(
            label = label,
            value = value ?: stateLabel(state),
            status = DetectorStatus.info(when (state) {
                NetworkObservationState.OBSERVED -> InfoKind.CONTEXT
                NetworkObservationState.FAILED -> InfoKind.ERROR
                else -> InfoKind.SUPPORT
            }),
            detail = listOfNotNull(availability, failureName, scope).joinToString(" "),
        )
    }

    private fun stateLabel(state: NetworkObservationState): String = when (state) {
        NetworkObservationState.NOT_EVALUATED -> text("Not evaluated", "未评估")
        NetworkObservationState.OBSERVED -> text("Observed", "已观察")
        NetworkObservationState.NO_DEFAULT_NETWORK -> text("No accessible default network", "无可访问的默认网络")
        NetworkObservationState.PERMISSION_REQUIRED -> text("Permission required", "缺少权限")
        NetworkObservationState.UNAVAILABLE -> text("Unavailable", "无法读取")
        NetworkObservationState.UNSUPPORTED -> text("Unsupported", "不支持")
        NetworkObservationState.NETWORK_CHANGED -> text("Network changed during scan", "扫描期间网络发生变化")
        NetworkObservationState.FAILED -> text("Read failed", "读取失败")
    }

    private fun availabilityDetail(state: NetworkObservationState): String? = when (state) {
        NetworkObservationState.NO_DEFAULT_NETWORK -> text(
            "Android returned no default network, which also happens when that network is blocked for this app. This is not an inactive-setting result.",
            "Android 未返回默认网络；网络对本应用被阻止时也会如此。此结果不代表相关配置已关闭。",
        )
        NetworkObservationState.PERMISSION_REQUIRED -> text(
            "The platform denied this read. Network-state observations require ACCESS_NETWORK_STATE.",
            "平台拒绝读取。网络状态采集需要 ACCESS_NETWORK_STATE 权限。",
        )
        NetworkObservationState.UNAVAILABLE -> text("The service or network metadata was unavailable.", "网络服务或元数据不可用。")
        NetworkObservationState.UNSUPPORTED -> text("The required public API is unavailable on this Android version.", "当前 Android 版本不提供所需公开接口。")
        NetworkObservationState.NETWORK_CHANGED -> text("The default-network handle changed; scan again for a fresh snapshot.", "默认网络句柄发生变化；请重新扫描以获取快照。")
        NetworkObservationState.FAILED -> text("The source threw an exception; its state is unknown.", "读取源抛出异常，状态未知。")
        NetworkObservationState.NOT_EVALUATED -> text("This source has not been read.", "尚未读取此来源。")
        NetworkObservationState.OBSERVED -> null
    }

    private fun proxyLabel(mode: NetworkProxyMode): String = when (mode) {
        NetworkProxyMode.NONE -> text("No recommended proxy", "未推荐代理")
        NetworkProxyMode.MANUAL -> text("Manual proxy configured", "已配置手动代理")
        NetworkProxyMode.AUTOMATIC -> text("Automatic proxy (PAC) configured", "已配置自动代理（PAC）")
    }

    private fun dnsLabel(mode: NetworkPrivateDnsMode): String = when (mode) {
        NetworkPrivateDnsMode.INACTIVE -> text("Not in use on this network", "此网络当前未使用")
        NetworkPrivateDnsMode.ACTIVE_OPPORTUNISTIC -> text("In use · opportunistic mode", "正在使用 · 自动协商模式")
        NetworkPrivateDnsMode.ACTIVE_STRICT -> text("In use · strict hostname mode", "正在使用 · 严格主机名模式")
        NetworkPrivateDnsMode.STRICT_NOT_ACTIVE -> text("Strict mode reported · not active", "报告严格模式 · 当前未启用")
    }

    private fun text(english: String, chinese: String): String = if (this.chinese) chinese else english
}
