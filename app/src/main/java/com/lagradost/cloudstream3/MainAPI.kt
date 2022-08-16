package com.lagradost.cloudstream3

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Base64.encodeToString
import androidx.annotation.WorkerThread
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.lagradost.cloudstream3.animeproviders.*
import com.lagradost.cloudstream3.liveproviders.EjaTv
import com.lagradost.cloudstream3.metaproviders.CrossTmdbProvider
import com.lagradost.cloudstream3.movieproviders.*
import com.lagradost.cloudstream3.providersnsfw.*
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.aniListApi
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.malApi
import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import okhttp3.Interceptor
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.absoluteValue

const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"

//val baseHeader = mapOf("User-Agent" to USER_AGENT)
val mapper = JsonMapper.builder().addModule(KotlinModule())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()!!

object APIHolder {
    val unixTime: Long
        get() = System.currentTimeMillis() / 1000L
    val unixTimeMS: Long
        get() = System.currentTimeMillis()

    private const val defProvider = 0

    val allProviders =
        arrayListOf(
            // Movie providers
            ElifilmsProvider(),
            EstrenosDoramasProvider(),
            PelisplusProvider(),
            PelisplusHDProvider(),
            PeliSmartProvider(),
            MeloMovieProvider(), // Captcha for links
            DoramasYTProvider(),
            CinecalidadProvider(),
            CuevanaProvider(),
            EntrepeliculasyseriesProvider(),
            PelisflixProvider(),
            SeriesflixProvider(),
            IHaveNoTvProvider(), // Documentaries provider
            VMoveeProvider(),
            AllMoviesForYouProvider(),
            VidEmbedProvider(),
            VfFilmProvider(),
            VfSerieProvider(),
            FrenchStreamProvider(),
            AsianLoadProvider(),
            AsiaFlixProvider(), // This should be removed in favor of asianembed.io, same source
            EjaTv(),
            BflixProvider(),
            FmoviesToProvider(),
            SflixProProvider(),
            FilmanProvider(),
            SflixProvider(),
            DopeboxProvider(),
            SolarmovieProvider(),
            PinoyMoviePediaProvider(),
            PinoyHDXyzProvider(),
            PinoyMoviesEsProvider(),
            TrailersTwoProvider(),
            TwoEmbedProvider(),
            DramaSeeProvider(),
            WatchAsianProvider(),
            DramaidProvider(),
            KdramaHoodProvider(),
            AkwamProvider(),
            MyCimaProvider(),
            CimaNowProvider(),
            EgyBestProvider(),
            FaselHDProvider(),
            SoaptwoDayProvider(),
            HDMProvider(),// disabled due to cloudflare
            TheFlixToProvider(),
            StreamingcommunityProvider(),
            TantifilmProvider(),
            CineblogProvider(),
            IlGenioDelloStreamingProvider(),
            AltadefinizioneProvider(),
            FilmpertuttiProvider(),
            HDMovie5(),
            RebahinProvider(),
            LayarKacaProvider(),
            HDTodayProvider(),
            OpenVidsProvider(),
            IdlixProvider(),
            MultiplexProvider(),
            VidSrcProvider(),
            UakinoProvider(),
            PhimmoichillProvider(),
            HDrezkaProvider(),
            YomoviesProvider(),
            SuperStream(),
            KisskhProvider(),

            // Metadata providers
            //TmdbProvider(),
            CrossTmdbProvider(),

            // Anime providers
            WatchCartoonOnlineProvider(),
            GogoanimeProvider(),
            AllAnimeProvider(),
            AnimekisaProvider(),
            //ShiroProvider(), // v2 fucked me
            AnimeFlickProvider(),
            AnimeflvnetProvider(),
            AnimefenixProvider(),
            AnimeflvIOProvider(),
            JKAnimeProvider(),
            TenshiProvider(),
            WcoProvider(),
            AnimePaheProvider(),
            NineAnimeProvider(),
            AnimeWorldProvider(),
            AnimeSaturnProvider(),
            AniPlayProvider(),
            ZoroProvider(),
            DubbedAnimeProvider(),
            MonoschinosProvider(),
            MundoDonghuaProvider(),
            KawaiifuProvider(), // disabled due to cloudflare
            NeonimeProvider(),
            KuramanimeProvider(),
            OploverzProvider(),
            GomunimeProvider(),
            NontonAnimeIDProvider(),
            KuronimeProvider(),
            OtakudesuProvider(),
            AnimeIndoProvider(),
            AnimeSailProvider(),
            TocanimeProvider(),
            //MultiAnimeProvider(),
            NginxProvider(),
            WcofunProvider(),
            OlgplyProvider(),
            AniflixProvider(),
            KimCartoonProvider(),
            XcineProvider(),

            // Additional providers
            KrunchyProvider(),

            // All of NSFW sources
            Javhdicu(),
            JavSubCo(),
            JavFreeSh(),
            OpJavCom(),
            Vlxx(),
            Xvideos(),
            Pornhub(),
            HentaiLa(),
            JKHentai(),
            Hanime(),
            HahoMoe(),
        )


    fun initAll() {
        for (api in allProviders) {
            api.init()
        }
        apiMap = null
    }

