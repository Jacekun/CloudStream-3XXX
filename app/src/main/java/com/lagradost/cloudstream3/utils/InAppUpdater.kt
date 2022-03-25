package com.lagradost.cloudstream3.utils

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.concurrent.thread

class InAppUpdater {
    companion object {
        // === IN APP UPDATER ===
        data class GithubAsset(
            @JsonProperty("name") val name: String,
            @JsonProperty("size") val size: Int, // Size bytes
            @JsonProperty("browser_download_url") val browser_download_url: String, // download link
            @JsonProperty("content_type") val content_type: String, // application/vnd.android.package-archive
        )

        data class GithubRelease(
            @JsonProperty("tag_name") val tag_name: String, // Version code
            @JsonProperty("body") val body: String, // Desc
            @JsonProperty("assets") val assets: List<GithubAsset>,
            @JsonProperty("target_commitish") val target_commitish: String, // branch
            @JsonProperty("prerelease") val prerelease: Boolean,
        )

        data class GithubObject(
            @JsonProperty("sha") val sha: String, // sha 256 hash
            @JsonProperty("type") val type: String, // object type
            @JsonProperty("url") val url: String,
        )

        data class GithubTag(
            @JsonProperty("object") val github_object: GithubObject,
        )

        data class Update(
            @JsonProperty("shouldUpdate") val shouldUpdate: Boolean,
            @JsonProperty("updateURL") val updateURL: String?,
            @JsonProperty("updateVersion") val updateVersion: String?,
            @JsonProperty("changelog") val changelog: String?,
        )

        private val mapper = JsonMapper.builder().addModule(KotlinModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()

        private fun Activity.getAppUpdate(): Update {
            return try {
                val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
                if (settingsManager.getBoolean(getString(R.string.prerelease_update_key), false)) {
                    getPreReleaseUpdate()
                } else {
                    getReleaseUpdate()
                }
            } catch (e: Exception) {
                println(e)
                Update(false, null, null, null)
            }
        }

        private fun Activity.getReleaseUpdate(): Update {
            val url = "https://api.github.com/repos/killerDogeEmpire/CloudStream-3XXX/releases"
            val headers = mapOf("Accept" to "application/vnd.github.v3+json")
            val response =
                mapper.readValue<List<GithubRelease>>(runBlocking {
                    app.get(
                        url,
                        headers = headers
                    ).text
                })

            val currentVersion = packageName?.let {
                packageManager.getPackageInfo(
                    it,
                    0
                )
            }
            Log.i("d", "Result => (currentVersion) ${currentVersion?.versionCode}")

            val versionRegex = Regex("""(?<=r\.)(.*\d+)(?=\.-release)""")
            val found = response.filter { !it.prerelease }
                .filter { it.assets.any { a -> a.content_type.equals("application/vnd.android.package-archive") } }
                .toList()
                .maxByOrNull { it.tag_name.replace("jav_r", "").trim().toInt() }
            Log.i("d", "Result => (found) ${found?.toJson()}")

            val foundAsset = found?.assets?.getOrNull(0)
            foundAsset?.name?.let { assetName ->
                val foundVersion = when (assetName.isNotEmpty()) {
                    true -> {
                        Log.i("d", "Result => (assetName) $assetName")
                        val code = assetName.let { it1 ->
                            versionRegex.find(
                                it1
                        )?.groupValues?.get(1) }
                        code?.toIntOrNull()
                    }
                    false -> null
                }
                Log.i("d", "Result => (foundVersion) ${foundVersion}")
                if (foundVersion != null && currentVersion != null) {
                    val shouldUpdate = foundVersion > currentVersion.versionCode
                    Log.i("d", "Result => (update found shouldUpdate) ${shouldUpdate}")
                    return Update(shouldUpdate, foundAsset.browser_download_url, foundVersion.toString(), found.body)
                }
            }
            return Update(false, null, null, null)
        }

        private fun Activity.getPreReleaseUpdate(): Update = runBlocking {
            val tagUrl = "https://api.github.com/repos/KillerDogeEmpire/CloudStream-3XXX/git/ref/tags/pre-release"
            val releaseUrl = "https://api.github.com/repos/KillerDogeEmpire/CloudStream-3XXX/releases"
            val headers = mapOf("Accept" to "application/vnd.github.v3+json")
            val response =
                mapper.readValue<List<GithubRelease>>(app.get(releaseUrl, headers = headers).text)

            val found =
                response.lastOrNull { rel ->
                    rel.prerelease
                }
            val foundAsset = found?.assets?.getOrNull(0)

            val tagResponse =
                mapper.readValue<GithubTag>(app.get(tagUrl, headers = headers).text)

            val shouldUpdate =
                (getString(R.string.prerelease_commit_hash) != tagResponse.github_object.sha)

            return@runBlocking if (foundAsset != null) {
                Update(
                    shouldUpdate,
                    foundAsset.browser_download_url,
                    tagResponse.github_object.sha,
                    found.body
                )
            } else {
                Update(false, null, null, null)
            }
        }

        private fun Activity.downloadUpdate(url: String): Boolean {
            val downloadManager = getSystemService<DownloadManager>()!!

            val request = DownloadManager.Request(Uri.parse(url))
                .setMimeType("application/vnd.android.package-archive")
                .setTitle("CloudStream Update")
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "CloudStream.apk"
                )
                .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                .setAllowedOverRoaming(true)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

            val localContext = this

            val id = try {
                downloadManager.enqueue(request)
            } catch (e: Exception) {
                logError(e)
                showToast(this, R.string.storage_error, Toast.LENGTH_SHORT)
                -1
            }
            if (id == -1L) return true
            registerReceiver(
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        try {
                            val downloadId = intent?.getLongExtra(
                                DownloadManager.EXTRA_DOWNLOAD_ID, id
                            ) ?: id

                            val query = DownloadManager.Query()
                            query.setFilterById(downloadId)
                            val c = downloadManager.query(query)

                            if (c.moveToFirst()) {
                                val columnIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
                                if (DownloadManager.STATUS_SUCCESSFUL == c
                                        .getInt(columnIndex)
                                ) {
                                    c.getColumnIndex(DownloadManager.COLUMN_MEDIAPROVIDER_URI)
                                    val uri = Uri.parse(
                                        c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                                    )
                                    openApk(localContext, uri)
                                }
                            }
                        } catch (e: Exception) {
                            logError(e)
                        }
                    }
                }, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
            return true
        }

