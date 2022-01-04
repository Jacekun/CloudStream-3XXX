package com.lagradost.cloudstream3.providersjav

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.HttpSession
import org.jsoup.Jsoup
import java.net.URLEncoder

class OpJavCom : MainAPI() {
    override val name: String get() = "OpJAV.com"
    override val mainUrl: String get() = "https://opjav.com"
    override val supportedTypes: Set<TvType> get() = setOf(TvType.JAV)
    override val hasDownloadSupport: Boolean get() = false
    override val hasMainPage: Boolean get() = true
    override val hasQuickSearch: Boolean get() = false

    override fun getMainPage(): HomePageResponse {
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

    override fun search(query: String): List<SearchResponse> {
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

    override fun load(url: String): LoadResponse {
        val response = app.get(url).text
        val doc = Jsoup.parse(response)
        //Log.i(this.name, "Result => (url) ${url}")
        val poster = doc.select("meta[itemprop=image]")?.get(1)?.attr("content")
        val title = doc.selectFirst("meta[property=og:title]")?.attr("content").toString()
        val descript = doc.selectFirst("meta[name=keywords]")?.attr("content")
        val year = doc.selectFirst("meta[itemprop=dateCreated]")?.attr("content")?.toIntOrNull()

        var watchlink = ""
        var mainLink = doc?.select("div.buttons.row > div > div > a")
            ?.attr("href") ?: ""
        Log.i(this.name, "Result => (mainLink) $mainLink")
        //TODO: Move to loadlinks
        val epsDoc = app.get(url = mainLink, referer = mainUrl).document
        val epsLink = epsDoc?.select("div.block.servers")
            ?.select("li")?.mapNotNull {
                it?.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            }
        epsLink?.forEach {
            val ajaxHead = mapOf(
                Pair("Origin", mainUrl),
                Pair("Referer", it)
            )
            val sess = HttpSession()
            val respAjax = sess.post("$mainUrl/ajax", headers = ajaxHead)
            Log.i(this.name, "Result => (respAjax) ${it}")
            Log.i(this.name, "Result => (respAjax) ${respAjax.statusCode}")
            Log.i(this.name, "Result => (respAjax) ${respAjax.text}")

        }
        return MovieLoadResponse(title, url, this.name, TvType.JAV, watchlink, poster, year, descript, null, null)
    }

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data == "about:blank") return false
        if (data.isEmpty()) return false
        //TODO: Load links
        return false
    }
}