    fun String.capitalize(): String {
        return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    var apis: List<MainAPI> = arrayListOf()
    var apiMap: Map<String, Int>? = null

    private fun initMap() {
        if (apiMap == null)
            apiMap = apis.mapIndexed { index, api -> api.name to index }.toMap()
    }

    fun getApiFromName(apiName: String?): MainAPI {
        return getApiFromNameNull(apiName) ?: apis[defProvider]
    }

    fun getApiFromNameNull(apiName: String?): MainAPI? {
        if (apiName == null) return null
        initMap()
        return apiMap?.get(apiName)?.let { apis.getOrNull(it) }
    }

    fun getApiFromUrlNull(url: String?): MainAPI? {
        if (url == null) return null
        for (api in allProviders) {
            if (url.startsWith(api.mainUrl))
                return api
        }
        return null
    }

    fun getLoadResponseIdFromUrl(url: String, apiName: String): Int {
        return url.replace(getApiFromName(apiName).mainUrl, "").replace("/", "").hashCode()
    }

    fun LoadResponse.getId(): Int {
        return getLoadResponseIdFromUrl(url, apiName)
    }

    /**
     * Gets the website captcha token
     * discovered originally by https://github.com/ahmedgamal17
     * optimized by https://github.com/justfoolingaround
     *
     * @param url the main url, likely the same website you found the key from.
     * @param key used to fill https://www.google.com/recaptcha/api.js?render=....
     *
     * @param referer the referer for the google.com/recaptcha/api.js... request, optional.
     * */

    // Try document.select("script[src*=https://www.google.com/recaptcha/api.js?render=]").attr("src").substringAfter("render=")
    // To get the key
    suspend fun getCaptchaToken(url: String, key: String, referer: String? = null): String? {
        val uri = Uri.parse(url)
        val domain = encodeToString(
            (uri.scheme + "://" + uri.host + ":443").encodeToByteArray(),
            0
        ).replace("\n", "").replace("=", ".")

        val vToken =
            app.get(
                "https://www.google.com/recaptcha/api.js?render=$key",
                referer = referer,
                cacheTime = 0
            )
                .text
                .substringAfter("releases/")
                .substringBefore("/")
        val recapToken =
            app.get("https://www.google.com/recaptcha/api2/anchor?ar=1&hl=en&size=invisible&cb=cs3&k=$key&co=$domain&v=$vToken")
                .document
                .selectFirst("#recaptcha-token")?.attr("value")
        if (recapToken != null) {
            return app.post(
                "https://www.google.com/recaptcha/api2/reload?k=$key",
                data = mapOf(
                    "v" to vToken,
                    "k" to key,
                    "c" to recapToken,
                    "co" to domain,
                    "sa" to "",
                    "reason" to "q"
                ), cacheTime = 0
            ).text
                .substringAfter("rresp\",\"")
                .substringBefore("\"")
        }
        return null
    }

    fun Context.getApiSettings(): HashSet<String> {
        //val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)

        val hashSet = HashSet<String>()
        val activeLangs = getApiProviderLangSettings()
        hashSet.addAll(apis.filter { activeLangs.contains(it.lang) }.map { it.name })

        /*val set = settingsManager.getStringSet(
            this.getString(R.string.search_providers_list_key),
            hashSet
        )?.toHashSet() ?: hashSet

        val list = HashSet<String>()
        for (name in set) {
            val api = getApiFromNameNull(name) ?: continue
            if (activeLangs.contains(api.lang)) {
                list.add(name)
            }
        }*/
        //if (list.isEmpty()) return hashSet
        //return list
        return hashSet
    }

    fun Context.getApiDubstatusSettings(): HashSet<DubStatus> {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val hashSet = HashSet<DubStatus>()
        hashSet.addAll(DubStatus.values())
        val list = settingsManager.getStringSet(
            this.getString(R.string.display_sub_key),
            hashSet.map { it.name }.toMutableSet()
        ) ?: return hashSet

        val names = DubStatus.values().map { it.name }.toHashSet()
        //if(realSet.isEmpty()) return hashSet

        return list.filter { names.contains(it) }.map { DubStatus.valueOf(it) }.toHashSet()
    }

    fun Context.getApiProviderLangSettings(): HashSet<String> {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val hashSet = HashSet<String>()
        hashSet.add("en") // def is only en
        val list = settingsManager.getStringSet(
            this.getString(R.string.provider_lang_key),
            hashSet.toMutableSet()
        )

        if (list.isNullOrEmpty()) return hashSet
        return list.toHashSet()
    }

    fun Context.getApiTypeSettings(): HashSet<TvType> {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val hashSet = HashSet<TvType>()
        hashSet.addAll(TvType.values())
        val list = settingsManager.getStringSet(
            this.getString(R.string.search_types_list_key),
            hashSet.map { it.name }.toMutableSet()
        )

        if (list.isNullOrEmpty()) return hashSet

        val names = TvType.values().map { it.name }.toHashSet()
        val realSet = list.filter { names.contains(it) }.map { TvType.valueOf(it) }.toHashSet()
        if (realSet.isEmpty()) return hashSet

        return realSet
    }

    fun Context.updateHasTrailers() {
        LoadResponse.isTrailersEnabled = getHasTrailers()
    }

    private fun Context.getHasTrailers(): Boolean {
        if (this.isTvSettings()) return false
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        return settingsManager.getBoolean(this.getString(R.string.show_trailers_key), true)
    }

    fun Context.filterProviderByPreferredMedia(hasHomePageIsRequired: Boolean = true): List<MainAPI> {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val currentPrefMedia =
            settingsManager.getInt(this.getString(R.string.prefer_media_type_key), 0)
        val langs = this.getApiProviderLangSettings()
        val allApis = apis.filter { langs.contains(it.lang) }
            .filter { api -> api.hasMainPage || !hasHomePageIsRequired }
        return if (currentPrefMedia < 1) {
            allApis
        } else {
            // Filter API depending on preferred media type
            val listEnumAnime = listOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)
            val listEnumMovieTv =
                listOf(TvType.Movie, TvType.TvSeries, TvType.Cartoon, TvType.AsianDrama)
            val listEnumDoc = listOf(TvType.Documentary)
            val mediaTypeList = when (currentPrefMedia) {
                2 -> listEnumAnime
                3 -> listEnumDoc
                4 -> listOf(TvType.JAV, TvType.Hentai, TvType.XXX)
                else -> listEnumMovieTv
            }
            allApis.filter { api -> api.supportedTypes.any { it in mediaTypeList } }
        }
    }
    fun Context.filterProviderChoicesByPreferredMedia(currentPrefMedia: Int): List<Pair<Int, List<TvType>>> {
        return when (currentPrefMedia) {
            1 -> listOf(
                Pair(R.string.movies, listOf(TvType.Movie)),
                Pair(R.string.tv_series, listOf(TvType.TvSeries, TvType.Documentary)),
                Pair(R.string.cartoons, listOf(TvType.Cartoon)),
                Pair(R.string.torrent, listOf(TvType.Torrent)),
                Pair(R.string.asian_drama, listOf(TvType.AsianDrama))
            )
            2 -> listOf(
                Pair(R.string.anime, listOf(TvType.Anime, TvType.OVA, TvType.AnimeMovie)),
                Pair(R.string.torrent, listOf(TvType.Torrent))
            )
            3 -> listOf(
                Pair(R.string.jav, listOf(TvType.JAV)),
                Pair(R.string.hentai, listOf(TvType.Hentai)),
                Pair(R.string.xxx, listOf(TvType.XXX))
            )
            else -> listOf(
                Pair(R.string.movies, listOf(TvType.Movie)),
                Pair(R.string.tv_series, listOf(TvType.TvSeries, TvType.Documentary)),
                Pair(R.string.cartoons, listOf(TvType.Cartoon)),
                Pair(R.string.anime, listOf(TvType.Anime, TvType.OVA, TvType.AnimeMovie)),
                Pair(R.string.torrent, listOf(TvType.Torrent)),
                Pair(R.string.asian_drama, listOf(TvType.AsianDrama)),
                Pair(R.string.jav, listOf(TvType.JAV)),
                Pair(R.string.hentai, listOf(TvType.Hentai)),
                Pair(R.string.xxx, listOf(TvType.XXX))
            )
        }
    }
    fun Context.filterSearchResultByFilmQuality(data: List<SearchResponse>): List<SearchResponse> {
        // Filter results omitting entries with certain quality
        if (data.isNotEmpty()) {
            val filteredSearchQuality = PreferenceManager.getDefaultSharedPreferences(this)
                ?.getStringSet(getString(R.string.pref_filter_search_quality_key), setOf())
                ?.mapNotNull { entry ->
                    entry.toIntOrNull() ?: return@mapNotNull null
                } ?: listOf()
            if (filteredSearchQuality.isNotEmpty()) {
                return data.filter { item ->
                    val searchQualVal = item.quality?.ordinal ?: -1
                    //Log.i("filterSearch", "QuickSearch item => ${item.toJson()}")
                    !filteredSearchQuality.contains(searchQualVal)
                }
            }
        }
        return data
    }

