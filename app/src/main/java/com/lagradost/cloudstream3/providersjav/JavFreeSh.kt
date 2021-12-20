package com.lagradost.cloudstream3.providersjav

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.json.JSONObject
import org.jsoup.Jsoup

class JavFreeSh : MainAPI() {
    override val name: String get() = "JavFree.sh"
    override val mainUrl: String get() = "https://javfree.sh"
    override val supportedTypes: Set<TvType> get() = setOf(TvType.JAV)
    override val hasDownloadSupport: Boolean get() = false
    override val hasMainPage: Boolean get() = true
    override val hasQuickSearch: Boolean get() = false

    class Response(json: String) : JSONObject(json) {
        val id: String? = this.optString("id")
        val poster: String? = this.optString("poster")
        val list = this.optJSONArray("list")
            ?.let { 0.until(it.length()).map { i -> it.optJSONObject(i) } } // returns an array of JSONObject
            ?.map { Links(it.toString()) } // transforms each JSONObject of the array into 'Links'
    }
    class Links(json: String) : JSONObject(json) {
        val url: String? = this.optString("url")
        val server: String? = this.optString("server")
        val active: Int? = this.optInt("active")
    }

    override fun getMainPage(): HomePageResponse {
        val html = app.get(mainUrl).text
        val document = Jsoup.parse(html)
        val all = ArrayList<HomePageList>()

        val mainbody = document.getElementsByTag("body").select("div#page")
            .select("div#content").select("div#primary")
            .select("main")

        mainbody.select("section")?.forEach { it2 ->
            // Fetch row title
            val title = it2.select("h2.widget-title")?.text() ?: "Unnamed Row"
            // Fetch list of items and map
            val inner = it2.select("div.videos-list").select("article")
            if (inner != null) {
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
        }
        return HomePageResponse(all)
    }

    override fun search(query: String): List<SearchResponse> {
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

    override fun load(url: String): LoadResponse {
        val response = app.get(url).text
        val doc = Jsoup.parse(response)
        //Log.i(this.name, "Result => (url) ${url}")
        val poster = doc.select("meta[property=og:image]").firstOrNull()?.attr("content")
        val title = doc.select("meta[name=title]").firstOrNull()?.attr("content").toString()
        val descript = doc.select("meta[name=description]").firstOrNull()?.attr("content")

        val body = doc.getElementsByTag("body")
        val yearElem = body
            ?.select("div#page > div#content > div#primary > main > article")
            ?.select("div.entry-content > div.tab-content > div#video-about > div#video-date")
        //Log.i(this.name, "Result => (yearElem) ${yearElem}")
        val year = yearElem?.text()?.trim()?.takeLast(4)?.toIntOrNull()

        var id = body
            ?.select("div#page > div#content > div#primary > main > article > header > div > div > div > script")
            ?.toString() ?: ""
        if (id != "") {
            val startS = "<iframe src="
            id = id.substring(id.indexOf(startS) + startS.length + 1)
            //Log.i(this.name, "Result => (id) ${id}")
            id = id.substring(0, id.indexOf("\""))
        }
        //Log.i(this.name, "Result => (id) ${id}")
        return MovieLoadResponse(title, url, this.name, TvType.JAV, id, poster, year, descript, null, null)
    }

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data == "about:blank") return false
        if (data == "") return false
        var sources: List<ExtractorLink> = listOf()
        try {
            // get request to: https://player.javfree.sh/stream/687234424271726c
            val id = data.substring(data.indexOf("#")).substring(1)
            val jsonres = app.get("https://player.javfree.sh/stream/${id}").text
            val streamdata = JavFreeSh.Response(jsonres)
            //Log.i(this.name, "Result => (jsonres) ${jsonres}")
            try {
                // Invoke sources
                if (streamdata.list != null) {
                    for (link in streamdata.list) {
                        var linkUrl = link.url ?: ""
                        var server = link.server ?: ""
                        if (linkUrl != "") {
                            Log.i(this.name, "Result => (link url) ${linkUrl}")
                            // identify server
                            if (server != "") {
                                server = server.toLowerCase()
                            }
                            if (server.contains("streamsb")) {
                                linkUrl = linkUrl.substring(0, linkUrl.indexOf("?poster"))
                                    //.replace("streamsb.net", "sbembed.com")
                                Log.i(this.name, "Result => (streamsb link) ${linkUrl}")
                                val extractor = StreamSB()
                                val src = extractor.getUrl(linkUrl, referer = "https://player.javfree.sh/embed.html")
                                if (src.isNotEmpty()) {
                                    //Log.i(this.name, "Result => (streamsb) ${src}")
                                    sources + src
                                }
                            }
                        }
                    }
                }
                if (sources.isNotEmpty()) {
                    for (source in sources) {
                        callback.invoke(source)
                        Log.i(this.name, "Result => (source) ${source.url}")
                    }
                    return true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.i(this.name, "Result => (e) ${e}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.i(this.name, "Result => (e) ${e}")
        }
        return false
    }
}