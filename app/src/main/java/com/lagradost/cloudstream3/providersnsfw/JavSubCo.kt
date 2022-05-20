package com.lagradost.cloudstream3.providersnsfw

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.FEmbed
import com.lagradost.cloudstream3.app
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

    data class Response(
        @JsonProperty("player") val player: List<String>?
    )

    override suspend fun getMainPage(): HomePageResponse {
        val document = app.get(mainUrl).document

        return HomePageResponse(
        document.select("main#main-content")
            ?.map { it2 ->
                val title = "Homepage"
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
                    title, elements
                )
            }?.filterNotNull()?.filter { a -> a.list.isNotEmpty() } ?: listOf())
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

        // Video details
        val contentmain = body?.select("div#content")
        val content = contentmain?.select("div")
        val title = content?.select("nav > p > span")?.text() ?: ""

        val descript = content?.select("main > article > div > div")
            ?.lastOrNull()?.select("div")?.text()
        //Log.i(this.name, "Result => ${descript}")
        // Year
        val re = Regex("[^0-9]")
        val yearString = try {
            content?.select("main > article > div > div")?.last()
                ?.select("p")?.filter { it.text()?.contains("Release Date") == true }
                ?.get(0)?.text()?.split(":")?.get(1)?.trim() ?: ""
        } catch (e: Exception) {
            Log.i(this.name, "Result => Exception (load year string) $e")
            ""
        }
        val year = try  {
            re.replace(yearString, "").takeLast(4).toIntOrNull()
        } catch (e: Exception) {
            Log.i(this.name, "Result => Exception (load year) $e")
            null
        }
        //Log.i(this.name, "Result => (year) ${year} / (string) ${yearString}")
        // Poster Image
        var posterElement = body.select("script.yoast-schema-graph")?.toString() ?: ""
        val posterId = "\"contentUrl\":"
        val poster = when (posterElement.isNotBlank()) {
            true -> {
                try {
                    posterElement = posterElement.substring(posterElement.indexOf(url.trimEnd('/') + "/#primaryimage"))
                    posterElement = posterElement.substring(0, posterElement.indexOf("}"))
                    posterElement =
                        posterElement.substring(posterElement.indexOf(posterId) + posterId.length + 1)
                    posterElement.substring(0, posterElement.indexOf("\","))
                } catch (e: Exception) {
                    null
                }
            }
            false -> null
        }
        //Log.i(this.name, "Result => (poster) ${poster}")

        val tags = content?.select("article.vdeo-single > header a")?.mapNotNull {
            //Log.i(this.name, "Result => (tag) $it")
            it?.text()?.trim() ?: return@mapNotNull null
        }

        val recs = contentmain?.select("section article")?.mapNotNull {
            if (it == null) { return@mapNotNull null }
            val aUrl = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val aName = it.select("header > h2")?.text()?.trim() ?: return@mapNotNull null
            val aImgMain = it.select("img")
            val aImg = aImgMain?.attr("src") ?: aImgMain?.attr("data-lazy-src")
            MovieSearchResponse(
                url = aUrl,
                name = aName,
                type = TvType.JAV,
                posterUrl = aImg,
                year = null,
                apiName = this.name
            )
        }

        // Video stream
        val streamLinks = mutableListOf<String>()
        val playerIframes: List<String> = try {
            //Note: Fetch all multi-link urls
            document.selectFirst("div.series-listing")?.select("a")?.mapNotNull {
                it?.attr("href") ?: return@mapNotNull null
            } ?: listOf()
        } catch (e: Exception) {
            Log.i(this.name, "Result => Exception (load) $e")
            listOf()
        }
        Log.i(this.name, "Result => (playerIframes) ${playerIframes.toJson()}")

        //Log.i(this.name, "Result => (streamUrl) $streamUrl")
        return MovieLoadResponse(
            name = title,
            url = url,
            apiName = this.name,
            type = TvType.JAV,
            dataUrl = streamLinks.toJson(),
            posterUrl = poster,
            year = year,
            plot = descript,
            tags = tags,
            recommendations = recs
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isBlank()) return false
        if (data == "[]") return false
        if (data == "about:blank") return false

        var count = 0
        tryParseJson<List<String>>(data)?.forEach { link->
            Log.i(this.name, "Result => (link) $link")
            if (link.isNotBlank()) {
                when {
                    link.contains("watch-jav") -> {
                        val extractor = FEmbed()
                        extractor.domainUrl = "embedsito.com"
                        extractor.getSafeUrl(link, mainUrl)?.forEach { it2 ->
                            callback.invoke(it2)
                            count++
                        }
                    }
                    else -> {
                        if (loadExtractor(link, mainUrl, callback)) {
                            count++
                        }
                    }
                }
            }
            //Log.i(this.name, "Result => count: $count")
        }
        return count > 0
    }
}