    fun Context.filterHomePageListByFilmQuality(data: HomePageList): HomePageList {
        // Filter results omitting entries with certain quality
        if (data.list.isNotEmpty()) {
            val filteredSearchQuality = PreferenceManager.getDefaultSharedPreferences(this)
                ?.getStringSet(getString(R.string.pref_filter_search_quality_key), setOf())
                ?.mapNotNull { entry ->
                    entry.toIntOrNull() ?: return@mapNotNull null
                } ?: listOf()
            if (filteredSearchQuality.isNotEmpty()) {
                return HomePageList(
                    name = data.name,
                    isHorizontalImages = data.isHorizontalImages,
                    list = data.list.filter { item ->
                        val searchQualVal = item.quality?.ordinal ?: -1
                        //Log.i("filterSearch", "QuickSearch item => ${item.toJson()}")
                        !filteredSearchQuality.contains(searchQualVal)
                    }
                )
            }
        }
        return data
    }
}


/*
0 = Site not good
1 = All good
2 = Slow, heavy traffic
3 = restricted, must donate 30 benenes to use
 */
const val PROVIDER_STATUS_KEY = "PROVIDER_STATUS_KEY"
const val PROVIDER_STATUS_URL =
    "https://raw.githubusercontent.com/LagradOst/CloudStream-3/master/docs/providers.json"
const val PROVIDER_STATUS_BETA_ONLY = 3
const val PROVIDER_STATUS_SLOW = 2
const val PROVIDER_STATUS_OK = 1
const val PROVIDER_STATUS_DOWN = 0

data class ProvidersInfoJson(
    @JsonProperty("name") var name: String,
    @JsonProperty("url") var url: String,
    @JsonProperty("credentials") var credentials: String? = null,
    @JsonProperty("status") var status: Int,
)


data class MainPageData(
    val name: String,
    val data: String,
)

data class MainPageRequest(
    val name: String,
    val data: String,
)

/** return list of MainPageData with url to name, make for more readable code */
fun mainPageOf(vararg elements: Pair<String, String>): List<MainPageData> {
    return elements.map { (url, name) -> MainPageData(name = name, data = url) }
}

fun newHomePageResponse(
    name: String,
    list: List<SearchResponse>,
    hasNext: Boolean? = null
): HomePageResponse {
    return HomePageResponse(
        listOf(HomePageList(name, list)),
        hasNext = hasNext ?: list.isNotEmpty()
    )
}

fun newHomePageResponse(list: HomePageList, hasNext: Boolean? = null): HomePageResponse {
    return HomePageResponse(listOf(list), hasNext = hasNext ?: list.list.isNotEmpty())
}

