package com.lagradost.cloudstream3.providersjav

import android.util.Log
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.FEmbed
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.DoodWsExtractor
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup

class JavSubCo : MainAPI() {
    override val name: String get() = "JAVSub.co"
    override val mainUrl: String get() = "https://javsub.co"
    override val supportedTypes: Set<TvType> get() = setOf(TvType.JAV)
    override val hasDownloadSupport: Boolean get() = true
    override val hasMainPage: Boolean get() = true
    override val hasQuickSearch: Boolean get() = false

    override fun getMainPage(): HomePageResponse {
        val document = app.get(mainUrl).document

        return HomePageResponse(document.select("div#content").select("div")?.first()
            ?.select("main > section")
            ?.get(0)?.getElementsByTag("div")?.map { it2 ->
                val title = "Homepage"
                val inner = it2?.select("article") ?: return@map null
                val elements: List<SearchResponse> = inner.mapNotNull {
                    //Log.i(this.name, "Inner content => $innerArticle")
                    val aa = it.selectFirst("div")?.selectFirst("figure") ?: return@mapNotNull null
                    val link = fixUrlNull(it.select("a")?.attr("href")) ?: return@mapNotNull null

                    val imgArticle = aa.select("img")
                    val name = imgArticle?.attr("alt") ?: "<No Title>"
                    val image = imgArticle?.attr("src")
                    val year = null

                    MovieSearchResponse(
                        name,
                        link,
                        this.name,
                        TvType.JAV,
                        image,
                        year,
                        null,
                    )
                }.distinctBy { a -> a.url }
                HomePageList(
                    title, elements
                )
            }?.filterNotNull()?.filter { a -> a.list.isNotEmpty() } ?: listOf())
    }

    override fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query}"
        val document = app.get(url).document.getElementsByTag("body")
            .select("div#content > div > main > section > div")
            .select("article")

        return document?.mapNotNull {
            if (it == null) {
                return@mapNotNull null
            }
            val linkUrl = fixUrlNull(it.select("a")?.attr("href")) ?: return@mapNotNull null
            val title = it.select("header > h2")?.text() ?: ""
            val image = it.select("div > figure").select("img")?.attr("src")?.trim('\'')
            val year = null

            MovieSearchResponse(
                title,
                linkUrl,
                this.name,
                TvType.JAV,
                image,
                year
            )
        }?.distinctBy { b -> b.url } ?: listOf()
    }

    override fun load(url: String): LoadResponse {
        val document = app.get(url).document
        //Log.i(this.name, "Url => ${url}")
        val body = document.getElementsByTag("body")
        //Log.i(this.name, "Result => ${body}")

        // Video details
        val content = body.select("div#content").select("div")
        val title = content.select("nav > p > span").text()

        val descript = content.select("main > article > div > div")
            .lastOrNull()?.select("div")?.text()
        //Log.i(this.name, "Result => ${descript}")
        // Year
        val re = Regex("[^0-9]")
        var yearString = content.select("main > article > div > div").last()
            ?.select("p")?.filter { it.text()?.contains("Release Date") == true }
            ?.get(0)?.text()
        yearString = yearString?.split(":")?.get(1)?.trim() ?: ""
        yearString = re.replace(yearString, "")
        val year = yearString.takeLast(4).toIntOrNull()
        //Log.i(this.name, "Result => (year) ${year} / (string) ${yearString}")
        // Poster Image
        var posterElement = body.select("script.yoast-schema-graph")?.toString() ?: ""
        val posterId = "\"contentUrl\":"
        val poster = when (posterElement.isNotEmpty()) {
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

        // Video stream
        val iframe: String =  try {
            val streamdataStart = body?.toString()?.indexOf("var torotube_Public = {") ?: 0
            var streamdata = body?.toString()?.substring(streamdataStart) ?: ""
            //Log.i(this.name, "Result => (streamdata) ${streamdata}")
            streamdata = streamdata.substring(0, streamdata.indexOf("};"))
            streamdata = streamdata.substring(streamdata.indexOf("player"))
            streamdata = streamdata.substring(streamdata.indexOf("["))
            streamdata.substring(1, streamdata.indexOf("]"))
                .replace("\\\"", "\"")
                .replace("\",\"", "")
                .replace("\\", "")
        } catch (e: Exception) {
            Log.i(this.name, "Result => Exception (load) $e")
            ""
        }
        val streamUrl = Jsoup.parse(iframe)?.select("iframe")
            ?.filter { s -> s.hasAttr("src") }
            ?.mapNotNull {
                a -> a?.attr("src") ?: return@mapNotNull null
            }?.toJson() ?: ""
        //Log.i(this.name, "Result => (streamUrl) $streamUrl")
        return MovieLoadResponse(title, url, this.name, TvType.JAV, streamUrl, poster, year, descript, null, null)
    }

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isEmpty()) return false
        if (data == "[]") return false
        if (data == "about:blank") return false

        mapper.readValue<List<String>>(data).forEach { link->
            Log.i(this.name, "Result => (link) $link")
            if (link.isNotEmpty()) {
                when {
                    link.contains("watch-jav") -> {
                        val extractor = FEmbed()
                        extractor.domainUrl = "embedsito.com"
                        extractor.getUrl(link, mainUrl).forEach { it2 ->
                            callback.invoke(it2)
                        }
                    }
                    link.contains("dood.ws") -> {
                        //Log.i(this.name, "Result => (doodwsUrl) ${link}")
                        // Probably not gonna work since link is on 'dood.ws' domain
                        // adding just in case it loads urls ¯\_(ツ)_/¯
                        val extractor = DoodWsExtractor()
                        extractor.getUrl(link, mainUrl)?.forEach { it2 ->
                            callback.invoke(it2)
                        }
                    }
                    else -> {
                        loadExtractor(link, mainUrl, callback)
                    }
                }
            }
        }
        return true
    }
}