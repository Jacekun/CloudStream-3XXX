package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getQualityFromName

open class Mcloud : ExtractorApi() {
    override var name = "Mcloud"
    override var mainUrl = "https://mcloud.to"
    override val requiresReferer = true
    val headers = mapOf(
        "Host" to "mcloud.to",
        "User-Agent" to USER_AGENT,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.5",
        "DNT" to "1",
        "Connection" to "keep-alive",
        "Upgrade-Insecure-Requests" to "1",
        "Sec-Fetch-Dest" to "iframe",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Site" to "cross-site",
        "Referer" to "https://animekisa.in/", //Referer works for wco and animekisa, probably with others too
        "Pragma" to "no-cache",
        "Cache-Control" to "no-cache",)
    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val link = url.replace("$mainUrl/e/","$mainUrl/info/")
        val response = app.get(link, headers = headers).text

        if(response.startsWith("<!DOCTYPE html>")) {
            // TODO decrypt html for link
            return emptyList()
        }

        data class Sources (
            @JsonProperty("file") val file: String
        )

        data class Media (
            @JsonProperty("sources") val sources: List<Sources>
        )

        data class JsonMcloud (
            @JsonProperty("success") val success: Boolean,
            @JsonProperty("media") val media: Media,
        )

        val mapped = parseJson<JsonMcloud>(response)
        val sources = mutableListOf<ExtractorLink>()

        if (mapped.success)
            mapped.media.sources.apmap {
                if (it.file.contains("m3u8")) {
                    M3u8Helper().m3u8Generation(
                        M3u8Helper.M3u8Stream(
                            it.file,
                            headers = app.get(url).headers.toMap()
                        ), true
                    )
                        .map { stream ->
                            sources.add(
                                ExtractorLink(
                                    name,
                                    name = name,
                                    stream.streamUrl,
                                    url,
                                    getQualityFromName(stream.quality?.toString()),
                                    true
                                )
                            )
                        }
                }
            }
        return sources
    }
}