fun newHomePageResponse(list: List<HomePageList>, hasNext: Boolean? = null): HomePageResponse {
    return HomePageResponse(list, hasNext = hasNext ?: list.isNotEmpty())
}

/**Every provider will **not** have try catch built in, so handle exceptions when calling these functions*/
abstract class MainAPI {
    companion object {
        var overrideData: HashMap<String, ProvidersInfoJson>? = null
    }

    fun init() {
        overrideData?.get(this.javaClass.simpleName)?.let { data ->
            overrideWithNewData(data)
        }
    }

    fun overrideWithNewData(data: ProvidersInfoJson) {
        if (!canBeOverridden) return
        this.name = data.name
        if (data.url.isNotBlank() && data.url != "NONE")
            this.mainUrl = data.url
        this.storedCredentials = data.credentials
    }

    open var name = "NONE"
    open var mainUrl = "NONE"
    open var storedCredentials: String? = null
    open var canBeOverridden: Boolean = true

    //open val uniqueId : Int by lazy { this.name.hashCode() } // in case of duplicate providers you can have a shared id

    open var lang = "en" // ISO_639_1 check SubtitleHelper

    /**If link is stored in the "data" string, so links can be instantly loaded*/
    open val instantLinkLoading = false

    /**Set false if links require referer or for some reason cant be played on a chromecast*/
    open val hasChromecastSupport = true

    /**If all links are encrypted then set this to false*/
    open val hasDownloadSupport = true

    /**Used for testing and can be used to disable the providers if WebView is not available*/
    open val usesWebView = false

    open val hasMainPage = false
    open val hasQuickSearch = false

    open val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Cartoon,
        TvType.Anime,
        TvType.OVA,
    )

    open val vpnStatus = VPNStatus.None
    open val providerType = ProviderType.DirectProvider

    open val mainPage = listOf(MainPageData("", ""))

    @WorkerThread
    open suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse? {
        throw NotImplementedError()
    }

    @WorkerThread
    open suspend fun search(query: String): List<SearchResponse>? {
        throw NotImplementedError()
    }

    @WorkerThread
    open suspend fun quickSearch(query: String): List<SearchResponse>? {
        throw NotImplementedError()
    }

    @WorkerThread
    /**
     * Based on data from search() or getMainPage() it generates a LoadResponse,
     * basically opening the info page from a link.
     * */
    open suspend fun load(url: String): LoadResponse? {
        throw NotImplementedError()
    }

    /**
     * Largely redundant feature for most providers.
     *
     * This job runs in the background when a link is playing in exoplayer.
     * First implemented to do polling for sflix to keep the link from getting expired.
     *
     * This function might be updated to include exoplayer timestamps etc in the future
     * if the need arises.
     * */
    @WorkerThread
    open suspend fun extractorVerifierJob(extractorData: String?) {
        throw NotImplementedError()
    }

    /**Callback is fired once a link is found, will return true if method is executed successfully*/
    @WorkerThread
    open suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        throw NotImplementedError()
    }

    /** An okhttp interceptor for used in OkHttpDataSource */
    open fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor? {
        return null
    }
}

/** Might need a different implementation for desktop*/
@SuppressLint("NewApi")
fun base64Decode(string: String): String {
    return String(base64DecodeArray(string), Charsets.ISO_8859_1)
}

@SuppressLint("NewApi")
fun base64DecodeArray(string: String): ByteArray {
    return try {
        android.util.Base64.decode(string, android.util.Base64.DEFAULT)
    } catch (e: Exception) {
        Base64.getDecoder().decode(string)
    }
}

@SuppressLint("NewApi")
fun base64Encode(array: ByteArray): String {
    return try {
        String(android.util.Base64.encode(array, android.util.Base64.NO_WRAP), Charsets.ISO_8859_1)
    } catch (e: Exception) {
        String(Base64.getEncoder().encode(array))
    }
}

class ErrorLoadingException(message: String? = null) : Exception(message)

fun MainAPI.fixUrlNull(url: String?): String? {
    if (url.isNullOrEmpty()) {
        return null
    }
    return fixUrl(url)
}

fun MainAPI.fixUrl(url: String): String {
    if (url.startsWith("http")) {
        return url
    }
    if (url.isEmpty()) {
        return ""
    }

    val startsWithNoHttp = url.startsWith("//")
    if (startsWithNoHttp) {
        return "https:$url"
    } else {
        if (url.startsWith('/')) {
            return mainUrl + url
        }
        return "$mainUrl/$url"
    }
}

fun sortUrls(urls: Set<ExtractorLink>): List<ExtractorLink> {
    return urls.sortedBy { t -> -t.quality }
}

fun sortSubs(subs: Set<SubtitleData>): List<SubtitleData> {
    return subs.sortedBy { it.name }
}

fun capitalizeString(str: String): String {
    return capitalizeStringNullable(str) ?: str
}

fun capitalizeStringNullable(str: String?): String? {
    if (str == null)
        return null
    return try {
        str.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    } catch (e: Exception) {
        str
    }
}

fun fixTitle(str: String): String {
    return str.split(" ").joinToString(" ") {
        it.lowercase()
            .replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else it }
    }
}

/** https://www.imdb.com/title/tt2861424/ -> tt2861424 */
fun imdbUrlToId(url: String): String? {
    return Regex("/title/(tt[0-9]*)").find(url)?.groupValues?.get(1)
        ?: Regex("tt[0-9]{5,}").find(url)?.groupValues?.get(0)
}

fun imdbUrlToIdNullable(url: String?): String? {
    if (url == null) return null
    return imdbUrlToId(url)
}

enum class ProviderType {
    // When data is fetched from a 3rd party site like imdb
    MetaProvider,

