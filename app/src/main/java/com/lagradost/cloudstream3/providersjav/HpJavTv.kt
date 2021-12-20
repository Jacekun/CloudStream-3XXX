package com.lagradost.cloudstream3.providersjav

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import org.jsoup.Jsoup

class HpJavTv : MainAPI() {
    override val name: String get() = "HpJAV.tv"
    override val mainUrl: String get() = "https://hpjav.tv"
    override val supportedTypes: Set<TvType> get() = setOf(TvType.JAV)
    override val hasDownloadSupport: Boolean get() = false
    override val hasMainPage: Boolean get() = true
    override val hasQuickSearch: Boolean get() = false

    override fun getMainPage(): HomePageResponse {
        val html = app.get(mainUrl).text
        val document = Jsoup.parse(html)
        val all = ArrayList<HomePageList>()

        val mainbody = document.getElementsByTag("body")

        mainbody.select("div.container")?.forEach { it2 ->
            // Fetch row title
            val title = it2.select("div.category-count > h3")?.text() ?: "<No Name Row>"
            // Fetch list of items and map
            val inner = it2.select("div.post-list > div")
            if (inner != null) {
                val elements: List<SearchResponse> = inner!!.map {

                    val aa = it.select("div.video-item > div > a").firstOrNull()

                    val linkTitle = it.select("div.entry-title > a")
                    val linkCode = linkTitle?.attr("href")
                    val link = if (linkCode != null) { if (linkCode != "") { fixUrl("${this.mainUrl}${linkCode}") } else { "" } } else { "" }
                    val name = linkTitle?.text() ?: "<No Title>"
                    val image = if (link != "") { aa?.select("img")?.attr("data-original") } else { null }
                    val year = null
                    //Log.i(this.name, "Result => (image) ${image} , (linkCode) ${linkCode}, (link) ${link}")

                    MovieSearchResponse(
                        name,
                        link,
                        this.name,
                        TvType.JAV,
                        image,
                        year,
                        null,
                    )
                }.filter { it3 -> it3.url.isNotEmpty() }

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
        val html = app.get(url).text
        val document = Jsoup.parse(html).getElementsByTag("body")
            .select("div.container > div > div.post-list")
            .select("div.col-md-3.col-sm-6.col-xs-6")
        //Log.i(this.name, "Result => $document")
        return document.map {
            val content = it.select("div.video-item > div > a").firstOrNull()
            //Log.i(this.name, "Result => $content")
            val linkCode = content?.attr("href") ?: ""
            val href = fixUrl(linkCode)
            val imgContent = content?.select("img")
            val title = imgContent?.attr("alt") ?: "<No Title Found>"
            val image = imgContent?.attr("data-original")?.trim('\'')
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
        }
    }

    override fun load(url: String): LoadResponse {
        val response = app.get(url).text
        val document = Jsoup.parse(response)
        //Log.i(this.name, "Url => ${url}")
        val body = document.getElementsByTag("body")
            .select("div.video-box-ather.container > div.container > div")
            .select("div > div > img")?.firstOrNull()
        //Log.i(this.name, "Result => ${body}")
        // Video details
        val poster = body?.attr("src")
        val title = body?.attr("alt") ?: "<No Title>"
        val descript = "<No Synopsis found>"
        val year = null

        // Video link
        val id = ""

        return MovieLoadResponse(title, url, this.name, TvType.JAV, id, poster, year, descript, null, null)
    }
}