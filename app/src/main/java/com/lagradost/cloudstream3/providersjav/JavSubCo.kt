package com.lagradost.cloudstream3.providersjav

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.get
import com.lagradost.cloudstream3.network.text
import org.jsoup.Jsoup

class JavSubCo : MainAPI() {
    override val name: String
        get() = "JAVSub.co"

    override val mainUrl: String
        get() = "https://javsub.co"

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
        val html = get(url).text
        val document = Jsoup.parse(html).getElementsByTag("body")
            .select("div#content > div > main > section > div")
            .select("article")

        return document.map {

            val href = fixUrl(it.select("a")?.attr("href") ?: "")
            val title = it.select("header > h2")?.text() ?: "<No Title Found>"
            val image = it.select("div > figure").select("img")?.attr("src")?.trim('\'')
            val year = null

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
        //Log.i(this.name, "Result => ${body}")
        val poster = body.select("header#header")
            .select("div").select("figure").select("a")
            .select("img").attr("src")

        val content = body.select("div#content").select("div")
        val title = content.select("nav > p > span").text()

        val descript = content.select("main > article > div > div")
            .lastOrNull()?.select("div")?.text() ?: "<No Synopsis found>"
        //Log.i(this.name, "Result => ${descript}")
        val id = ""
        val re = Regex("[^0-9]")
        var yearString = content.select("main > article > div > div").last()
            ?.select("p")?.filter { it.text()?.contains("Release Date") == true }
            ?.get(0)?.text()
        yearString = yearString?.split(":")?.get(1)?.trim() ?: ""
        yearString = re.replace(yearString, "")
        val year = yearString?.takeLast(4)?.toIntOrNull()
        Log.i(this.name, "Result => (year) ${year} / (string) ${yearString}")

        return MovieLoadResponse(title, url, this.name, TvType.JAV, id, poster, year, descript, null, null)
    }
}