    // When all data is from the site
    DirectProvider,
}

enum class VPNStatus {
    None,
    MightBeNeeded,
    Torrent,
}

enum class ShowStatus {
    Completed,
    Ongoing,
}

enum class DubStatus(val id: Int) {
    Dubbed(1),
    Subbed(0),
    PremiumDub(99),
    PremiumSub(98)
}

enum class TvType {
    Movie,
    AnimeMovie,
    TvSeries,
    Cartoon,
    Anime,
    OVA,
    Torrent,
    Documentary,
    AsianDrama,
    Live,
    JAV,
    Hentai,
    XXX
}

// IN CASE OF FUTURE ANIME MOVIE OR SMTH
fun TvType.isMovieType(): Boolean {
    return this == TvType.Movie || this == TvType.AnimeMovie || this == TvType.Torrent || this == TvType.Live || this == TvType.JAV || this == TvType.XXX
}

fun TvType.isLiveStream(): Boolean {
    return this == TvType.Live
}

// returns if the type has an anime opening
fun TvType.isAnimeOp(): Boolean {
    return this == TvType.Anime || this == TvType.OVA
}

data class SubtitleFile(val lang: String, val url: String)

data class HomePageResponse(
    val items: List<HomePageList>,
    val hasNext: Boolean = false
)

data class HomePageList(
    val name: String,
    var list: List<SearchResponse>,
    val isHorizontalImages: Boolean = false
)

enum class SearchQuality(value: Int?) {
    //https://en.wikipedia.org/wiki/Pirated_movie_release_types
    Cam(0),
    CamRip(1),
    HdCam(2),
    Telesync(3), // TS
    WorkPrint(4),
    Telecine(5), // TC
    HQ(6),
    HD(7),
    HDR(8), // high dynamic range
    BlueRay(9),
    DVD(10),
    SD(11),
    FourK(12),
    UHD(13),
    SDR(14), // standard dynamic range
    WebRip(15)
}

/**Add anything to here if you find a site that uses some specific naming convention*/
fun getQualityFromString(string: String?): SearchQuality? {
    val check = (string ?: return null).trim().lowercase().replace(" ", "")

    return when (check) {
        "cam" -> SearchQuality.Cam
        "camrip" -> SearchQuality.CamRip
        "hdcam" -> SearchQuality.HdCam
        "hdtc" -> SearchQuality.HdCam
        "hdts" -> SearchQuality.HdCam
        "highquality" -> SearchQuality.HQ
        "hq" -> SearchQuality.HQ
        "highdefinition" -> SearchQuality.HD
        "hdrip" -> SearchQuality.HD
        "hd" -> SearchQuality.HD
        "hdtv" -> SearchQuality.HD
        "rip" -> SearchQuality.CamRip
        "telecine" -> SearchQuality.Telecine
        "tc" -> SearchQuality.Telecine
        "telesync" -> SearchQuality.Telesync
        "ts" -> SearchQuality.Telesync
        "dvd" -> SearchQuality.DVD
        "dvdrip" -> SearchQuality.DVD
        "dvdscr" -> SearchQuality.DVD
        "blueray" -> SearchQuality.BlueRay
        "bluray" -> SearchQuality.BlueRay
        "blu" -> SearchQuality.BlueRay
        "fhd" -> SearchQuality.HD
        "br" -> SearchQuality.BlueRay
        "standard" -> SearchQuality.SD
        "sd" -> SearchQuality.SD
        "4k" -> SearchQuality.FourK
        "uhd" -> SearchQuality.UHD // may also be 4k or 8k
        "blue" -> SearchQuality.BlueRay
        "wp" -> SearchQuality.WorkPrint
        "workprint" -> SearchQuality.WorkPrint
        "webrip" -> SearchQuality.WebRip
        "webdl" -> SearchQuality.WebRip
        "web" -> SearchQuality.WebRip
        "hdr" -> SearchQuality.HDR
        "sdr" -> SearchQuality.SDR
        else -> null
    }
}

interface SearchResponse {
    val name: String
    val url: String
    val apiName: String
    var type: TvType?
    var posterUrl: String?
    var posterHeaders: Map<String, String>?
    var id: Int?
    var quality: SearchQuality?
}

fun MainAPI.newMovieSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.Movie,
    fix: Boolean = true,
    initializer: MovieSearchResponse.() -> Unit = { },
): MovieSearchResponse {
    val builder = MovieSearchResponse(name, if (fix) fixUrl(url) else url, this.name, type)
    builder.initializer()

    return builder
}

fun MainAPI.newTvSeriesSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.TvSeries,
    fix: Boolean = true,
    initializer: TvSeriesSearchResponse.() -> Unit = { },
): TvSeriesSearchResponse {
    val builder = TvSeriesSearchResponse(name, if (fix) fixUrl(url) else url, this.name, type)
    builder.initializer()

    return builder
}


fun MainAPI.newAnimeSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.Anime,
    fix: Boolean = true,
    initializer: AnimeSearchResponse.() -> Unit = { },
): AnimeSearchResponse {
    val builder = AnimeSearchResponse(name, if (fix) fixUrl(url) else url, this.name, type)
    builder.initializer()

    return builder
}

fun SearchResponse.addQuality(quality: String) {
    this.quality = getQualityFromString(quality)
}

fun SearchResponse.addPoster(url: String?, headers: Map<String, String>? = null) {
    this.posterUrl = url
    this.posterHeaders = headers
}

fun LoadResponse.addPoster(url: String?, headers: Map<String, String>? = null) {
    this.posterUrl = url
    this.posterHeaders = headers
}

enum class ActorRole {
    Main,
    Supporting,
    Background,
}

