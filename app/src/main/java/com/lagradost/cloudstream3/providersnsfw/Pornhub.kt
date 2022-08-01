package com.lagradost.cloudstream3.providersnsfw

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class Pornhub : MainAPI() {
    override var mainUrl = "https://www.pornhub.com"
    override var name = "Pornhub"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val vpnStatus = VPNStatus.MightBeNeeded //Cause it's a big site
    override val supportedTypes = setOf(TvType.XXX, TvType.JAV, TvType.Hentai)

    override val mainPage = mainPageOf(
        "$mainUrl/video?page=" to "Main Page",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        try {
            val categoryData = request.data
            val categoryName = request.name
            val pagedLink = if (page > 0) categoryData + page else categoryData
            val soup = app.get(pagedLink).document
            val home = soup.select("div.sectionWrapper div.wrap").mapNotNull {
                if (it == null) { return@mapNotNull null }
                val title = it.selectFirst("span.title a")?.text() ?: ""
                val link = fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
                val img = fetchImgUrl(it.selectFirst("img"))
                MovieSearchResponse(
                    name = title,
                    url = link,
                    apiName = this.name,
                    type = TvType.XXX,
                    posterUrl = img
                )
            }
            if (home.isNotEmpty()) {
                return newHomePageResponse(
                    list = HomePageList(
                        name = categoryName,
                        list = home,
                        isHorizontalImages = true
                    ),
                    hasNext = true
                )
            } else {
                throw ErrorLoadingException("No homepage data found!")
            }
        } catch (e: Exception) {
            //e.printStackTrace()
            logError(e)
        }
        throw ErrorLoadingException()
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/video/search?search=${query}"
        val document = app.get(url).document
        return document.select("div.sectionWrapper div.wrap").mapNotNull {
            if (it == null) { return@mapNotNull null }
            val title = it.selectFirst("span.title a")?.text() ?: return@mapNotNull null
            val link = fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
            val image = fetchImgUrl(it.selectFirst("img"))
            MovieSearchResponse(
                name = title,
                url = link,
                apiName = this.name,
                type = TvType.XXX,
                posterUrl = image
            )
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val soup = app.get(url).document
        val title = soup.selectFirst(".title span")?.text() ?: ""
        val poster: String? = soup.selectFirst("div.video-wrapper .mainPlayerDiv img")?.attr("src") ?:
            soup.selectFirst("head meta[property=og:image]")?.attr("content")
        val tags = soup.select("div.categoriesWrapper a")
            .map { it?.text()?.trim().toString().replace(", ","") }
        return MovieLoadResponse(
            name = title,
            url = url,
            apiName = this.name,
            type = TvType.XXX,
            dataUrl = url,
            posterUrl = poster,
            tags = tags,
            plot = title
        )
    }
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(
            url = data,
            interceptor = WebViewResolver(
                Regex("(master\\.m3u8\\?.*)")
            )
        ).let { response ->
            M3u8Helper().m3u8Generation(
                M3u8Helper.M3u8Stream(
                    response.url,
                    headers = response.headers.toMap()
                ), true
            ).apmap { stream ->
                callback(
                    ExtractorLink(
                        source = name,
                        name = "${this.name} m3u8",
                        url = stream.streamUrl,
                        referer = mainUrl,
                        quality = getQualityFromName(stream.quality?.toString()),
                        isM3u8 = true
                    )
                )
            }
        }
        return true
    }

    private fun fetchImgUrl(imgsrc: Element?): String? {
        val img = try { imgsrc?.attr("data-src")
                ?: imgsrc?.attr("data-mediabook")
                ?: imgsrc?.attr("alt")
                ?: imgsrc?.attr("data-mediumthumb")
                ?: imgsrc?.attr("data-thumb_url")
                ?: imgsrc?.attr("src")
        } catch (e:Exception) { null }
        return img
    }
}