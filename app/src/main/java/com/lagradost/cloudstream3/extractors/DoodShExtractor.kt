package com.lagradost.cloudstream3.extractors

import android.util.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import java.lang.Exception
import java.lang.Thread.sleep

open class DoodShExtractor : ExtractorApi() {
    override val name = "DoodStreamSh"
    override val mainUrl = "https://dood.sh"
    override val requiresReferer = false

    override fun getExtractorUrl(id: String): String {
        return "$mainUrl/e/$id"
    }

    override fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        try {
            val doc = app.get(url, referer = url).text
            var md5 = doc.substring(doc.indexOf("\$.get('/pass_md5"))
            //Log.i(this.name, "Result => (md5) $md5")
            val start = md5.indexOf("('")
            if (start > -1) {
                md5 = md5.substring(start + 2, md5.indexOf("',"))
                Log.i(this.name, "Result => (md5 substring) $md5")
                val src = app.get("$mainUrl$md5", referer = url).text
                Log.i(this.name, "Result => (src) $src")
                val trueLink = src
                return listOf(
                    ExtractorLink(
                        trueLink,
                        this.name,
                        trueLink,
                        mainUrl,
                        Qualities.Unknown.value,
                        true
                    )
                )
            }
        } catch (e: Exception) {
            Log.i(this.name, "Result => (getUrl) $e")
        }
        return listOf()
    }
}