package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper

class SBfull : StreamSB() {
    override var mainUrl = "https://sbfull.com"
}

class StreamSB1 : StreamSB() {
    override var mainUrl = "https://sbplay1.com"
}

class StreamSB2 : StreamSB() {
    override var mainUrl = "https://sbplay2.com"
}

class StreamSB3 : StreamSB() {
    override var mainUrl = "https://sbplay3.com"
}

class StreamSB4 : StreamSB() {
    override var mainUrl = "https://cloudemb.com"
}

class StreamSB5 : StreamSB() {
    override var mainUrl = "https://sbplay.org"
}

class StreamSB6 : StreamSB() {
    override var mainUrl = "https://embedsb.com"
}

class StreamSB7 : StreamSB() {
    override var mainUrl = "https://pelistop.co"
}

class StreamSB8 : StreamSB() {
    override var mainUrl = "https://streamsb.net"
}

class StreamSB9 : StreamSB() {
    override var mainUrl = "https://sbplay.one"
}

class StreamSB10 : StreamSB() {
    override var mainUrl = "https://sbplay2.xyz"
}

// This is a modified version of https://github.com/jmir1/aniyomi-extensions/blob/master/src/en/genoanime/src/eu/kanade/tachiyomi/animeextension/en/genoanime/extractors/StreamSBExtractor.kt
// The following code is under the Apache License 2.0 https://github.com/jmir1/aniyomi-extensions/blob/master/LICENSE
open class StreamSB : ExtractorApi() {
    override var name = "StreamSB"
    override var mainUrl = "https://watchsb.com"
    override val requiresReferer = false

    private val hexArray = "0123456789ABCDEF".toCharArray()

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF

            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }

    data class Subs (
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String,
    )

    data class StreamData (
        @JsonProperty("file") val file: String,
        @JsonProperty("cdn_img") val cdnImg: String,
        @JsonProperty("hash") val hash: String,
        @JsonProperty("subs") val subs: List<Subs>?,
        @JsonProperty("length") val length: String,
        @JsonProperty("id") val id: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("backup") val backup: String,
    )

    data class Main (
        @JsonProperty("stream_data") val streamData: StreamData,
        @JsonProperty("status_code") val statusCode: Int,
    )

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val regexID = Regex("(embed-[a-zA-Z0-9]{0,8}[a-zA-Z0-9_-]+|\\/e\\/[a-zA-Z0-9]{0,8}[a-zA-Z0-9_-]+)")
        val id = regexID.findAll(url).map {
            it.value.replace(Regex("(embed-|\\/e\\/)"),"")
        }.first()
        val bytes = id.toByteArray()
        val bytesToHex = bytesToHex(bytes)
        val master = "$mainUrl/sources43/566d337678566f743674494a7c7c${bytesToHex.lowercase()}7c7c346b6767586d6934774855537c7c73747265616d7362/6565417268755339773461447c7c346133383438333436313335376136323337373433383634376337633465366534393338373136643732373736343735373237613763376334363733353737303533366236333463353333363534366137633763373337343732363536313664373336327c7c6b586c3163614468645a47617c7c73747265616d7362"
        val headers = mapOf(
            "watchsb" to "streamsb",
            )
        val urltext = app.get(master,
            headers = headers,
            allowRedirects = false
        ).text
        val mapped = urltext.let { parseJson<Main>(it) }
        val testurl = app.get(mapped.streamData.file, headers = headers).text
        // val urlmain = mapped.streamData.file.substringBefore("/hls/")
        if (urltext.contains("m3u8") && testurl.contains("EXTM3U"))
            return M3u8Helper.generateM3u8(
                name,
                mapped.streamData.file,
                url,
                headers = headers
            )
        return null
    }
}