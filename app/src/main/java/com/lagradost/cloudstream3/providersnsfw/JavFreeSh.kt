package com.lagradost.cloudstream3.providersnsfw

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup

class JavFreeSh : MainAPI() {
    override var name = "JavFree"
    override var mainUrl = "https://javfree.sh"
    override val supportedTypes: Set<TvType> get() = setOf(TvType.JAV)
    override val hasDownloadSupport: Boolean get() = false
    override val hasMainPage: Boolean get() = true
    override val hasQuickSearch: Boolean get() = false

    private data class ResponseJson(
        @JsonProperty("list") val list: List<ResponseData>?
    )
    private data class ResponseData(
        @JsonProperty("url") val file: String?,
        @JsonProperty("server") val server: String?,
        @JsonProperty("active") val active: Int?
    )

    fun String.cleanText() : String = this.trim().removePrefix("Watch JAV Free").removeSuffix("HD Free Online on JAVFree.SH").trim()

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val html = app.get(mainUrl).text
        val document = Jsoup.parse(html)
        val all = ArrayList<HomePageList>()

        val mainbody = document.getElementsByTag("body").select("div#page")
            .select("div#content").select("div#primary")
            .select("main")

        mainbody.select("section").forEach { it2 ->
            // Fetch row title
            val title = it2?.select("h2.widget-title")?.text() ?: "Unnamed Row"
            // Fetch list of items and map
            it2.select("div.videos-list")
                .select("article").let { inner ->

                val elements: List<SearchResponse> = inner.map {

                    val aa = it.select("a").firstOrNull()
                    val link = fixUrl(aa?.attr("href") ?: "")
                    val name = aa?.attr("title") ?: "<No Title>"

                    var image = aa?.select("div")?.select("img")?.attr("data-src") ?: ""
                    if (image == "") {
                        image = aa?.select("div")?.select("video")?.attr("poster") ?: ""
                    }
                    val year = null

                    MovieSearchResponse(
                        name = name,
                        url = link,
                        apiName = this.name,
                        type = TvType.JAV,
                        posterUrl = image,
                        year = year
                    )
                }

                all.add(
                    HomePageList(
                        name = title,
                        list = elements,
                        isHorizontalImages = true
                    )
                )
            }
        }
        return HomePageResponse(all)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/movie/${query}"
        val html = app.get(url).text
        val document = Jsoup.parse(html).select("div.videos-list").select("article[id^=post]")

        return document.map {
            val aa = it.select("a")
            val title = aa.attr("title")
            val href = fixUrl(aa.attr("href"))
            val year = null
            val image = aa.select("div.post-thumbnail.thumbs-rotation")
                .select("img").attr("data-src")

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

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        //Log.i(this.name, "Result => (url) ${url}")
        val poster = doc.select("meta[property=og:image]").firstOrNull()?.attr("content")
        val title = doc.select("meta[name=title]").firstOrNull()?.attr("content")?.toString()?.cleanText() ?: ""
        val descript = doc.select("meta[name=description]").firstOrNull()?.attr("content")?.cleanText()

        val body = doc.getElementsByTag("body")
        val yearElem = body
            ?.select("div#page > div#content > div#primary > main > article")
            ?.select("div.entry-content > div.tab-content > div#video-about > div#video-date")
        //Log.i(this.name, "Result => (yearElem) ${yearElem}")
        val year = yearElem?.text()?.trim()?.takeLast(4)?.toIntOrNull()

        var streamUrl = body
            ?.select("div#page > div#content > div#primary > main > article > header > div > div > div > script")
            ?.toString() ?: ""
        if (streamUrl.isNotEmpty()) {
            val startS = "<iframe src="
            streamUrl = streamUrl.substring(streamUrl.indexOf(startS) + startS.length + 1)
            //Log.i(this.name, "Result => (id) ${id}")
            streamUrl = streamUrl.substring(0, streamUrl.indexOf("\""))
        }
        //Log.i(this.name, "Result => (id) ${id}")
        return MovieLoadResponse(
            name = title,
            url = url,
            apiName = this.name,
            type = TvType.JAV,
            dataUrl = streamUrl,
            posterUrl = poster,
            year = year,
            plot = descript
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        var success = false
        try {
            // GET request to: https://player.javfree.sh/stream/687234424271726c
            val id = data.substring(data.indexOf("#")).substring(1)
            val linkToGet = "https://player.javfree.sh/stream/$id"
            val jsonres = app.get(linkToGet, referer = mainUrl).text
            val referer = "https://player.javfree.sh/embed.html"
            //Log.i(this.name, "Result => (jsonres) ${jsonres}")
            tryParseJson<ResponseJson?>(jsonres)?.let { item ->
                item?.list?.forEach { link ->
                    val linkUrl = link.file ?: ""
                    if (linkUrl.isNotBlank()) {
                        //Log.i(this.name, "ApiError => (link url) $linkUrl")
                        success = loadExtractor(
                            url= linkUrl,
                            referer = referer,
                            subtitleCallback = subtitleCallback,
                            callback = callback
                        )
                    }
                }
                return success
            }
        } catch (e: Exception) {
            e.printStackTrace()
            logError(e)
        }
        return false
    }
}