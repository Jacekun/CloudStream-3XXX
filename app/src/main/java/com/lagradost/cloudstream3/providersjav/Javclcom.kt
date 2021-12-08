package com.lagradost.cloudstream3.providersjav

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import org.jsoup.Jsoup

class Javclcom : MainAPI() {
    override val name: String
        get() = "JAVcl.com"

    override val mainUrl: String
        get() = "https://javcl.com"

    override val supportedTypes: Set<TvType>
        get() = setOf(TvType.JAV)

    override val hasDownloadSupport: Boolean
        get() = false

    override val hasMainPage: Boolean
        get() = false

    override val hasQuickSearch: Boolean
        get() = false


    override fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/${query}/"
        val html = app.get(url).text
        val document = Jsoup.parse(html).getElementsByTag("body")
            .select("div.col-xl-3.col-sm-4.col-6.mb-4")
        //Log.i(this.name, "Result => $document")
        return document!!.map {
            val content = it.select("img.video-thumb").firstOrNull()
            //Log.i(this.name, "Result => $content")

            val href = it.select("a.video-link")?.firstOrNull()?.attr("href") ?: ""
            val title = content?.attr("alt") ?: "<No Title Found>"
            val image = content?.attr("src")
            val year = null
            //Log.i(this.name, "Result => Title: ${title}, Image: ${image}")

            MovieSearchResponse(
                title,
                href,
                this.name,
                TvType.JAV,
                image,
                year
            )
        }
    }

    override fun load(url: String): LoadResponse {
        val response = app.get(url).text
        val doc = Jsoup.parse(response)
        //Log.i(this.name, "Url => ${url}")

        // Video details
        //Log.i(this.name, "Result => ${body}")
        val poster = doc?.select("meta[property=og:image]")?.firstOrNull()?.attr("content")
        val title = doc?.select("meta[property=og:title]")?.firstOrNull()?.attr("content") ?: "<No Title>"
        val descript = doc?.select("meta[property=og:description]")?.firstOrNull()
            ?.attr("content") ?:"<No Synopsis found>"
        //Log.i(this.name, "Result => ${descript}")
        val re = Regex("\\d{4}")
        val year = when (!poster.isNullOrEmpty()) {
            true -> re.find(poster)?.groupValues?.firstOrNull()?.toIntOrNull()
            false -> null
        }

        // Video links
        // WIP: POST https://javcl.me/api/source/${id}
        val id = ""

        return MovieLoadResponse(title, url, this.name, TvType.JAV, id, poster, year, descript, null, null)
    }
}