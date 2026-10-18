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
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GitHubReleaseManifestClientTest {
    @Test
    fun `discovers preview manifests and skips drafts`() {
        val urls = mutableListOf<String>()
        val manifest = previewManifest("preview-12")
        val client = GitHubReleaseManifestClient(UpdateHttpClient { url, _ ->
            urls += url
            if (url == UpdateRepository.MANIFEST_URL) {
                JSONArray().put(release("preview-13", draft = true))
                    .put(release("preview-12")).toString()
            } else manifest
        }, channel = ReleaseUpdateChannel.PREVIEW)
        assertEquals(manifest, client.get(UpdateRepository.MANIFEST_URL, "application/json"))
        assertEquals(listOf(UpdateRepository.MANIFEST_URL, manifestUrl("preview-12")), urls)
    }

    @Test
    fun `rejects a manifest asset hosted outside the project`() {
        val release = release("preview-12")
        release.getJSONArray("assets").getJSONObject(0)
            .put("browser_download_url", "https://example.com/update.json")
        val client = GitHubReleaseManifestClient(
            UpdateHttpClient { _, _ -> JSONArray().put(release).toString() },
            channel = ReleaseUpdateChannel.PREVIEW,
        )
        assertThrows(IOException::class.java) { client.get(UpdateRepository.MANIFEST_URL, "application/json") }
    }

    @Test
    fun `rejects a different release APK even from the same repository`() {
        val client = GitHubReleaseManifestClient(UpdateHttpClient { url, _ ->
            if (url == UpdateRepository.MANIFEST_URL) JSONArray().put(release("preview-12")).toString()
            else previewManifest("preview-11")
        }, channel = ReleaseUpdateChannel.PREVIEW)
        assertThrows(IOException::class.java) { client.get(UpdateRepository.MANIFEST_URL, "application/json") }
    }

    @Test
    fun `stable channel never selects a preview APK`() {
        var assetRequests = 0
        val client = GitHubReleaseManifestClient(UpdateHttpClient { url, _ ->
            if (url == UpdateRepository.MANIFEST_URL) JSONArray().put(release("preview-12")).toString()
            else { assetRequests += 1; previewManifest("preview-12") }
        }, channel = ReleaseUpdateChannel.STABLE)
        assertThrows(IOException::class.java) { client.get(UpdateRepository.MANIFEST_URL, "application/json") }
        assertEquals(0, assetRequests)
    }

    private fun previewManifest(tag: String): String = JSONObject(validUpdateManifestJson())
        .put("channel", "preview")
        .also { root -> root.getJSONObject("apk").put("downloadUrl", directory(tag) + "NS.Detector-test.apk") }
        .toString()

    private fun release(tag: String, draft: Boolean = false): JSONObject = JSONObject()
        .put("tag_name", tag).put("draft", draft).put("prerelease", true)
        .put("assets", JSONArray().put(JSONObject().put("name", "update.json")
            .put("browser_download_url", manifestUrl(tag))))

    private fun manifestUrl(tag: String) = directory(tag) + "update.json"
    private fun directory(tag: String) = "https://github.com/withite/ns-detector/releases/download/$tag/"
}