data class Actor(
    val name: String,
    val image: String? = null,
)

data class ActorData(
    val actor: Actor,
    val role: ActorRole? = null,
    val roleString: String? = null,
    val voiceActor: Actor? = null,
)

data class AnimeSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType? = null,

    override var posterUrl: String? = null,
    var year: Int? = null,
    var dubStatus: EnumSet<DubStatus>? = null,

    var otherName: String? = null,
    var episodes: MutableMap<DubStatus, Int> = mutableMapOf(),

    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var posterHeaders: Map<String, String>? = null,
) : SearchResponse

fun AnimeSearchResponse.addDubStatus(status: DubStatus, episodes: Int? = null) {
    this.dubStatus = dubStatus?.also { it.add(status) } ?: EnumSet.of(status)
    if (this.type?.isMovieType() != true)
        if (episodes != null && episodes > 0)
            this.episodes[status] = episodes
}

fun AnimeSearchResponse.addDubStatus(isDub: Boolean, episodes: Int? = null) {
    addDubStatus(if (isDub) DubStatus.Dubbed else DubStatus.Subbed, episodes)
}

fun AnimeSearchResponse.addDub(episodes: Int?) {
    if (episodes == null || episodes <= 0) return
    addDubStatus(DubStatus.Dubbed, episodes)
}

fun AnimeSearchResponse.addSub(episodes: Int?) {
    if (episodes == null || episodes <= 0) return
    addDubStatus(DubStatus.Subbed, episodes)
}

fun AnimeSearchResponse.addDubStatus(
    dubExist: Boolean,
    subExist: Boolean,
    dubEpisodes: Int? = null,
    subEpisodes: Int? = null
) {
    if (dubExist)
        addDubStatus(DubStatus.Dubbed, dubEpisodes)

    if (subExist)
        addDubStatus(DubStatus.Subbed, subEpisodes)
}

fun AnimeSearchResponse.addDubStatus(status: String, episodes: Int? = null) {
    if (status.contains("(dub)", ignoreCase = true)) {
        addDubStatus(DubStatus.Dubbed, episodes)
    } else if (status.contains("(sub)", ignoreCase = true)) {
        addDubStatus(DubStatus.Subbed, episodes)
    }
}

data class TorrentSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType?,

    override var posterUrl: String?,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var posterHeaders: Map<String, String>? = null,
) : SearchResponse

data class MovieSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType? = null,

    override var posterUrl: String? = null,
    var year: Int? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var posterHeaders: Map<String, String>? = null,
) : SearchResponse

data class LiveSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType? = null,

    override var posterUrl: String? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var posterHeaders: Map<String, String>? = null,
    val lang: String? = null,
) : SearchResponse

data class TvSeriesSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType? = null,

    override var posterUrl: String? = null,
    val year: Int? = null,
    val episodes: Int? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var posterHeaders: Map<String, String>? = null,
) : SearchResponse

data class TrailerData(
    var mirros: List<ExtractorLink>,
    var subtitles: List<SubtitleFile> = emptyList(),
)

interface LoadResponse {
    var name: String
    var url: String
    var apiName: String
    var type: TvType
    var posterUrl: String?
    var year: Int?
    var plot: String?
    var rating: Int? // 0-10000
    var tags: List<String>?
    var duration: Int? // in minutes
    var trailers: MutableList<TrailerData>

    var recommendations: List<SearchResponse>?
    var actors: List<ActorData>?
    var comingSoon: Boolean
    var syncData: MutableMap<String, String>
    var posterHeaders: Map<String, String>?
    var backgroundPosterUrl: String?

    companion object {
        private val malIdPrefix = malApi.idPrefix
        private val aniListIdPrefix = aniListApi.idPrefix
        var isTrailersEnabled = true

        @JvmName("addActorNames")
        fun LoadResponse.addActors(actors: List<String>?) {
            this.actors = actors?.map { ActorData(Actor(it)) }
        }

        @JvmName("addActors")
        fun LoadResponse.addActors(actors: List<Pair<Actor, String?>>?) {
            this.actors = actors?.map { (actor, role) -> ActorData(actor, roleString = role) }
        }

        @JvmName("addActorsRole")
        fun LoadResponse.addActors(actors: List<Pair<Actor, ActorRole?>>?) {
            this.actors = actors?.map { (actor, role) -> ActorData(actor, role = role) }
        }

        @JvmName("addActorsOnly")
        fun LoadResponse.addActors(actors: List<Actor>?) {
            this.actors = actors?.map { actor -> ActorData(actor) }
        }

        fun LoadResponse.getMalId(): String? {
            return this.syncData[malIdPrefix]
        }

        fun LoadResponse.getAniListId(): String? {
            return this.syncData[aniListIdPrefix]
        }

        fun LoadResponse.addMalId(id: Int?) {
            this.syncData[malIdPrefix] = (id ?: return).toString()
        }

        fun LoadResponse.addAniListId(id: Int?) {
            this.syncData[aniListIdPrefix] = (id ?: return).toString()
        }

        fun LoadResponse.addImdbUrl(url: String?) {
            addImdbId(imdbUrlToIdNullable(url))
        }

        /**better to call addTrailer with mutible trailers directly instead of calling this multiple times*/
        suspend fun LoadResponse.addTrailer(trailerUrl: String?, referer: String? = null) {
            if (!isTrailersEnabled || trailerUrl == null) return
            val links = arrayListOf<ExtractorLink>()
            val subs = arrayListOf<SubtitleFile>()
            loadExtractor(trailerUrl, referer, { subs.add(it) }, { links.add(it) })
            this.trailers.add(TrailerData(links, subs))
        }

        fun LoadResponse.addTrailer(newTrailers: List<ExtractorLink>) {
            trailers.addAll(newTrailers.map { TrailerData(listOf(it)) })
        }

        suspend fun LoadResponse.addTrailer(trailerUrls: List<String>?, referer: String? = null) {
            if (!isTrailersEnabled || trailerUrls == null) return
            val trailers = trailerUrls.apmap { trailerUrl ->
                val links = arrayListOf<ExtractorLink>()
                val subs = arrayListOf<SubtitleFile>()
                loadExtractor(trailerUrl, referer, { subs.add(it) }, { links.add(it) })
                links to subs
            }.map { (links, subs) -> TrailerData(links, subs) }
            this.trailers.addAll(trailers)
        }

        fun LoadResponse.addImdbId(id: String?) {
            // TODO add imdb sync
        }

        fun LoadResponse.addTrackId(id: String?) {
            // TODO add trackt sync
        }

        fun LoadResponse.addkitsuId(id: String?) {
            // TODO add kitsu sync
        }

        fun LoadResponse.addTMDbId(id: String?) {
            // TODO add TMDb sync
        }

        fun LoadResponse.addRating(text: String?) {
            addRating(text.toRatingInt())
        }

        fun LoadResponse.addRating(value: Int?) {
            if ((value ?: return) < 0 || value > 10000) {
                return
            }
            this.rating = value
        }

        fun LoadResponse.addDuration(input: String?) {
            this.duration = getDurationFromString(input) ?: this.duration
        }
    }
}

