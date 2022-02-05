package com.lagradost.cloudstream3.providersjav

import android.util.Log
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.XStreamCdn
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.HttpSession
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup

class OpJavCom : MainAPI() {
    override val name: String get() = "OpJAV.com"
    override val mainUrl: String get() = "https://opjav.com"
    override val supportedTypes: Set<TvType> get() = setOf(TvType.JAV)
    override val hasDownloadSupport: Boolean get() = false
    override val hasMainPage: Boolean get() = true
    override val hasQuickSearch: Boolean get() = false

    override suspend fun getMainPage(): HomePageResponse {
        val html = app.get(mainUrl).text
        val document = Jsoup.parse(html)
        val all = ArrayList<HomePageList>()

        val mainbody = document.getElementsByTag("body").select("div#header")
            .select("div#body-wrapper").select("div.content-wrapper")
            .select("div.container.fit").select("div.main.col-lg-8.col-md-8.col-sm-7")
            .select("div.block.update").select("div.block-body")
            .select("div.content")

        var count = 0
        mainbody.select("div.list-film.row")?.forEach { it2 ->
            count++
            val title = "Row $count"
            // Fetch items and map
            val inner = it2.select("div.inner")
            if (inner != null) {
                val elements: List<SearchResponse> = inner.map {

                    val aa = it.select("a.poster")
                    val link = fixUrl(aa.attr("href"))
                    val image = aa.select("img").attr("src")
                    val name = aa.attr("title") ?: "<No Title>"
                    val year = inner.select("dfn")?.get(1)?.text()?.toIntOrNull()

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

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/${query}/"
        val html = app.get(url).text
        val document = Jsoup.parse(html).select("div.block-body > div.list-film.row")
            .select("div.item.col-lg-3.col-md-3.col-sm-6.col-xs-6")

        Log.i(this.name, "Result size => ${document.size}")
        return document.map {
            val inner = it.select("div.inner")

            val innerPost = inner.select("a.poster")

            val href = fixUrl(innerPost.attr("href") ?: "")
            val title = innerPost.attr("title") ?: "<No Title found>"
            val image = innerPost.select("img").attr("src")
            Log.i(this.name, "Result image => $image")
            val year = inner.select("dfn").lastOrNull()?.text()?.toIntOrNull()

            //Log.i(this.name, "Result => $")
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

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        //Log.i(this.name, "Result => (url) ${url}")
        val poster = doc.select("meta[itemprop=image]")?.get(1)?.attr("content")
        val title = doc.selectFirst("meta[property=og:title]")?.attr("content").toString()
        val descript = doc.selectFirst("meta[name=keywords]")?.attr("content")
        val year = doc.selectFirst("meta[itemprop=dateCreated]")?.attr("content")?.toIntOrNull()

        //Fetch server links
        val watchlink = ArrayList<String>()
        val mainLink = doc.select("div.buttons.row > div > div > a")
            ?.attr("href") ?: ""
        //Log.i(this.name, "Result => (mainLink) $mainLink")

        //Fetch episode links from mainlink
        val epsDoc = app.get(url = mainLink, referer = mainUrl).document
        //Fetch filmId
        /*var filmId = ""
        val epLinkDoc = epsDoc.getElementsByTag("head").select("script").toString()
        //Log.i(this.name, "Result => (epLinkDoc) $epLinkDoc")
        try {
            val epTextTemp = epLinkDoc.substring(epLinkDoc.indexOf("filmID = parseInt"))
            val epText = epTextTemp.substring(1, epTextTemp.indexOf("</script>")).trim()
                .filterNot { a -> a.isWhitespace() }
            if (epText.isNotEmpty()) {
                filmId = try {
                    "(?<=filmID=parseInt\\(')(.*)(?='\\);)".toRegex().find(epText)?.groupValues?.get(0) ?: ""
                } catch (e: Exception) { "" }
                Log.i(this.name, "Result => (filmId) $filmId")
            }
        } catch (e: Exception) { }*/
        //Fetch server links
        epsDoc.select("div.block.servers")?.select("li")?.mapNotNull {
            val inner = it?.selectFirst("a") ?: return@mapNotNull null
            val linkUrl = inner.attr("href") ?: return@mapNotNull null
            val linkId = inner.attr("id") ?: return@mapNotNull null
            Pair(linkUrl, linkId)
        }?.forEach {
            //First = Url, Second = EpisodeID
            //Log.i(this.name, "Result => (eplink-Id) $it")
            val ajaxHead = mapOf(
                Pair("Origin", mainUrl),
                Pair("Referer", it.first)
            )
            //https://opjav.com/movie/War%20of%20the%20Roses-64395/watch-movie.html
            //EpisodeID, 442671
            //filmID, 64395
            val ajaxData = mapOf(
                Pair("NextEpisode", "1"),
                Pair("EpisodeID", it.second)
                //Pair("filmID", filmId)
            )
            val sess = HttpSession()
            val respAjax = sess.post("$mainUrl/ajax", headers = ajaxHead, data = ajaxData).text
            Log.i(this.name, "Result => (respAjax text) $respAjax")
            Jsoup.parse(respAjax).select("iframe")?.forEach { iframe ->
                val serverLink = iframe?.attr("src")?.trim()
                if (!serverLink.isNullOrEmpty()) {
                    watchlink.add(serverLink)
                    Log.i(this.name, "Result => (serverLink) $serverLink")
                }
            }
        }
        return MovieLoadResponse(title, url, this.name, TvType.JAV, watchlink.distinct().toJson(), poster, year, descript, null, null)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data == "about:blank") return false
        if (data.isEmpty()) return false

        mapper.readValue<List<String>>(data).forEach { link ->
            val url = fixUrl(link.trim())
            Log.i(this.name, "Result => (url) $url")
            when {
                url.startsWith("https://opmovie.xyz") -> {
                    val ext = XStreamCdn()
                    ext.domainUrl = "opmovie.xyz"
                    ext.getSafeUrl(url, url)?.forEach {
                        Log.i(this.name, "Result => (xtream) ${it.url}")
                        callback.invoke(it)
                    }
                }
                else -> {
                    loadExtractor(url, mainUrl, callback)
                }
            }
        }
        return false
    }
}