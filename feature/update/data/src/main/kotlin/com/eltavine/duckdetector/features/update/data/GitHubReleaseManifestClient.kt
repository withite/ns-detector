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

package com.eltavine.duckdetector.features.update.data

import java.io.IOException
import java.net.URI
import org.json.JSONArray
import org.json.JSONObject

/** Resolves immutable release assets, including prereleases which GitHub /latest omits. */
internal enum class ReleaseUpdateChannel(val manifestValue: String, val prerelease: Boolean) {
    STABLE("stable", false),
    PREVIEW("preview", true),
}

internal class GitHubReleaseManifestClient(
    private val transport: UpdateHttpClient = HttpUrlConnectionUpdateClient(),
    private val channel: ReleaseUpdateChannel = ReleaseUpdateChannel.STABLE,
) : UpdateHttpClient {
    override fun get(url: String, accept: String): String {
        val directFeed = UpdateRepository.MANIFEST_URL
        val proxyFeed = "https://gh-proxy.com/$directFeed"
        if (url != directFeed && url != proxyFeed) return transport.get(url, accept)
        val prefix = if (url == proxyFeed) "https://gh-proxy.com/" else ""
        val releases = if (channel == ReleaseUpdateChannel.STABLE) {
            // /latest excludes prereleases and cannot lose stable builds behind a busy preview feed.
            JSONArray().put(JSONObject(transport.get(prefix + STABLE_RELEASE_URL, "application/vnd.github+json")))
        } else {
            JSONArray(transport.get(url, "application/vnd.github+json"))
        }
        val release = (0 until releases.length()).asSequence()
            .map { releases.getJSONObject(it) }
            .filter { !it.optBoolean("draft", true) }
            .filter { it.optBoolean("prerelease", false) == channel.prerelease }
            .mapNotNull(::releaseAsset)
            .firstOrNull() ?: throw IOException("No published NS Detector update manifest is available.")
        val manifest = transport.get(prefix + release.manifestUrl, "application/json")
        // The APK and its manifest must belong to the same versioned release.
        val parsed = UpdateManifestParser().parse(manifest)
        if (parsed.channel != channel.manifestValue ||
            !parsed.apk.downloadUrl.startsWith(release.directoryUrl)
        ) {
            throw IOException("APK and manifest do not match the selected release channel.")
        }
        return manifest
    }

    private fun releaseAsset(release: JSONObject): PublishedManifest? {
        val tag = release.optString("tag_name")
        if (!SAFE_TAG.matches(tag)) return null
        val directory = "https://github.com/withite/ns-detector/releases/download/$tag/"
        val assets = release.optJSONArray("assets") ?: return null
        for (index in 0 until assets.length()) {
            val asset = assets.getJSONObject(index)
            if (asset.optString("name") != "update.json") continue
            val candidate = asset.optString("browser_download_url")
            val uri = runCatching { URI(candidate) }.getOrNull() ?: continue
            if (candidate != directory + "update.json" || uri.rawUserInfo != null) {
                throw IOException("Update manifest is outside the NS Detector release.")
            }
            return PublishedManifest(candidate, directory)
        }
        return null
    }

    private data class PublishedManifest(val manifestUrl: String, val directoryUrl: String)

    private companion object {
        private const val STABLE_RELEASE_URL = "https://api.github.com/repos/withite/ns-detector/releases/latest"
        private val SAFE_TAG = Regex("^[A-Za-z0-9._-]{1,100}$")
    }
}
