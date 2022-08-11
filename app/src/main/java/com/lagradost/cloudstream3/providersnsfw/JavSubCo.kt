package com.lagradost.cloudstream3.providersnsfw

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.FEmbed
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup

class JavSubCo : MainAPI() {
    override var name = "JavSub"
    override var mainUrl = "https://javsub.co"
    override val supportedTypes: Set<TvType> get() = setOf(TvType.JAV)
    override val hasDownloadSupport: Boolean get() = true
    override val hasMainPage: Boolean get() = true
    override val hasQuickSearch: Boolean get() = false

    private val prefixTag = "dummyTag" //For use on stream links to differentiate links

    data class ResponseMovieDetails(
        @JsonProperty("name") val name: String?,
        @JsonProperty("description") val description: String?,
        @JsonProperty("thumbnailUrl") val thumbnailUrl: String?,
        @JsonProperty("uploadDate") val uploadDate: String?,
        @JsonProperty("contentUrl") val contentUrl: String?
    )

    override val mainPage = mainPageOf(
        "$mainUrl/page/" to "Main Page",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val categoryData = request.data
        val categoryName = request.name
        val pagedlink = if (page > 0) categoryData + page else categoryData
        val document = app.get(pagedlink).document
        val homepage = document.select("main#main-content").map { it2 ->
            val inner = it2?.select("article > div.post-item-wrap") ?: return@map null
            //Log.i(this.name, "inner => $inner")
            val elements: List<SearchResponse> = inner.mapNotNull {
                //Log.i(this.name, "Inner content => $innerArticle")
                val innerA = it.selectFirst("div.blog-pic-wrap > a")?: return@mapNotNull null
                val link = fixUrlNull(innerA.attr("href")) ?: return@mapNotNull null

                val imgArticle = innerA.selectFirst("img")
                val name = innerA.attr("title") ?: imgArticle?.attr("alt") ?: "<No Title>"
                val image = imgArticle?.attr("data-src")
                val year = null
                //Log.i(this.name, "image => $image")

                MovieSearchResponse(
                    name = name,
                    url = link,
                    apiName = this.name,
                    type = TvType.JAV,
                    posterUrl = image,
                    year = year
                )
            }.distinctBy { a -> a.url }

            HomePageList(
                name = categoryName,
                list = elements,
                isHorizontalImages = true
            )
        }.filterNotNull().filter { a -> a.list.isNotEmpty() }

        if (homepage.isNotEmpty()) {
            return newHomePageResponse(
                list = homepage,
                hasNext = true
            )
        }
        throw ErrorLoadingException("No homepage data found!")
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query}"
        val document = app.get(url).document.getElementsByTag("body")
            .select("main#main-content")?.select("article")

        return document?.mapNotNull {
            if (it == null) { return@mapNotNull null }
            val innerA = it.selectFirst("div.blog-pic-wrap > a")?: return@mapNotNull null
            val link = fixUrlNull(innerA.attr("href")) ?: return@mapNotNull null

            val imgArticle = innerA.selectFirst("img")
            val title = innerA.attr("title") ?: imgArticle?.attr("alt") ?: "<No Title>"
            val image = imgArticle?.attr("data-src")
            val year = null

            MovieSearchResponse(
                name = title,
                url = link,
                apiName = this.name,
                type = TvType.JAV,
                posterUrl = image,
                year = year
            )
        }?.distinctBy { b -> b.url } ?: listOf()
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val body = document.getElementsByTag("body")

        // Default values
        var title = ""
        var poster : String? = null
        var year : Int? = null
        var descript : String? = null

        // Video details
        var scriptJson = ""
        run breaking@{
            body.select("script")?.forEach {
                val scrAttr = it?.attr("type") ?: return@forEach
                if (scrAttr.equals("application/ld+json", ignoreCase = true)) {
                    scriptJson = it.html() ?: ""
                    return@breaking
                }
            }
        }
        //Log.i(this.name, "Result => (scriptJson) $scriptJson")

        // Video stream
        val playerIframes: MutableList<String> = try {
            //Note: Fetch all multi-link urls
            document.selectFirst("div.series-listing")?.select("a")?.mapNotNull {
                it?.attr("href") ?: return@mapNotNull null
            }?.toMutableList() ?: mutableListOf()
        } catch (e: Exception) {
            Log.i(this.name, "Result => Exception (load) $e")
            mutableListOf()
        }

        // JAV Info
        tryParseJson<ResponseMovieDetails>(scriptJson)?.let {
            val contentUrl = it.contentUrl
            title = it.name ?: ""
            poster = it.thumbnailUrl
            year = it.uploadDate?.take(4)?.toIntOrNull()
            descript = "Title: $title ${System.lineSeparator()} ${it.description}"

            // Add additional links, Raw link without needing to fetch from JavSub API
            //if (!contentUrl.isNullOrBlank()) {
                //playerIframes.add("$prefixTag$contentUrl")
            //}
            //Log.i(this.name, "Result => (contentUrl) $contentUrl")
        }

        Log.i(this.name, "Result => (playerIframes) ${playerIframes.toJson()}")

        return MovieLoadResponse(
            name = title,
            url = url,
            apiName = this.name,
            type = TvType.JAV,
            dataUrl = playerIframes.toJson(),
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

        var count = 0
        tryParseJson<List<String>>(data)?.apmap { link ->
            Log.i(this.name, "Result => (link) $link")
            if (link.startsWith(prefixTag)) {
                val linkUrl = link.removePrefix(prefixTag)
                val success = extractStreamLink(linkUrl, subtitleCallback, callback)
                if (success) {
                    count++
                }
            }
            else {
                val innerDoc =
                    app.get(link).document.selectFirst("script#beeteam368_obj_wes-js-extra")
                var innerText = innerDoc?.html() ?: ""
                if (innerText.isNotBlank()) {
                    "(?<=single_video_url\":)(.*)(?=,)".toRegex().find(innerText)
                        ?.groupValues?.get(0)?.let { iframe ->
                            innerText = iframe.trim().trim('"')
                        }
                    Jsoup.parse(innerText)?.selectFirst("iframe")?.attr("src")?.let { server ->
                        val serverLink = server.replace("\\", "").replace("\"", "")
                        val success = extractStreamLink(serverLink, subtitleCallback, callback)
                        if (success) {
                            count++
                        }
                        Log.i(this.name, "Result => (streamLink add) $serverLink")
                    }
                }
            }
        }
        //Log.i(this.name, "Result => count: $count")
        return count > 0
    }

    private suspend fun extractStreamLink(
        link: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit)
    : Boolean {
        if (link.isNotBlank()) {
            when {
                link.contains("watch-jav") -> {
                    val extractor = FEmbed()
                    extractor.domainUrl = "embedsito.com"
                    extractor.getSafeUrl(
                        url = link,
                        referer = mainUrl,
                        subtitleCallback = subtitleCallback,
                        callback = callback
                    )
                    return true
                }
                else -> {
                    return loadExtractor(
                        url = link,
                        referer = mainUrl,
                        subtitleCallback = subtitleCallback,
                        callback = callback
                    )
                }
            }
        }
        return false
    }
}