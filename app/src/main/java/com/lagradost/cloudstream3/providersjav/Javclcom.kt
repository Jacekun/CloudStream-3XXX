package com.lagradost.cloudstream3.providersjav

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.get
import com.lagradost.cloudstream3.network.text
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
        val url = "$mainUrl/search${query}/"
        val html = get(url).text
        val document = Jsoup.parse(html).getElementsByTag("body")
            .select("div.container > div > div.post-list")
            .select("div.col-md-3.col-sm-6.col-xs-6")
        //Log.i(this.name, "Result => $document")
        return document.map {
            val content = it.select("div.video-item > div > a").firstOrNull()
            //Log.i(this.name, "Result => $content")
            val linkCode = content?.attr("href") ?: ""
            val href = fixUrl(linkCode)
            val imgContent = content?.select("img")
            val title = imgContent?.attr("alt") ?: "<No Title Found>"
            val image = imgContent?.attr("data-original")?.trim('\'')
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
        val response = get(url).text
        val document = Jsoup.parse(response)
        //Log.i(this.name, "Url => ${url}")
        val body = document.getElementsByTag("body")
            .select("div.video-box-ather.container > div.container > div")
            .select("div > div > img")?.firstOrNull()

        //Log.i(this.name, "Result => ${body}")
        val poster = body?.attr("src")
        val title = body?.attr("alt") ?: "<No Title>"
        val descript = "<No Synopsis found>"
        //Log.i(this.name, "Result => ${descript}")
        val id = ""
        val year = null

        return MovieLoadResponse(title, url, this.name, TvType.JAV, id, poster, year, descript, null, null)
    }
}