        fun openApk(context: Context, uri: Uri) {
            try {
                uri.path?.let {
                    val contentUri = FileProvider.getUriForFile(
                        context,
                        BuildConfig.APPLICATION_ID + ".provider",
                        File(it)
                    )
                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                        data = contentUri
                    }
                    context.startActivity(installIntent)
                }
            } catch (e: Exception) {
                logError(e)
            }
        }

        fun Activity.runAutoUpdate(checkAutoUpdate: Boolean = true): Boolean {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)

            if (!checkAutoUpdate || settingsManager.getBoolean(
                    getString(R.string.auto_update_key),
                    true
                )
            ) {
                val update = getAppUpdate()
                if (update.shouldUpdate && update.updateURL != null) {
                    runOnUiThread {
                        try {
                            val currentVersion = packageName?.let {
                                packageManager.getPackageInfo(
                                    it,
                                    0
                                )
                            }

                            val builder: AlertDialog.Builder = AlertDialog.Builder(this)
                            builder.setTitle(
                                getString(R.string.new_update_format).format(
                                    currentVersion?.versionCode,
                                    update.updateVersion
                                )
                            )
                            builder.setMessage("${update.changelog}")

                            val context = this
                            builder.apply {
                                setPositiveButton(R.string.update) { _, _ ->
                                    showToast(context, R.string.download_started, Toast.LENGTH_LONG)
                                    thread {
                                        val downloadStatus =
                                            normalSafeApiCall { context.downloadUpdate(update.updateURL) }
                                                ?: false
                                        if (!downloadStatus) {
                                            runOnUiThread {
                                                showToast(
                                                    context,
                                                    R.string.download_failed,
                                                    Toast.LENGTH_LONG
                                                )
                                            }
                                        }
                                    }
                                }

                                setNegativeButton(R.string.cancel) { _, _ -> }

                                if (checkAutoUpdate) {
                                    setNeutralButton(R.string.dont_show_again) { _, _ ->
                                        settingsManager.edit().putBoolean("auto_update", false)
                                            .apply()
                                    }
                                }
                            }
                            builder.show()
                        } catch (e: Exception) {
                            logError(e)
                        }
                    }
                    return true
                }
                return false
            }
            return false
        }
    }
}
