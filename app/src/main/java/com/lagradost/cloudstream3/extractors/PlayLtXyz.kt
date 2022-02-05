package com.lagradost.cloudstream3.extractors

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.utils.*

class PlayLtXyz: ExtractorApi() {
    override val name: String = "PlayLt"
    override val mainUrl: String = "https://play.playlt.xyz"
    override val requiresReferer = true

    private data class ResponseData(
        @JsonProperty("data") val data: String?
    )

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val extractedLinksList: MutableList<ExtractorLink> = mutableListOf()
        val sess = HttpSession()
        val id = url.trim().split("/").last()
        val ajaxHead = mapOf(
            Pair("Origin", "https://play.playlt.xyz"),
            Pair("Referer", "https://play.playlt.xyz")
        )
        val ajaxData = mapOf(
            Pair("referrer", referer),
            Pair("typeend", "html")
        )
        val data = sess.post("https://api-plhq.playlt.xyz/apiv5/608f7c85cf0743547f1f1b4e/$id", headers = ajaxHead, data = ajaxData)
        //Log.i(this.name, "Result => (url, id, req) ${url} -> ${id} -> ${req}")
        if (data.statusCode == 200) {
            val itemstr = data.text
            Log.i(this.name, "Result => (data) $itemstr")
            mapper.readValue<ResponseData>(itemstr).let { item ->
                val linkUrl = item.data ?: ""
                if (linkUrl.isNotEmpty()) {
                    extractedLinksList.add(
                        ExtractorLink(
                            source = name,
                            name = name,
                            url = linkUrl,
                            referer = url,
                            quality = Qualities.Unknown.value,
                            isM3u8 = true
                        )
                    )
                }
            }
        }
        return extractedLinksList
    }
}