fun getDurationFromString(input: String?): Int? {
    val cleanInput = input?.trim()?.replace(" ", "") ?: return null
    Regex("([0-9]*)h.*?([0-9]*)m").find(cleanInput)?.groupValues?.let { values ->
        if (values.size == 3) {
            val hours = values[1].toIntOrNull()
            val minutes = values[2].toIntOrNull()
            return if (minutes != null && hours != null) {
                hours * 60 + minutes
            } else null
        }
    }
    Regex("([0-9]*)m").find(cleanInput)?.groupValues?.let { values ->
        if (values.size == 2) {
            return values[1].toIntOrNull()
        }
    }
    return null
}

fun LoadResponse?.isEpisodeBased(): Boolean {
    if (this == null) return false
    return this is EpisodeResponse && this.type.isEpisodeBased()
}

fun LoadResponse?.isAnimeBased(): Boolean {
    if (this == null) return false
    return (this.type == TvType.Anime || this.type == TvType.OVA) // && (this is AnimeLoadResponse)
}

fun TvType?.isEpisodeBased(): Boolean {
    if (this == null) return false
    return (this == TvType.TvSeries || this == TvType.Anime)
}


data class NextAiring(
    val episode: Int,
    val unixTime: Long,
)

data class SeasonData(
    val season: Int,
    val name: String? = null,
)

interface EpisodeResponse {
    var showStatus: ShowStatus?
    var nextAiring: NextAiring?
    var seasonNames: List<SeasonData>?
}

@JvmName("addSeasonNamesString")
fun EpisodeResponse.addSeasonNames(names: List<String>) {
    this.seasonNames = if (names.isEmpty()) null else names.mapIndexed { index, s ->
        SeasonData(
            season = index + 1,
            s
        )
    }
}

@JvmName("addSeasonNamesSeasonData")
fun EpisodeResponse.addSeasonNames(names: List<SeasonData>) {
    this.seasonNames = names.ifEmpty { null }
}

data class TorrentLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    var magnet: String?,
    var torrent: String?,
    override var plot: String?,
    override var type: TvType = TvType.Torrent,
    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var rating: Int? = null,
    override var tags: List<String>? = null,
    override var duration: Int? = null,
    override var trailers: MutableList<TrailerData> = mutableListOf(),
    override var recommendations: List<SearchResponse>? = null,
    override var actors: List<ActorData>? = null,
    override var comingSoon: Boolean = false,
    override var syncData: MutableMap<String, String> = mutableMapOf(),
    override var posterHeaders: Map<String, String>? = null,
    override var backgroundPosterUrl: String? = null,
) : LoadResponse

data class AnimeLoadResponse(
    var engName: String? = null,
    var japName: String? = null,
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType,

    override var posterUrl: String? = null,
    override var year: Int? = null,

    var episodes: MutableMap<DubStatus, List<Episode>> = mutableMapOf(),
    override var showStatus: ShowStatus? = null,

    override var plot: String? = null,
    override var tags: List<String>? = null,
    var synonyms: List<String>? = null,

    override var rating: Int? = null,
    override var duration: Int? = null,
    override var trailers: MutableList<TrailerData> = mutableListOf(),
    override var recommendations: List<SearchResponse>? = null,
    override var actors: List<ActorData>? = null,
    override var comingSoon: Boolean = false,
    override var syncData: MutableMap<String, String> = mutableMapOf(),
    override var posterHeaders: Map<String, String>? = null,
    override var nextAiring: NextAiring? = null,
    override var seasonNames: List<SeasonData>? = null,
    override var backgroundPosterUrl: String? = null,
) : LoadResponse, EpisodeResponse

fun AnimeLoadResponse.addEpisodes(status: DubStatus, episodes: List<Episode>?) {
    if (episodes.isNullOrEmpty()) return
    this.episodes[status] = episodes
}

suspend fun MainAPI.newAnimeLoadResponse(
    name: String,
    url: String,
    type: TvType,
    comingSoonIfNone: Boolean = true,
    initializer: suspend AnimeLoadResponse.() -> Unit = { },
): AnimeLoadResponse {
    val builder = AnimeLoadResponse(name = name, url = url, apiName = this.name, type = type)
    builder.initializer()
    if (comingSoonIfNone) {
        builder.comingSoon = true
        for (key in builder.episodes.keys)
            if (!builder.episodes[key].isNullOrEmpty()) {
                builder.comingSoon = false
                break
            }
    }
    return builder
}

