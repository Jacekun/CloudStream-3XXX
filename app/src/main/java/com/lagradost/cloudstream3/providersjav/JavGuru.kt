package com.lagradost.cloudstream3.providersjav

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.get
import com.lagradost.cloudstream3.network.text
import org.jsoup.Jsoup

class JavGuru : MainAPI() {
    override val name: String
        get() = "Jav Guru"

    override val mainUrl: String
        get() = "https://jav.guru"

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

        val mainbody = document.getElementsByTag("body").select("div#page")
            .select("div#content").select("div#primary")
            .select("main")

        //Log.i(this.name, "Main body => $mainbody")
        // Fetch row title
        val title = "Homepage"
        // Fetch list of items and map
        val inner = mainbody.select("div.row")
        //Log.i(this.name, "Inner => $inner")
        if (inner != null) {
            val elements: List<SearchResponse> = inner.map {

                val innerArticle = it.select("div.column")
                    .select("div.inside-article").select("div.imgg")
                //Log.i(this.name, "Inner content => $innerArticle")
                val aa = innerArticle.select("a").firstOrNull()
                val link = fixUrl(aa?.attr("href") ?: "")

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
        val html = get(url).text
        val document = Jsoup.parse(html).select("main.site-main").select("div.row")

        return document.map {
            val aa = it.select("div.column").select("div.inside-article")
                .select("div.imgg").select("a")
            val imgrow = aa.select("img")

            val href = fixUrl(aa.attr("href"))
            val title = imgrow.attr("alt")
            val image = imgrow.attr("src").trim('\'')
            val year = Regex("(?<=\\/)(.[0-9]{3})(?=\\/)")
                .find(image)?.groupValues?.get(1)?.toIntOrNull()

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
        val response = get(url).text
        val document = Jsoup.parse(response)

        //Log.i(this.name, "Url => ${url}")
        val body = document.getElementsByTag("body")
            .select("div#page")
            .select("div#content").select("div.content-area")
            .select("main").select("article")
            .select("div.inside-article").select("div.content")
            .select("div.posts")

        //Log.i(this.name, "Result => ${body}")
        val poster = body.select("div.large-screenshot").select("div.large-screenimg")
            .select("img").attr("src")
        val title = body.select("h1.titl").text()
        val descript = body.select("div.wp-content").select("p").firstOrNull()?.text()
        val id = ""
        val year = body.select("div.infometa > div.infoleft > ul > li")
            ?.get(1)?.text()?.takeLast(10)?.substring(0, 4)?.toIntOrNull()

        return MovieLoadResponse(title, url, this.name, TvType.JAV, id, poster, year, descript, null, null)
    }
}