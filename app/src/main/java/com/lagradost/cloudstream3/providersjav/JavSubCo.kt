package com.lagradost.cloudstream3.providersjav

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.FEmbed
import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.Jsoup

class JavSubCo : MainAPI() {
    override val name: String get() = "JAVSub.co"
    override val mainUrl: String get() = "https://javsub.co"
    override val supportedTypes: Set<TvType> get() = setOf(TvType.JAV)
    override val hasDownloadSupport: Boolean get() = false
    override val hasMainPage: Boolean get() = true
    override val hasQuickSearch: Boolean get() = false

    override fun getMainPage(): HomePageResponse {
        val html = app.get(mainUrl).text
        val document = Jsoup.parse(html)
        val all = ArrayList<HomePageList>()

        val mainbody = document.getElementsByTag("body").select("div#content")
            .select("div")?.first()?.select("main")
            ?.select("section")?.select("div")?.firstOrNull()

        //Log.i(this.name, "Main body => $mainbody")
        // Fetch row title
        val title = "Homepage"
        // Fetch list of items and map
        val inner = mainbody?.select("article")
        //Log.i(this.name, "Inner => $inner")
        if (inner != null) {
            val elements: List<SearchResponse> = inner.map {

                //Log.i(this.name, "Inner content => $innerArticle")
                val aa = it.select("div").first()?.select("figure")?.firstOrNull()
                val link = fixUrl(it.select("a")?.attr("href") ?: "")

                val imgArticle = aa?.select("img")
                val name = imgArticle?.attr("alt") ?: "<No Title>"
                var image = imgArticle?.attr("src") ?: ""
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
            }

            all.add(
                HomePageList(
                    title, elements
                )
            )
        }
        return HomePageResponse(all)
    }

    override fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query}"
        val html = app.get(url).text
        val document = Jsoup.parse(html).getElementsByTag("body")
            .select("div#content > div > main > section > div")
            .select("article")

        return document.map {
            val href = it.select("a")?.attr("href")
            val linkUrl = if (href.isNullOrEmpty()) { "" } else fixUrl(href)
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
        }.filter { a -> a.url.isNotEmpty() }
            .distinctBy { b -> b.url }
    }

    override fun load(url: String): LoadResponse {
        val response = app.get(url).text
        val document = Jsoup.parse(response)
        //Log.i(this.name, "Url => ${url}")
        val body = document.getElementsByTag("body")
        //Log.i(this.name, "Result => ${body}")

        // Video details
        val content = body.select("div#content").select("div")
        val title = content.select("nav > p > span").text()

        val descript = content.select("main > article > div > div")
            .lastOrNull()?.select("div")?.text() ?: "<No Synopsis found>"
        //Log.i(this.name, "Result => ${descript}")
        // Year
        val re = Regex("[^0-9]")
        var yearString = content.select("main > article > div > div").last()
            ?.select("p")?.filter { it.text()?.contains("Release Date") == true }
            ?.get(0)?.text()
        yearString = yearString?.split(":")?.get(1)?.trim() ?: ""
        yearString = re.replace(yearString, "")
        val year = yearString?.takeLast(4)?.toIntOrNull()
        //Log.i(this.name, "Result => (year) ${year} / (string) ${yearString}")
        // Poster Image
        var posterElement = body.select("script.yoast-schema-graph")?.toString() ?: ""
        val posterId = "\"contentUrl\":"
        val poster = when (posterElement.isNotEmpty()) {
            true -> {
                posterElement = posterElement.substring(posterElement.indexOf(url.trimEnd('/') + "/#primaryimage"))
                posterElement = posterElement.substring(0, posterElement.indexOf("}"))
                posterElement = posterElement.substring(posterElement.indexOf(posterId) + posterId.length + 1)
                posterElement.substring(0, posterElement.indexOf("\","))
            }
            false -> null
        }
        //Log.i(this.name, "Result => (poster) ${poster}")

        // Video stream
        val streamUrl: String =  try {
            val streamdataStart = body?.toString()?.indexOf("var torotube_Public = {") ?: 0
            var streamdata = body?.toString()?.substring(streamdataStart) ?: ""
            //Log.i(this.name, "Result => (streamdata) ${streamdata}")
            streamdata.substring(0, streamdata.indexOf("};"))
        } catch (e: Exception) {
            Log.i(this.name, "Result => Exception (load) ${e}")
            ""
        }
        return MovieLoadResponse(title, url, this.name, TvType.JAV, streamUrl, poster, year, descript, null, null)
    }

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data == "about:blank") return false
        if (data == "") return false
        val sources = mutableListOf<ExtractorLink>()
        try {
            var streamdata = data.substring(data.indexOf("player"))
            streamdata = streamdata.substring(streamdata.indexOf("["))
            streamdata = streamdata.substring(1, streamdata.indexOf("]"))
                .replace("\\\"", "\"")
                .replace("\",\"", "")
                .replace("\\", "")
            //Log.i(this.name, "Result => (streamdata) ${streamdata}")

            // Get all src from iframes
            val streambody =
                Jsoup.parse(streamdata)?.select("iframe")?.filter { s -> s.hasAttr("src") }
                    ?.map { a -> a?.attr("src") ?: "" }
            //Log.i(this.name, "Result => (streambody) ${streambody.toString()}")

            if (streambody != null) {
                for (link in streambody) {
                    //Log.i(this.name, "Result => (link) ${link}")
                    if (link != null) {
                        if (link.isNotEmpty()) {
                            if (link.contains("streamtape")) {
                                val extractor = StreamTape()
                                val src = extractor.getUrl(link)
                                if (src != null) {
                                    sources.addAll(src)
                                }
                            }
                            if (link.contains("dood.ws")) {
                                //Log.i(this.name, "Result => (doodwsUrl) ${doodwsUrl}")
                                // Probably not gonna work since link is on 'dood.ws' domain
                                // adding just in case it loads urls ¯\_(ツ)_/
                                val extractor = DoodLaExtractor()
                                val src = extractor.getUrl(link, null)
                                if (src != null) {
                                    sources.addAll(src)
                                }
                            }
                            if (link.contains("watch-jav")) {
                                val extractor = FEmbed()
                                val src = extractor.getUrl(link)
                                if (src.isNotEmpty()) {
                                    sources.addAll(src)
                                }
                            }
                        }
                    }
                }
            }
            // Invoke sources
            if (sources.size > 0) {
                for (source in sources) {
                    callback.invoke(source)
                    //Log.i(this.name, "Result => (callback) ${source.url}")
                }
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.i(this.name, "Result => (e) ${e}")
        }
        return false
    }
}