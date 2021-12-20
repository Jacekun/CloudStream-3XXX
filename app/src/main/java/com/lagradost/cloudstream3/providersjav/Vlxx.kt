package com.lagradost.cloudstream3.providersjav

import android.util.Log
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.movieproviders.SflixProvider
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.HttpSession
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.jsoup.Jsoup
import java.lang.Exception

class Vlxx : MainAPI() {
    override val name: String get() = "Vlxx"
    override val mainUrl: String get() = "https://vlxx.sex"
    override val supportedTypes: Set<TvType> get() = setOf(TvType.JAV)
    override val hasDownloadSupport: Boolean get() = false
    override val hasMainPage: Boolean get() = true
    override val hasQuickSearch: Boolean get() = false

    override fun getMainPage(): HomePageResponse {
        val html = app.get(mainUrl).text
        val document = Jsoup.parse(html)
        val all = ArrayList<HomePageList>()
        val title = "Homepage"
        val inner = document.select("#container .box .video-list")
        if (inner != null) {
            val elements: List<SearchResponse> = inner.map {
                val link = fixUrl(it.select("a")?.attr("href") ?: "")
                val imgArticle = it.select(".video-image").attr("src")
                val name = it.selectFirst(".video-name").text()
                val year = null
                MovieSearchResponse(
                    name,
                    link,
                    this.name,
                    TvType.JAV,
                    imgArticle,
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
        val url = "$mainUrl/search/${query}/"
        val html = app.get(url).text
        val document = Jsoup.parse(html)
        val list = document.select("#container .box .video-list")

        return list.map {

            val link = fixUrl(it.select("a")?.attr("href") ?: "")
            val imgArticle = it.select(".video-image").attr("src")
            val name = it.selectFirst(".video-name").text()
            val year = null
            MovieSearchResponse(
                name,
                link,
                this.name,
                TvType.JAV,
                imgArticle,
                year
            )
        }
    }

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val pathSplits = data.split("/")
        val id = pathSplits[pathSplits.size - 2]
        Log.d("Blue", "Data -> ${data} id -> ${id}")
        val res = app.post(
            "${mainUrl}/ajax.php",
            headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
            data = mapOf("id" to id, "server" to "1", "vlxx_server" to "1"),
            referer = mainUrl
        ).text
        val json = getParamFromJS(res, "var opts = {\\r\\n\\t\\t\\t\\t\\t\\tsources:", "}]")
        Log.d("Blue", "json ${json}")
        json?.let {

            val list = mapper.readValue<List<SflixProvider.Sources>>(it)
            Log.d("Blue", "list ${list}")
            list.forEach {
                it.file?.let { file ->
                    callback.invoke(
                        ExtractorLink(
                            file,
                            name = "${this.name} - ${it.label}",
                            url = file,
                            referer = data,
                            quality = it.label?.let { it1 -> getQualityFromName(it1) } ?: kotlin.run { Qualities.P720.value },
                            file.endsWith("m3u8")
                        )
                    )
                }
            }
        }
        return false

    }

    private fun getParamFromJS(str: String, key: String, keyEnd: String): String? {
        try {
            val firstIndex = str.indexOf(key) + key.length // 4 to index point to first char.
            val temp = str.substring(firstIndex)
            val lastIndex = temp.indexOf(keyEnd) + (keyEnd.length)
            val jsonConfig = temp.substring(0, lastIndex) //
            Log.d("Blue", "jsonConfig ${jsonConfig}")
            //console.log("json string ", jsonConfig)

            val re = jsonConfig.replace("\\r", "")
                .replace("\\t", "")
                .replace("\\\"", "\"")
                .replace("\\\\\\/", "/")
                .replace("\\n", "")

            return re
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null

    }

    override fun load(url: String): LoadResponse {
        val response = app.get(url).text
        val document = Jsoup.parse(response)
        val title = document?.selectFirst(".breadcrumb")?.text() ?: "<No Title>"
        val descript = document?.select(".video-content .content")?.text()
        val year = null
        val poster = document.select(".lcol img").attr("src")
        return MovieLoadResponse(
            title,
            url,
            this.name,
            TvType.JAV,
            url,
            poster,
            year,
            descript,
            null,
            null
        )
    }
}