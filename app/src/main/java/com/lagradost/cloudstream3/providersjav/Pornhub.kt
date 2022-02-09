package com.lagradost.cloudstream3.providersjav

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.*
import java.util.*

class Pornhub:MainAPI() {
    override val mainUrl = "https://www.pornhub.com"
    override val name = "Pornhub"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val vpnStatus = VPNStatus.MightBeNeeded //Cause it's a big site
    override val supportedTypes = setOf(
        TvType.JAV,
    )
    override suspend fun getMainPage(): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val urls = listOf(
            Pair(mainUrl, "Trending Now"),
            Pair("$mainUrl/video?o=tr", "Best rated this month"),
            Pair("$mainUrl/video?c=111", "Japanese"),
            Pair("$mainUrl/categories/hentai", "Hentai"),
        )
        for (i in urls) {
            try {
                val soup = app.get(i.first).document
                val home = soup.select("div.sectionWrapper div.wrap").map {
                    val title = it.selectFirst("span.title a").text()
                    val link = it.selectFirst("a").attr("href")
                    TvSeriesSearchResponse(
                        title,
                        fixUrl(link),
                        this.name,
                        TvType.Movie,
                        it.selectFirst("img").attr("data-src"),
                        null,
                        null,
                    )
                }

                items.add(HomePageList(i.second, home))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/video/search?search=${query}"
        val document = app.get(url).document
        return document.select("div.sectionWrapper div.wrap").map {
            val title = it.selectFirst("span.title a").text()
            val href = it.selectFirst("a").attr("href")
            val image = it.selectFirst("img").attr("data-src")
            MovieSearchResponse(
                title,
                fixUrl(href),
                this.name,
                TvType.Movie,
                image,
                null
            )

        }.toList()
    }
    override suspend fun load(url: String): LoadResponse? {
        val soup = app.get(url, timeout = 120).document
        val title = soup.selectFirst(".title span").text()
        val description = title ?: null
        val poster: String? = soup.selectFirst("div.video-wrapper .mainPlayerDiv img").attr("src") ?:
            soup.selectFirst("head meta[property=og:image]").attr("content")
        val tags = soup.select("div.categoriesWrapper a")
            .map { it?.text()?.trim().toString().replace(", ","") }
        return MovieLoadResponse(
            title,
            url,
            this.name,
            TvType.Movie,
            url,
            poster,
            null,
            description,
            null,
            null,
            tags
        )
    }
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
       val response = app.get(data, interceptor = WebViewResolver(
           Regex("(master\\.m3u8\\?.*)")
       )
       )
        M3u8Helper().m3u8Generation(
            M3u8Helper.M3u8Stream(
                response.url,
                headers = response.headers.toMap()
            ), true
        )
            .map { stream ->
                val qualityString = if ((stream.quality ?: 0) == 0) "" else "${stream.quality}p"
                callback(ExtractorLink(
                    name,
                    "$name $qualityString",
                    stream.streamUrl,
                    mainUrl,
                    getQualityFromName(stream.quality.toString()),
                    true
                ))
            }

        return true
    }
}