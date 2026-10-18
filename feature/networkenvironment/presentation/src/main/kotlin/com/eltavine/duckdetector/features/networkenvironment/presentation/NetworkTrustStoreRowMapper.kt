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
import com.eltavine.duckdetector.features.networkenvironment.domain.NetworkTrustStoreObservation
import com.eltavine.duckdetector.features.networkenvironment.domain.TrustStoreObservationState
import com.eltavine.duckdetector.features.networkenvironment.presentation.model.NetworkEnvironmentDetailRowModel

internal class NetworkTrustStoreRowMapper(private val chinese: Boolean) {
    fun map(observation: NetworkTrustStoreObservation): NetworkEnvironmentDetailRowModel {
        val state = observation.state
        return NetworkEnvironmentDetailRowModel(
            label = text("Visible CA entries", "可见 CA 条目"),
            value = value(observation),
            status = DetectorStatus.info(when (state) {
                TrustStoreObservationState.OBSERVED -> InfoKind.CONTEXT
                TrustStoreObservationState.FAILED -> InfoKind.ERROR
                else -> InfoKind.SUPPORT
            }),
            detail = listOfNotNull(
                availability(state),
                observation.failureName,
                text(
                    "AndroidCAStore entries visible to this app's user/profile only. A zero count means none were visible; provider enumeration cannot prove that an inaccessible store is empty. User-added CAs can be legitimate enterprise, development or personal configuration.",
                    "仅统计 AndroidCAStore 对本应用当前用户或工作资料可见的条目。零表示未观察到；提供程序枚举不能证明无法访问的存储为空。用户 CA 可用于企业管理、开发或个人配置。",
                ),
                text(
                    "Apps targeting Android 7+ default to system CAs and can customize trust per app/domain. These counts do not establish this app's actual TLS trust, certificate validity or traffic interception. No aliases or certificate contents are retained.",
                    "面向 Android 7 及以上的应用默认仅信任系统 CA，也可按应用或域名自定义信任。这些计数不能确定本应用实际 TLS 信任、证书有效性或流量拦截。报告不保留别名或证书内容。",
                ),
            ).joinToString(" "),
        )
    }

    private fun value(observation: NetworkTrustStoreObservation): String {
        val counts = observation.counts ?: return when (observation.state) {
            TrustStoreObservationState.NOT_EVALUATED -> text("Not evaluated", "未评估")
            TrustStoreObservationState.PROVIDER_UNAVAILABLE -> text("AndroidCAStore provider unavailable", "AndroidCAStore 提供程序不可用")
            TrustStoreObservationState.PERMISSION_REQUIRED -> text("CA-store access denied", "CA 存储访问被拒绝")
            TrustStoreObservationState.FAILED -> text("CA-store read failed", "CA 存储读取失败")
            else -> error("Observed CA entries require counts")
        }
        val prefix = if (observation.state == TrustStoreObservationState.OBSERVED) ""
        else text("Partial counts · ", "部分计数 · ")
        val known = text("User-added: ${counts.userAdded} · System: ${counts.system}", "用户添加：${counts.userAdded} · 系统：${counts.system}")
        val unknown = if (counts.unrecognized == 0) ""
        else text(" · Unclassified: ${counts.unrecognized}", " · 未分类：${counts.unrecognized}")
        return prefix + known + unknown
    }

    private fun availability(state: TrustStoreObservationState): String? = when (state) {
        TrustStoreObservationState.NOT_EVALUATED -> text("This source has not been read.", "尚未读取此来源。")
        TrustStoreObservationState.PROVIDER_UNAVAILABLE -> text("The required KeyStore provider is unavailable; no counts are inferred.", "所需 KeyStore 提供程序不可用，不推断条目数量。")
        TrustStoreObservationState.PERMISSION_REQUIRED -> text("The platform denied this read; no counts are inferred.", "平台拒绝读取，不推断条目数量。")
        TrustStoreObservationState.TRUNCATED -> text("The 4,096-entry processing limit was reached. Counts are a partial sample, not totals.", "已达到 4,096 条应用处理上限。计数为部分样本，不是总数。")
        TrustStoreObservationState.UNRECOGNIZED_ALIASES -> text("Some entries have an unrecognized or malformed alias namespace and could not be classified.", "部分条目的别名命名空间未知或格式异常，无法分类。")
        TrustStoreObservationState.FAILED -> text("The provider threw an exception; counts are unknown.", "提供程序抛出异常，条目数量未知。")
        TrustStoreObservationState.OBSERVED -> null
    }

    private fun text(english: String, chinese: String): String = if (this.chinese) chinese else english
}
