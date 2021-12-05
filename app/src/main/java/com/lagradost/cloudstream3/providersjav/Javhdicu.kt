package com.lagradost.cloudstream3.providersjav

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.FEmbed
import com.lagradost.cloudstream3.extractors.StreamLare
import com.lagradost.cloudstream3.network.get
import com.lagradost.cloudstream3.network.text
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.Jsoup

class Javhdicu : MainAPI() {
    override val name: String
        get() = "JAVHD.icu"

    override val mainUrl: String
        get() = "https://javhd.icu"

    override val supportedTypes: Set<TvType>
        get() = setOf(TvType.JAV)

    override val hasDownloadSupport: Boolean
        get() = false

    override val hasMainPage: Boolean
        get() = true

    override val hasQuickSearch: Boolean
        get() = false

    override fun getMainPage(): HomePageResponse {
        val html = get("$mainUrl", timeout = 15).text
        val document = Jsoup.parse(html)
        val all = ArrayList<HomePageList>()
        val mainbody = document.getElementsByTag("body")
            ?.select("div.container")
        //Log.i(this.name, "Result => (mainbody) ${mainbody}")
        var count = 0
        val titles = mainbody?.select("div.section-header")!!.map {
            count++
            Pair(count, it?.text())
        }
        //Log.i(this.name, "Result => (titles) ${titles}")
        val entries = mainbody?.select("div#video-widget-3016")
        count = 0
        entries?.forEach { it2 ->
            count++
            // Fetch row title
            val pair = titles.filter { aa -> aa.first == count }
            val title = if (pair.size > 0) { pair?.get(0)?.second ?: "<Unnamed Row>" } else { "<No Name Row>" }
            // Fetch list of items and map
            val inner = it2.select("div.col-md-3.col-sm-6.col-xs-6.item.responsive-height.post")
            if (inner != null) {
                val elements: List<SearchResponse> = inner!!.map {

                    val aa = it.select("div.item-img > a").firstOrNull()

                    val link = aa?.attr("href") ?: ""
                    val name = aa?.attr("title") ?: "<No Title>"
                    val image = aa?.select("img")?.attr("src")
                    val year = null
                    //Log.i(this.name, "Result => (link) ${link}")
                    //Log.i(this.name, "Result => (image) ${image}")

                    MovieSearchResponse(
                        name,
                        link,
                        this.name,
                        TvType.JAV,
                        image,
                        year,
                        null,
                    )
                }.filter { it3 -> it3.url != "" }

                all.add(
                    HomePageList(
                        title, elements
                    )
                )
            }
        }
        return HomePageResponse(all.filter { hp -> hp.list.isNotEmpty() })
    }

    override fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query}"
        val html = get(url).text
        val document = Jsoup.parse(html).getElementsByTag("body")
            ?.select("div.container > div.row")
            ?.select("div.col-md-8.col-sm-12.main-content")
            ?.select("div.row.video-section.meta-maxwidth-230")
            ?.select("div.item.responsive-height.col-md-4.col-sm-6.col-xs-6")
        //Log.i(this.name, "Result => $document")
        return document!!.map {
            val content = it.select("div.item-img > a").firstOrNull()
            //Log.i(this.name, "Result => $content")
            val href = fixUrl(content?.attr("href") ?: "")
            val imgContent = content?.select("img")
            val title = imgContent?.attr("alt") ?: "<No Title Found>"
            val image = imgContent?.attr("src")?.trim('\'')
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
        }.filter { item -> item.url != "" }
    }

    override fun load(url: String): LoadResponse {
        val response = get(url).text
        val document = Jsoup.parse(response)
        val body = document.getElementsByTag("body")
            ?.select("div.container > div.row")
            ?.select("div.col-md-8.col-sm-12.main-content")
            ?.firstOrNull()
        //Log.i(this.name, "Result => ${body}")
        val innerBody = body?.select("div.video-details > div.post-entry")
        val innerDiv = innerBody?.select("div")?.firstOrNull()

        // Video details
        val poster = innerDiv?.select("img")?.attr("src")
        val title = innerDiv?.select("p")?.firstOrNull()?.text() ?: "<No Title>"
        val descript = innerBody?.select("p")?.firstOrNull()?.text() ?: "<No Synopsis found>"
        //Log.i(this.name, "Result => ${descript}")

        val re = Regex("[^0-9]")
        var yearString = body?.select("span")?.firstOrNull()?.text()
        yearString = yearString?.let { re.replace(it, "").trim() }
        val year = yearString?.takeLast(4)?.toIntOrNull()

        // Video link
        val videoLinks: MutableList<String> = mutableListOf()
        val videotapes = body?.select("ul.pagination.post-tape > li")
        if (videotapes != null) {
            for (section in videotapes) {
                val vidlink = section?.select("a")?.attr("href")
                if (vidlink != null) {
                    if (vidlink.isNotEmpty()) {
                        try {
                            val doc = Jsoup.parse(get(vidlink).text)
                            val streamLink =
                                doc?.select("div.player.player-small.embed-responsive.embed-responsive-16by9")
                                    ?.select("iframe")?.attr("src") ?: ""
                            //Log.i(this.name, "Result => (streamLink) ${streamLink}")
                            if (streamLink.isNotEmpty()) {
                                videoLinks.add(streamLink)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Log.i(this.name, "Result => (Exception) ${e}")
                        }
                    }
                }
            }
        }

        return MovieLoadResponse(title, url, this.name, TvType.JAV, videoLinks.toString(), poster, year, descript, null, null)
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
            if (data.isNotEmpty()) {
                val vidlinks = data.replace("[", "")
                    .replace("]", "")
                    .split(",")

                var count = 0
                for (vid in vidlinks) {
                    count += 1
                    //Log.i(this.name, "Result => (vid) ${vid}")
                    // parse single link
                    if (vid.startsWith("https://javhdfree.icu")) {
                        val extractor = FEmbed()
                        val src = extractor.getUrl(vid)
                        if (src != null) {
                            val srcAdd = src.toMutableList()
                            for (item in srcAdd) {
                                item.name += " Scene ${count}"
                            }
                            sources.addAll(srcAdd)
                        }
                        continue
                    }
                    if (vid.contains("streamlare")) {
                        val extractor = StreamLare()
                        val src = extractor.getUrl(vid)
                        if (src != null) {
                            val srcAdd = src.toMutableList()
                            for (item in srcAdd) {
                                item.name += " Scene ${count}"
                            }
                            sources.addAll(srcAdd)
                        }
                    }
                }
            }
            // Invoke sources
            if (sources.isNotEmpty()) {
                for (source in sources) {
                    callback.invoke(source)
                    //Log.i(this.name, "Result => (source) ${source.url}")
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