data class LiveStreamLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    var dataUrl: String,

    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var plot: String? = null,

    override var type: TvType = TvType.Live,
    override var rating: Int? = null,
    override var tags: List<String>? = null,
    override var duration: Int? = null,
    override var trailers: MutableList<TrailerData> = mutableListOf(),
    override var recommendations: List<SearchResponse>? = null,
    override var actors: List<ActorData>? = null,
    override var comingSoon: Boolean = false,
    override var syncData: MutableMap<String, String> = mutableMapOf(),
    override var posterHeaders: Map<String, String>? = null,
    override var backgroundPosterUrl: String? = null,
) : LoadResponse

data class MovieLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType,
    var dataUrl: String,

    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var plot: String? = null,

    override var rating: Int? = null,
    override var tags: List<String>? = null,
    override var duration: Int? = null,
    override var trailers: MutableList<TrailerData> = mutableListOf(),
    override var recommendations: List<SearchResponse>? = null,
    override var actors: List<ActorData>? = null,
    override var comingSoon: Boolean = false,
    override var syncData: MutableMap<String, String> = mutableMapOf(),
    override var posterHeaders: Map<String, String>? = null,
    override var backgroundPosterUrl: String? = null,
) : LoadResponse

suspend fun <T> MainAPI.newMovieLoadResponse(
    name: String,
    url: String,
    type: TvType,
    data: T?,
    initializer: suspend MovieLoadResponse.() -> Unit = { }
): MovieLoadResponse {
    // just in case
    if (data is String) return newMovieLoadResponse(
        name,
        url,
        type,
        dataUrl = data,
        initializer = initializer
    )
    val dataUrl = data?.toJson() ?: ""
    val builder = MovieLoadResponse(
        name = name,
        url = url,
        apiName = this.name,
        type = type,
        dataUrl = dataUrl,
        comingSoon = dataUrl.isBlank()
    )
    builder.initializer()
    return builder
}

suspend fun MainAPI.newMovieLoadResponse(
    name: String,
    url: String,
    type: TvType,
    dataUrl: String,
    initializer: suspend MovieLoadResponse.() -> Unit = { }
): MovieLoadResponse {
    val builder = MovieLoadResponse(
        name = name,
        url = url,
        apiName = this.name,
        type = type,
        dataUrl = dataUrl,
        comingSoon = dataUrl.isBlank()
    )
    builder.initializer()
    return builder
}

data class Episode(
    var data: String,
    var name: String? = null,
    var season: Int? = null,
    var episode: Int? = null,
    var posterUrl: String? = null,
    var rating: Int? = null,
    var description: String? = null,
    var date: Long? = null,
)

fun Episode.addDate(date: String?, format: String = "yyyy-MM-dd") {
    try {
        this.date = SimpleDateFormat(format)?.parse(date ?: return)?.time
    } catch (e: Exception) {
        logError(e)
    }
}

fun Episode.addDate(date: Date?) {
    this.date = date?.time
}

fun MainAPI.newEpisode(
    url: String,
    initializer: Episode.() -> Unit = { },
    fix: Boolean = true,
): Episode {
    val builder = Episode(
        data = if (fix) fixUrl(url) else url
    )
    builder.initializer()
    return builder
}

fun <T> MainAPI.newEpisode(
    data: T,
    initializer: Episode.() -> Unit = { }
): Episode {
    if (data is String) return newEpisode(
        url = data,
        initializer = initializer
    ) // just in case java is wack

    val builder = Episode(
        data = data?.toJson() ?: throw ErrorLoadingException("invalid newEpisode")
    )
    builder.initializer()
    return builder
}

data class TvSeriesLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType,
    var episodes: List<Episode>,

    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var plot: String? = null,

    override var showStatus: ShowStatus? = null,
    override var rating: Int? = null,
    override var tags: List<String>? = null,
    override var duration: Int? = null,
    override var trailers: MutableList<TrailerData> = mutableListOf(),
    override var recommendations: List<SearchResponse>? = null,
    override var actors: List<ActorData>? = null,
    override var comingSoon: Boolean = false,
    override var syncData: MutableMap<String, String> = mutableMapOf(),
    override var posterHeaders: Map<String, String>? = null,
    override var nextAiring: NextAiring? = null,
    override var seasonNames: List<SeasonData>? = null,
    override var backgroundPosterUrl: String? = null,
) : LoadResponse, EpisodeResponse

suspend fun MainAPI.newTvSeriesLoadResponse(
    name: String,
    url: String,
    type: TvType,
    episodes: List<Episode>,
    initializer: suspend TvSeriesLoadResponse.() -> Unit = { }
): TvSeriesLoadResponse {
    val builder = TvSeriesLoadResponse(
        name = name,
        url = url,
        apiName = this.name,
        type = type,
        episodes = episodes,
        comingSoon = episodes.isEmpty(),
    )
    builder.initializer()
    return builder
}

fun fetchUrls(text: String?): List<String> {
    if (text.isNullOrEmpty()) {
        return listOf()
    }
    val linkRegex =
        Regex("""(https?://(www\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b([-a-zA-Z0-9()@:%_+.~#?&/=]*))""")
    return linkRegex.findAll(text).map { it.value.trim().removeSurrounding("\"") }.toList()
}

fun String?.toRatingInt(): Int? =
    this?.replace(" ", "")?.trim()?.toDoubleOrNull()?.absoluteValue?.times(1000f)?.toInt()
