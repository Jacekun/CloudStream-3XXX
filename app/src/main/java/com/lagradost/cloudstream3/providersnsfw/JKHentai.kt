package com.lagradost.cloudstream3.providersnsfw

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import java.util.*
import kotlin.collections.ArrayList

class JKHentai:MainAPI() {
    override var mainUrl = "https://jkhentai.net"
    override var name = "JKhentai"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Hentai)

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val urls = listOf(
            Pair("$mainUrl/lista/", "Hentais"),
            Pair("$mainUrl/estrenos-hentai", "Estrenos"),
            Pair("$mainUrl/genero/sin-censura/", "Sin censura"),
        )
        val items = ArrayList<HomePageList>()
        for (i in urls) {
            try {
                val doc = app.get(i.first).document
                val home = doc.select("div#box_movies .movie").mapNotNull {
                    val url = fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
                    val title = it.selectFirst("h2")?.text() ?: ""
                    val poster = it.selectFirst("img")?.attr("src")
                    AnimeSearchResponse(
                        name = title,
                        url = url,
                        apiName = this.name,
                        type = TvType.Hentai,
                        posterUrl = fixUrlNull(poster),
                        dubStatus = if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(DubStatus.Dubbed) else EnumSet.of(DubStatus.Subbed),
                    )
                }

                items.add(HomePageList(i.second, home))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    override suspend fun search(query: String): ArrayList<SearchResponse> {
        val url = "${mainUrl}/buscador.php?search=${query}"
        val doc = app.get(url).document
        val episodes = doc.select("div#box_movies .movie").mapNotNull {
            val href = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = it.selectFirst("h2")?.text() ?: ""
            val image = it.selectFirst("img")?.attr("src")
            AnimeSearchResponse(
                name = title,
                url = href,
                apiName = this.name,
                type = TvType.Hentai,
                posterUrl = fixUrlNull(image),
                dubStatus = if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(DubStatus.Dubbed) else EnumSet.of(DubStatus.Subbed),
            )
        }
        return ArrayList(episodes)
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst(".dataplus h1")?.text() ?: ""
        val description = doc.selectFirst("div.dataplus div#dato-2.data-content.tsll p")?.text()
            ?.replace("Sinopsis: ","")
        val poster = doc.selectFirst(".datos img")?.attr("src")
        val episodes = doc.select("div#cssmenu ul li.has-sub.open ul li").mapNotNull { li ->
            val href = fixUrlNull(li.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
            Episode(
                data = fixUrl(href),
            )
        }.reversed()
        val genre = doc.select(".xcsd strong a")
            .map { it?.text()?.trim().toString() }

        val status = when (doc.selectFirst(".data-content span.R")?.text()) {
            "EMISIÓN" -> ShowStatus.Ongoing
            "FINALIZADA" -> ShowStatus.Completed
            else -> null
        }

        return newAnimeLoadResponse(title, url, TvType.Hentai) {
            posterUrl = fixUrlNull(poster)
            addEpisodes(DubStatus.Subbed, episodes)
            showStatus = status
            plot = description
            tags = genre
        }
    }
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document.select(".player-content iframe").apmap {
            val url = it?.attr("src") ?: return@apmap null
            loadExtractor(
                url = url,
                referer = data,
                subtitleCallback = subtitleCallback,
                callback = callback
            )
        }
        return true
    }
}
