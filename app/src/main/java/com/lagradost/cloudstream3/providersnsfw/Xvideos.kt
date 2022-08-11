package com.lagradost.cloudstream3.providersnsfw

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.*


open class Xvideos : MainAPI() {
    override var mainUrl = "https://www.xvideos.com"
    override var name = "Xvideos"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.XXX, TvType.JAV, TvType.Hentai)

    override val mainPage = mainPageOf(
        Pair(mainUrl, "Main Page"),
        Pair("$mainUrl/new/", "New")
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val categoryData = request.data
        val categoryName = request.name
        val isPaged = categoryData.endsWith('/')
        val pagedLink = if (isPaged) categoryData + page else categoryData
        try {
            if (!isPaged && page < 2 || isPaged) {
                val soup = app.get(pagedLink).document
                val home = soup.select("div.thumb-block").mapNotNull {
                    if (it == null) { return@mapNotNull null }
                    val title = it.selectFirst("p.title a")?.text() ?: ""
                    val link = fixUrlNull(it.selectFirst("div.thumb a")?.attr("href")) ?: return@mapNotNull null
                    val image = it.selectFirst("div.thumb a img")?.attr("data-src")
                    MovieSearchResponse(
                        name = title,
                        url = link,
                        apiName = this.name,
                        type = TvType.XXX,
                        posterUrl = image,
                        year = null
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
            }
        } catch (e: Exception) {
            //e.printStackTrace()
            logError(e)
        }
        throw ErrorLoadingException()
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl?k=${query}"
        val document = app.get(url).document
        return document.select("div.thumb-block").mapNotNull {
            val title = it.selectFirst("p.title a")?.text()
                ?: it.selectFirst("p.profile-name a")?.text()
                ?: ""
            val href = fixUrlNull(it.selectFirst("div.thumb a")?.attr("href")) ?: return@mapNotNull null
            val image = if (href.contains("channels") || href.contains("pornstars")) null else it.selectFirst("div.thumb-inside a img")?.attr("data-src")
            val finaltitle = if (href.contains("channels") || href.contains("pornstars")) "" else title
            MovieSearchResponse(
                name = finaltitle,
                url = href,
                apiName = this.name,
                type = TvType.XXX,
                posterUrl = image
            )

        }.toList()
    }
    override suspend fun load(url: String): LoadResponse? {
        val soup = app.get(url).document
        val title = if (url.contains("channels")||url.contains("pornstars")) soup.selectFirst("html.xv-responsive.is-desktop head title")?.text() else
            soup.selectFirst(".page-title")?.text()
        val poster: String? = if (url.contains("channels") || url.contains("pornstars")) soup.selectFirst(".profile-pic img")?.attr("data-src") else
            soup.selectFirst("head meta[property=og:image]")?.attr("content")
        val tags = soup.select(".video-tags-list li a")
            .map { it?.text()?.trim().toString().replace(", ","") }
        val episodes = soup.select("div.thumb-block").mapNotNull {
            val href = it?.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val name = it.selectFirst("p.title a")?.text() ?: ""
            val epthumb = it.selectFirst("div.thumb a img")?.attr("data-src")
            Episode(
                name = name,
                data = href,
                posterUrl = epthumb,
            )
        }
        val tvType = if (url.contains("channels") || url.contains("pornstars")) TvType.TvSeries else TvType.XXX
        return when (tvType) {
            TvType.TvSeries -> {
                TvSeriesLoadResponse(
                    name = title ?: "",
                    url = url,
                    apiName = this.name,
                    type = TvType.XXX,
                    episodes = episodes,
                    posterUrl = poster,
                    plot = title,
                    showStatus = ShowStatus.Ongoing,
                    tags = tags,
                )
            }
            TvType.XXX -> {
                MovieLoadResponse(
                    name = title ?: "",
                    url = url,
                    apiName = this.name,
                    type = tvType,
                    dataUrl = url,
                    posterUrl = poster,
                    plot = title,
                    tags = tags,
                )
            }
            else -> null
        }
    }
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document.select("script").apmap { script ->
            if (script.data().contains("HTML5Player")) {
                val extractedlink = script.data().substringAfter(".setVideoHLS('")
                    .substringBefore("');")
                if (extractedlink.isNotBlank()) {
                    M3u8Helper().m3u8Generation(
                        M3u8Helper.M3u8Stream(
                            extractedlink,
                            headers = app.get(data).headers.toMap()
                        ), true
                    ).map { stream ->
                        callback(
                            ExtractorLink(
                                source = this.name,
                                name = "${this.name} m3u8",
                                url = stream.streamUrl,
                                referer = data,
                                quality = getQualityFromName(stream.quality?.toString()),
                                isM3u8 = true
                            )
                        )
                    }
                }
                val mp4linkhigh = script.data().substringAfter("html5player.setVideoUrlHigh('").substringBefore("');")
                if (mp4linkhigh.isNotBlank()) {
                    callback(
                        ExtractorLink(
                            source = this.name,
                            name = "${this.name} MP4 High",
                            url = mp4linkhigh,
                            referer = data,
                            quality = Qualities.Unknown.value,
                        )
                    )
                }
                val mp4linklow = script.data().substringAfter("html5player.setVideoUrlLow('").substringBefore("');")
                if (mp4linklow.isNotBlank()) {
                    callback(
                        ExtractorLink(
                            source = this.name,
                            name = "${this.name} MP4 Low",
                            url = mp4linklow,
                            referer = data,
                            quality = Qualities.Unknown.value,
                        )
                    )
                }
            }
        }
        return true
    }
}
