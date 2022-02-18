package com.lagradost.cloudstream3.utils

import android.net.Uri
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.*
import com.lagradost.cloudstream3.mvvm.suspendSafeApiCall
import kotlinx.coroutines.delay
import org.jsoup.Jsoup

data class ExtractorLink(
    val source: String,
    var name: String,
    override val url: String,
    override val referer: String,
    val quality: Int,
    val isM3u8: Boolean = false,
    override val headers: Map<String, String> = mapOf(),
    /** Used for getExtractorVerifierJob() */
    val extractorData: String? = null
) : VideoDownloadManager.IDownloadableMinimum

data class ExtractorUri(
    val uri : Uri,
    val name : String,

    val basePath: String? = null,
    val relativePath: String? = null,
    val displayName: String? = null,

    val id : Int? = null,
    val parentId : Int? = null,
    val episode : Int? = null,
    val season : Int? = null,
    val headerName : String? = null,
    val tvType: TvType? = null,
)

data class ExtractorSubtitleLink(
    val name: String,
    override val url: String,
    override val referer: String,
    override val headers: Map<String, String> = mapOf()
) : VideoDownloadManager.IDownloadableMinimum

enum class Qualities(var value: Int) {
    Unknown(0),
    P360(-2), // 360p
    P480(-1), // 480p
    P720(1), // 720p
    P1080(2), // 1080p
    P1440(3), // 1440p
    P2160(4) // 4k or 2160p
}

fun getQualityFromName(qualityName: String): Int {
    return when (qualityName.replace("p", "").replace("P", "").trim()) {
        "360" -> Qualities.P360
        "480" -> Qualities.P480
        "720" -> Qualities.P720
        "1080" -> Qualities.P1080
        "1440" -> Qualities.P1440
        "2160" -> Qualities.P2160
        "4k" -> Qualities.P2160
        "4K" -> Qualities.P2160
        else -> Qualities.Unknown
    }.value
}

private val packedRegex = Regex("""eval\(function\(p,a,c,k,e,.*\)\)""")
fun getPacked(string: String): String? {
    return packedRegex.find(string)?.value
}

fun getAndUnpack(string: String): String {
    val packedText = getPacked(string)
    return JsUnpacker(packedText).unpack() ?: string
}

/**
 * Tries to load the appropriate extractor based on link, returns true if any extractor is loaded.
 * */
suspend fun loadExtractor(url: String, referer: String? = null, callback: (ExtractorLink) -> Unit) : Boolean {
    for (extractor in extractorApis) {
        if (url.startsWith(extractor.mainUrl)) {
            extractor.getSafeUrl(url, referer)?.forEach(callback)
            return true
        }
    }
    return false
}

val extractorApis: Array<ExtractorApi> = arrayOf(
    //AllProvider(),
    WcoStream(),
    Mp4Upload(),
    StreamTape(),
    MixDrop(),
    Mcloud(),
    XStreamCdn(),
    StreamSB(),
    Streamhub(),

    FEmbed(),
    FeHD(),
    Fplayer(),
    WatchSB(),
    Uqload(),
    Uqload1(),
    Evoload(),
    Evoload1(),
    VoeExtractor(),
    UpstreamExtractor(),

    Tomatomatela(),
    Cinestart(),
    OkRu(),

    // dood extractors
    DoodToExtractor(),
    DoodSoExtractor(),
    DoodLaExtractor(),
    DoodWsExtractor(),

    AsianLoad(),

    SBPlay(),
    SBPlay1(),
    SBPlay2(),
    SBPlay3(),

    JKhentaiExtractor(),

    //Jav extractors
    StreamLare(),
    PlayLtXyz()
)

fun getExtractorApiFromName(name: String): ExtractorApi {
    for (api in extractorApis) {
        if (api.name == name) return api
    }
    return extractorApis[0]
}

fun requireReferer(name: String): Boolean {
    return getExtractorApiFromName(name).requiresReferer
}

fun httpsify(url: String): String {
    return if (url.startsWith("//")) "https:$url" else url
}

suspend fun getPostForm(requestUrl : String, html : String) : String? {
    val document = Jsoup.parse(html)
    val inputs = document.select("Form > input")
    if (inputs.size < 4) return null
    var op: String? = null
    var id: String? = null
    var mode: String? = null
    var hash: String? = null

    for (input in inputs) {
        val value = input.attr("value") ?: continue
        when (input.attr("name")) {
            "op" -> op = value
            "id" -> id = value
            "mode" -> mode = value
            "hash" -> hash = value
            else -> Unit
        }
    }
    if (op == null || id == null || mode == null || hash == null) {
        return null
    }
    delay(5000) // ye this is needed, wont work with 0 delay

    val postResponse = app.post(
        requestUrl,
        headers = mapOf(
            "content-type" to "application/x-www-form-urlencoded",
            "referer" to requestUrl,
            "user-agent" to USER_AGENT,
            "accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
        ),
        data = mapOf("op" to op, "id" to id, "mode" to mode, "hash" to hash)
    ).text

    return postResponse
}

abstract class ExtractorApi {
    abstract val name: String
    abstract val mainUrl: String
    abstract val requiresReferer: Boolean

    suspend fun getSafeUrl(url: String, referer: String? = null): List<ExtractorLink>? {
        return suspendSafeApiCall { getUrl(url, referer) }
    }

    /**
     * Will throw errors, use getSafeUrl if you don't want to handle the exception yourself
     */
    abstract suspend fun getUrl(url: String, referer: String? = null): List<ExtractorLink>?

    open fun getExtractorUrl(id: String): String {
        return id
    }
}
