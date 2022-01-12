package com.lagradost.cloudstream3.providersjav

import android.util.Log
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.FEmbed
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class Javhdicu : MainAPI() {
    override val name: String get() = "JAVHD.icu"
    override val mainUrl: String get() = "https://javhd.icu"
    override val supportedTypes: Set<TvType> get() = setOf(TvType.JAV)
    override val hasDownloadSupport: Boolean get() = true
    override val hasMainPage: Boolean get() = true
    override val hasQuickSearch: Boolean get() = false

    override fun getMainPage(): HomePageResponse {
        val document = app.get(mainUrl).document
        val all = ArrayList<HomePageList>()
        val mainbody = document.getElementsByTag("body")?.select("div.container")
        //Log.i(this.name, "Result => (mainbody) ${mainbody}")

        var count = 0
        val titles = mainbody?.select("div.section-header")?.mapNotNull {
            val text = it?.text() ?: return@mapNotNull null
            count++
            Pair(count, text)
        } ?: return HomePageResponse(all)

        //Log.i(this.name, "Result => (titles) ${titles}")
        val entries = mainbody.select("div#video-widget-3016")
        count = 0
        entries?.forEach { it2 ->
            count++
            // Fetch row title
            val pair = titles.filter { aa -> aa.first == count }
            val title = if (pair.isNotEmpty()) { pair[0].second } else { "<No Name Row>" }
            // Fetch list of items and map
            val inner = it2.select("div.col-md-3.col-sm-6.col-xs-6.item.responsive-height.post")
            val elements: List<SearchResponse> = inner?.mapNotNull {
                // Inner element
                val aa = it.selectFirst("div.item-img > a") ?: return@mapNotNull null
                // Video details
                val link = aa.attr("href") ?: return@mapNotNull null
                var name = aa.attr("title") ?: ""
                val image = aa.select("img")?.attr("src")
                val year = null
                //Log.i(this.name, "Result => (link) ${link}")
                //Log.i(this.name, "Result => (image) ${image}")
                name = name.removePrefix("JAV HD")

                MovieSearchResponse(
                    name = name,
                    url = link,
                    apiName = this.name,
                    type = TvType.JAV,
                    posterUrl = image,
                    year = year,
                    id = null,
                )
            }?.distinctBy { a -> a.url } ?: listOf()
            all.add(
                HomePageList(
                    title, elements
                )
            )
        }
        return HomePageResponse(all.filter { hp -> hp.list.isNotEmpty() })
    }

    override fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document.getElementsByTag("body")
            ?.select("div.container > div.row")
            ?.select("div.col-md-8.col-sm-12.main-content")
            ?.select("div.row.video-section.meta-maxwidth-230")
            ?.select("div.item.responsive-height.col-md-4.col-sm-6.col-xs-6")
        //Log.i(this.name, "Result => $document")
        if (!document.isNullOrEmpty()) {
            return document.map {
                val content = it.select("div.item-img > a").firstOrNull()
                //Log.i(this.name, "Result => $content")
                val href = content?.attr("href")
                val link = if (href.isNullOrEmpty()) {
                    ""
                } else {
                    fixUrl(href)
                }
                val imgContent = content?.select("img")
                var title = imgContent?.attr("alt") ?: ""
                val image = imgContent?.attr("src")?.trim('\'')
                val year = null
                //Log.i(this.name, "Result => Title: ${title}, Image: ${image}")

                if (title.startsWith("JAV HD")) {
                    title = title.substring(7)
                }
                MovieSearchResponse(
                    title,
                    link,
                    this.name,
                    TvType.JAV,
                    image,
                    year
                )
            }.filter { item -> item.url.isNotEmpty() }
        }
        return listOf()
    }

    override fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val body = document.getElementsByTag("body")
            ?.select("div.container > div.row")
            ?.select("div.col-md-8.col-sm-12.main-content")
            ?.firstOrNull()
        //Log.i(this.name, "Result => ${body}")
        val innerBody = body?.select("div.video-details > div.post-entry")
        val innerDiv = innerBody?.select("div")?.firstOrNull()

        // Video details
        val poster = innerDiv?.select("img")?.attr("src")
        val title = innerDiv?.selectFirst("p.wp-caption-text")?.text() ?: "<No Title>"
        val descript = innerBody?.select("p")?.firstOrNull()?.text()
        //Log.i(this.name, "Result => (innerDiv) ${innerDiv}")

        val re = Regex("[^0-9]")
        var yearString = body?.select("div.video-details > span.date")?.firstOrNull()?.text()
        //Log.i(this.name, "Result => (yearString) ${yearString}")
        yearString = yearString?.let { re.replace(it, "").trim() }
        //Log.i(this.name, "Result => (yearString) ${yearString}")
        val year = yearString?.takeLast(4)?.toIntOrNull()

        // Video link
        val videoLinks = body?.select("ul.pagination.post-tape > li")?.mapNotNull { section ->
            val vidlink = section?.select("a")?.attr("href")
            if (vidlink.isNullOrEmpty()) {
                return@mapNotNull null
            }
            val doc = app.get(vidlink).document
            doc.select("div.player.player-small.embed-responsive.embed-responsive-16by9")
                    ?.select("iframe")?.attr("src") ?: return@mapNotNull null
        }?.toJson() ?: ""
        return MovieLoadResponse(title, url, this.name, TvType.JAV, videoLinks, poster, year, descript, null, null)
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

        var count = 0
        mapper.readValue<List<String>>(data.trim()).forEach { vid ->
            count += 1
            Log.i(this.name, "Result => (vid) $vid")
            // parse single link
            if (vid.startsWith("https://javhdfree.icu")) {
                val extractor = FEmbed()
                val srcAdd = extractor.getUrl(vid)
                for (item in srcAdd) {
                    item.name += " Scene $count"
                }
                srcAdd.forEach{ callback.invoke(it) }
            } else {
                loadExtractor(vid, vid, callback)
            }
        }
        return true
    }
}