package com.lagradost.cloudstream3.ui.settings

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.pm.PackageInfoCompat.getLongVersionCode
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.hippo.unifile.UniFile
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.APIHolder.filterProviderByPreferredMedia
import com.lagradost.cloudstream3.APIHolder.filterProviderChoicesByPreferredMedia
import com.lagradost.cloudstream3.APIHolder.getApiDubstatusSettings
import com.lagradost.cloudstream3.APIHolder.getApiProviderLangSettings
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.AcraApplication.Companion.removeKey
import com.lagradost.cloudstream3.CommonActivity.setLocale
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.DubStatus
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.OAuth2API
import com.lagradost.cloudstream3.syncproviders.OAuth2API.Companion.aniListApi
import com.lagradost.cloudstream3.syncproviders.OAuth2API.Companion.malApi
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.subtitles.ChromecastSubtitlesFragment
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.BackupUtils.backup
import com.lagradost.cloudstream3.utils.BackupUtils.restorePrompt
import com.lagradost.cloudstream3.utils.HOMEPAGE_API
import com.lagradost.cloudstream3.utils.InAppUpdater.Companion.runAutoUpdate
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showMultiDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showNginxTextInputDialog
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.SubtitleHelper.getFlagFromIso
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.setImage
import com.lagradost.cloudstream3.utils.VideoDownloadManager
import com.lagradost.cloudstream3.utils.VideoDownloadManager.getBasePath
import com.lagradost.cloudstream3.utils.VideoDownloadManager.getDownloadDir
import kotlinx.android.synthetic.main.logcat.*
import okhttp3.internal.closeQuietly
import java.io.BufferedReader
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import kotlinx.android.synthetic.main.main_settings.*
import java.io.File

class SettingsFragment : Fragment() {
    companion object {
        var beneneCount = 0

        fun PreferenceFragmentCompat?.getPref(id: Int): Preference? {
            if (this == null) return null

            return try {
                findPreference(getString(id))
            } catch (e: Exception) {
                logError(e)
                null
            }
        }

        fun getFolderSize(dir: File): Long {
            var size: Long = 0
            dir.listFiles()?.let {
                for (file in it) {
                    size += if (file.isFile) {
                        // System.out.println(file.getName() + " " + file.length());
                        file.length()
                    } else getFolderSize(file)
                }
            }

            return size
        }

        private fun Context.getLayoutInt(): Int {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
            return settingsManager.getInt(this.getString(R.string.app_layout_key), -1)
        }

        fun Context.isTvSettings(): Boolean {
            var value = getLayoutInt()
            if (value == -1) {
                value = if (isAutoTv()) 1 else 0
            }
            return value == 1 || value == 2
        }

        fun Context.isTrueTvSettings(): Boolean {
            var value = getLayoutInt()
            if (value == -1) {
                value = if (isAutoTv()) 1 else 0
            }
            return value == 1
        }

        fun Context.isEmulatorSettings(): Boolean {
            return getLayoutInt() == 2
        }

        private fun Context.isAutoTv(): Boolean {
            val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager?
            // AFT = Fire TV
            return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION || Build.MODEL.contains(
                "AFT"
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.main_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        fun navigate(id: Int) {
            activity?.navigate(id, Bundle())
        }

        val isTrueTv = context?.isTrueTvSettings() == true

        listOf(
            Pair(settings_player, R.id.action_navigation_settings_to_navigation_settings_player),
            Pair(settings_credits, R.id.action_navigation_settings_to_navigation_settings_account),
            Pair(settings_ui, R.id.action_navigation_settings_to_navigation_settings_ui),
            Pair(settings_lang, R.id.action_navigation_settings_to_navigation_settings_lang),
            Pair(settings_nginx, R.id.action_navigation_settings_to_navigation_settings_nginx),
            Pair(settings_updates, R.id.action_navigation_settings_to_navigation_settings_updates),
        ).forEach { (view, navigationId) ->
            view?.apply {
                setOnClickListener {
                    navigate(navigationId)
                }
                if (isTrueTv) {
                    isFocusable = true
                    isFocusableInTouchMode = true
                }
            }
        }
    }
}