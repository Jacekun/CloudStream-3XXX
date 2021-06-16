package com.lagradost.cloudstream3.ui.player

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.*
import android.content.Context.AUDIO_SERVICE
import android.content.pm.ActivityInfo
import android.content.res.Resources
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.*
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ProgressBar
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.transition.Fade
import androidx.transition.Transition
import androidx.transition.TransitionManager
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.C.TIME_UNSET
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.Util
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.MainActivity.Companion.isInPIPMode
import com.lagradost.cloudstream3.MainActivity.Companion.isInPlayer
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.UIHelper.getFocusRequest
import com.lagradost.cloudstream3.UIHelper.getNavigationBarHeight
import com.lagradost.cloudstream3.UIHelper.getStatusBarHeight
import com.lagradost.cloudstream3.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.UIHelper.hideSystemUI
import com.lagradost.cloudstream3.UIHelper.isCastApiAvailable
import com.lagradost.cloudstream3.UIHelper.popCurrentPage
import com.lagradost.cloudstream3.UIHelper.requestLocalAudioFocus
import com.lagradost.cloudstream3.UIHelper.showSystemUI
import com.lagradost.cloudstream3.UIHelper.toPx
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.mvvm.observeDirectly
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.ResultViewModel
import com.lagradost.cloudstream3.utils.CastHelper.startCast
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.utils.DataStoreHelper.setViewPos
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getId
import kotlinx.android.synthetic.main.fragment_player.*
import kotlinx.android.synthetic.main.player_custom_layout.*
import kotlinx.coroutines.*
import java.io.File
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.properties.Delegates


//http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4
const val STATE_RESUME_WINDOW = "resumeWindow"
const val STATE_RESUME_POSITION = "resumePosition"
const val STATE_PLAYER_FULLSCREEN = "playerFullscreen"
const val STATE_PLAYER_PLAYING = "playerOnPlay"
const val ACTION_MEDIA_CONTROL = "media_control"
const val EXTRA_CONTROL_TYPE = "control_type"
const val PLAYBACK_SPEED = "playback_speed"
const val RESIZE_MODE_KEY = "resize_mode" // Last used resize mode
const val PLAYBACK_SPEED_KEY = "playback_speed" // Last used playback speed

const val OPENING_PROCENTAGE = 50
const val AUTOLOAD_NEXT_EPISODE_PROCENTAGE = 80

enum class PlayerEventType(val value: Int) {
    Stop(-1),
    Pause(0),
    Play(1),
    SeekForward(2),
    SeekBack(3),
    SkipCurrentChapter(4),
    NextEpisode(5),
    PlayPauseToggle(6)
}

/*
data class PlayerData(
    val id: Int, // UNIQUE IDENTIFIER, USED FOR SET TIME, HASH OF slug+episodeIndex
    val titleName: String, // TITLE NAME
    val episodeName: String?, // EPISODE NAME, NULL IF MOVIE
    val episodeIndex: Int?, // EPISODE INDEX, NULL IF MOVIE
    val seasonIndex : Int?, // SEASON INDEX IF IT IS FOUND, EPISODE CAN BE GIVEN BUT SEASON IS NOT GUARANTEED
    val episodes : Int?, // MAX EPISODE
    //val seasons : Int?, // SAME AS SEASON INDEX, NOT GUARANTEED, SET TO 1
)*/
data class PlayerData(
    val episodeIndex: Int,
    val seasonIndex: Int?,
    val mirrorId: Int,
)

// YE, I KNOW, THIS COULD BE HANDLED A LOT BETTER
class PlayerFragment : Fragment() {
    private var isCurrentlyPlaying: Boolean = false
    private val mapper = JsonMapper.builder().addModule(KotlinModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()

    lateinit var apiName: String

    private var isFullscreen = false
    private var isPlayerPlaying = true
    private lateinit var viewModel: ResultViewModel
    private lateinit var playerData: PlayerData
    private var isLoading = true
    private var isShowing = true
    private lateinit var exoPlayer: SimpleExoPlayer

    //private var currentPercentage = 0
    // private var hasNextEpisode = true

    // val formatBuilder = StringBuilder()
    //  val formatter = Formatter(formatBuilder, Locale.getDefault())

    private var width = Resources.getSystem().displayMetrics.heightPixels
    private var height = Resources.getSystem().displayMetrics.widthPixels
    private var statusBarHeight by Delegates.notNull<Int>()
    private var navigationBarHeight by Delegates.notNull<Int>()

    private var isLocked = false

    private lateinit var settingsManager: SharedPreferences

    abstract class DoubleClickListener(private val ctx: PlayerFragment) : OnTouchListener {
        // The time in which the second tap should be done in order to qualify as
        // a double click

        private var doubleClickQualificationSpanInMillis: Long = 300L
        private var singleClickQualificationSpanInMillis: Long = 300L
        private var timestampLastClick: Long = 0
        private var timestampLastSingleClick: Long = 0
        private var clicksLeft = 0
        private var clicksRight = 0
        private var fingerLeftScreen = true
        abstract fun onDoubleClickRight(clicks: Int)
        abstract fun onDoubleClickLeft(clicks: Int)
        abstract fun onSingleClick()
        abstract fun onMotionEvent(event: MotionEvent)

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            onMotionEvent(event)
            if (event.action == MotionEvent.ACTION_UP) {
                fingerLeftScreen = true
            }
            if (event.action == MotionEvent.ACTION_DOWN) {
                fingerLeftScreen = false

                if ((SystemClock.elapsedRealtime() - timestampLastClick) < doubleClickQualificationSpanInMillis) {
                    if (event.rawX >= ctx.width / 2) {
                        clicksRight++
                        if (!ctx.isLocked && ctx.doubleTapEnabled) onDoubleClickRight(clicksRight)
                        if (!ctx.isShowing) onSingleClick()
                    } else {
                        clicksLeft++
                        if (!ctx.isLocked && ctx.doubleTapEnabled) onDoubleClickLeft(clicksLeft)
                        if (!ctx.isShowing) onSingleClick()
                    }
                } else if (clicksLeft == 0 && clicksRight == 0 && fingerLeftScreen) {
                    // onSingleClick()
                    // timestampLastSingleClick = SystemClock.elapsedRealtime()
                } else {
                    clicksLeft = 0
                    clicksRight = 0
                    val job = Job()
                    val uiScope = CoroutineScope(Dispatchers.Main + job)

                    fun check() {
                        if ((SystemClock.elapsedRealtime() - timestampLastSingleClick) > singleClickQualificationSpanInMillis && (SystemClock.elapsedRealtime() - timestampLastClick) > doubleClickQualificationSpanInMillis) {
                            timestampLastSingleClick = SystemClock.elapsedRealtime()
                            onSingleClick()
                        }
                    }

                    if (ctx.isShowing && !ctx.isLocked && ctx.doubleTapEnabled) {
                        uiScope.launch {
                            delay(doubleClickQualificationSpanInMillis + 1)
                            check()
                        }
                    } else {
                        check()
                    }
                }
                timestampLastClick = SystemClock.elapsedRealtime()

            }

            return true
        }
    }

    private fun onClickChange() {
        isShowing = !isShowing

        click_overlay?.visibility = if (isShowing) GONE else VISIBLE

        val titleMove = if (isShowing) 0f else -50.toPx.toFloat()
        ObjectAnimator.ofFloat(video_title, "translationY", titleMove).apply {
            duration = 200
            start()
        }

        ObjectAnimator.ofFloat(video_title_rez, "translationY", titleMove).apply {
            duration = 200
            start()
        }

        val playerBarMove = if (isShowing) 0f else 50.toPx.toFloat()
        ObjectAnimator.ofFloat(bottom_player_bar, "translationY", playerBarMove).apply {
            duration = 200
            start()
        }

        changeSkip()
        val fadeTo = if (isShowing) 1f else 0f
        val fadeAnimation = AlphaAnimation(1f - fadeTo, fadeTo)

        fadeAnimation.duration = 100
        fadeAnimation.fillAfter = true

        if (!isLocked) {
            shadow_overlay?.startAnimation(fadeAnimation)
        }
        video_holder?.startAnimation(fadeAnimation)

        //video_lock_holder?.startAnimation(fadeAnimation)
    }

    private fun forceLetters(inp: Int, letters: Int = 2): String {
        val added: Int = letters - inp.toString().length
        return if (added > 0) {
            "0".repeat(added) + inp.toString()
        } else {
            inp.toString()
        }
    }

    private fun convertTimeToString(time: Double): String {
        val sec = time.toInt()
        val rsec = sec % 60
        val min = ceil((sec - rsec) / 60.0).toInt()
        val rmin = min % 60
        val h = ceil((min - rmin) / 60.0).toInt()
        //int rh = h;// h % 24;
        return (if (h > 0) forceLetters(h) + ":" else "") + (if (rmin >= 0 || h >= 0) forceLetters(rmin) + ":" else "") + forceLetters(
            rsec
        )
    }

    private fun skipOP() {
        seekTime(85000L)
    }

    private var swipeEnabled = true //<settingsManager!!.getBoolean("swipe_enabled", true)
    private var swipeVerticalEnabled = true//settingsManager.getBoolean("swipe_vertical_enabled", true)
    private var playBackSpeedEnabled = true//settingsManager!!.getBoolean("playback_speed_enabled", false)
    private var playerResizeEnabled = true//settingsManager!!.getBoolean("player_resize_enabled", false)
    private var doubleTapEnabled = false
    private var useSystemBrightness = false

    private var skipTime = 0L
    private var prevDiffX = 0.0
    private var preventHorizontalSwipe = false
    private var hasPassedVerticalSwipeThreshold = false
    private var hasPassedSkipLimit = false

    private var isMovingStartTime = 0L
    private var currentX = 0F
    private var currentY = 0F
    private var cachedVolume = 0f
    private var isValidTouch = false

    fun handleMotionEvent(motionEvent: MotionEvent) {
        // TIME_UNSET   ==   -9223372036854775807L
        // No swiping on unloaded
        // https://exoplayer.dev/doc/reference/constant-values.html
        if (isLocked || exoPlayer.duration == TIME_UNSET || (!swipeEnabled && !swipeVerticalEnabled)) return


        val audioManager = activity?.getSystemService(AUDIO_SERVICE) as? AudioManager

        when (motionEvent.action) {
            MotionEvent.ACTION_DOWN -> {
                // SO YOU CAN PULL DOWN STATUSBAR OR NAVBAR
                if (motionEvent.rawY > statusBarHeight && motionEvent.rawX < width - navigationBarHeight) {
                    currentX = motionEvent.rawX
                    currentY = motionEvent.rawY
                    isValidTouch = true
                    //println("DOWN: " + currentX)
                    isMovingStartTime = exoPlayer.currentPosition
                } else {
                    isValidTouch = false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isValidTouch) return
                if (swipeVerticalEnabled) {
                    val distanceMultiplierY = 2F
                    val distanceY = (motionEvent.rawY - currentY) * distanceMultiplierY
                    val diffY = distanceY * 2.0 / height

                    // Forces 'smooth' moving preventing a bug where you
                    // can make it think it moved half a screen in a frame

                    if (abs(diffY) >= 0.2 && !hasPassedSkipLimit) {
                        hasPassedVerticalSwipeThreshold = true
                        preventHorizontalSwipe = true
                    }
                    if (hasPassedVerticalSwipeThreshold) {
                        if (currentX > width * 0.5) {
                            if (audioManager != null && progressBarLeftHolder != null) {
                                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

                                if (progressBarLeftHolder?.alpha ?: 0f <= 0f) {
                                    cachedVolume = currentVolume.toFloat() / maxVolume.toFloat()
                                }

                                progressBarLeftHolder?.alpha = 1f
                                val vol = minOf(1f,
                                    cachedVolume - diffY.toFloat() * 0.5f) // 0.05f *if (diffY > 0) 1 else -1
                                cachedVolume = vol
                                //progressBarRight?.progress = ((1f - alpha) * 100).toInt()

                                progressBarLeft?.max = 100 * 100
                                progressBarLeft?.progress = ((vol) * 100 * 100).toInt()

                                if (audioManager.isVolumeFixed) {
                                    // Lmao might earrape, we'll see in bug reports
                                    exoPlayer.volume = minOf(1f, maxOf(vol, 0f))
                                } else {
                                    // audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol*, 0)
                                    val desiredVol = (vol * maxVolume).toInt()
                                    if (desiredVol != currentVolume) {
                                        val newVolumeAdjusted =
                                            if (desiredVol < currentVolume) AudioManager.ADJUST_LOWER else AudioManager.ADJUST_RAISE

                                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, newVolumeAdjusted, 0)
                                    }
                                    //audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                                }
                                currentY = motionEvent.rawY
                            }
                        } else if (progressBarRightHolder != null) {
                            progressBarRightHolder?.alpha = 1f

                            if (useSystemBrightness) {
                                // https://developer.android.com/reference/android/view/WindowManager.LayoutParams#screenBrightness
                                val lp = activity?.window?.attributes
                                val currentBrightness = if (lp?.screenBrightness ?: -1.0f <= 0f) (android.provider.Settings.System.getInt(
                                    context?.contentResolver,
                                    android.provider.Settings.System.SCREEN_BRIGHTNESS
                                ) * (1 / 255).toFloat())
                                else lp?.screenBrightness!!

                                val alpha = minOf(
                                    maxOf(
                                        0.005f, // BRIGHTNESS_OVERRIDE_OFF doesn't seem to work
                                        currentBrightness - diffY.toFloat() * 0.5f
                                    ), 1.0f
                                )// 0.05f *if (diffY > 0) 1 else -1
                                lp?.screenBrightness = alpha
                                activity?.window?.attributes = lp

                                progressBarRight?.max = 100 * 100
                                progressBarRight?.progress = (alpha * 100 * 100).toInt()
                            }
                            else {
                                val alpha = minOf(0.95f,
                                    brightness_overlay.alpha + diffY.toFloat() * 0.5f) // 0.05f *if (diffY > 0) 1 else -1
                                brightness_overlay?.alpha = alpha

                                progressBarRight?.max = 100 * 100
                                progressBarRight?.progress = ((1f - alpha) * 100 * 100).toInt()
                            }

                            currentY = motionEvent.rawY
                        }
                    }
                }

                if (swipeEnabled) {
                    val distanceMultiplierX = 2F
                    val distanceX = (motionEvent.rawX - currentX) * distanceMultiplierX
                    val diffX = distanceX * 2.0 / width
                    if (abs(diffX - prevDiffX) > 0.5) {
                        return
                    }
                    prevDiffX = diffX

                    skipTime = ((exoPlayer.duration * (diffX * diffX) / 10) * (if (diffX < 0) -1 else 1)).toLong()
                    if (isMovingStartTime + skipTime < 0) {
                        skipTime = -isMovingStartTime
                    } else if (isMovingStartTime + skipTime > exoPlayer.duration) {
                        skipTime = exoPlayer.duration - isMovingStartTime
                    }
                    if ((abs(skipTime) > 3000 || hasPassedSkipLimit) && !preventHorizontalSwipe) {
                        hasPassedSkipLimit = true
                        val timeString =
                            "${convertTimeToString((isMovingStartTime + skipTime) / 1000.0)} [${(if (abs(skipTime) < 1000) "" else (if (skipTime > 0) "+" else "-"))}${
                                convertTimeToString(abs(skipTime / 1000.0))
                            }]"
                        timeText.alpha = 1f
                        timeText.text = timeString
                    } else {
                        timeText.alpha = 0f
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!isValidTouch) return
                isValidTouch = false
                val transition: Transition = Fade()
                transition.duration = 1000

                TransitionManager.beginDelayedTransition(player_holder, transition)

                if (abs(skipTime) > 7000 && !preventHorizontalSwipe && swipeEnabled) {
                    seekTo(skipTime + isMovingStartTime)
                    //exoPlayer.seekTo(maxOf(minOf(skipTime + isMovingStartTime, exoPlayer.duration), 0))
                }
                changeSkip()

                hasPassedSkipLimit = false
                hasPassedVerticalSwipeThreshold = false
                preventHorizontalSwipe = false
                prevDiffX = 0.0
                skipTime = 0

                timeText.animate().alpha(0f).setDuration(200)
                    .setInterpolator(AccelerateInterpolator()).start()
                progressBarRightHolder.animate().alpha(0f).setDuration(200)
                    .setInterpolator(AccelerateInterpolator()).start()
                progressBarLeftHolder.animate().alpha(0f).setDuration(200)
                    .setInterpolator(AccelerateInterpolator()).start()
                //val fadeAnimation = AlphaAnimation(1f, 0f)
                //fadeAnimation.duration = 100
                //fadeAnimation.fillAfter = true
                //progressBarLeftHolder.startAnimation(fadeAnimation)
                //progressBarRightHolder.startAnimation(fadeAnimation)
                //timeText.startAnimation(fadeAnimation)

            }
        }
    }

    @SuppressLint("SetTextI18n")
    fun changeSkip(position: Long? = null) {
        val data = localData

        if (this::exoPlayer.isInitialized && exoPlayer.currentPosition >= 0) {
            val percentage = ((position ?: exoPlayer.currentPosition) * 100 / exoPlayer.contentDuration).toInt()
            val hasNext = hasNextEpisode()

            if (percentage >= AUTOLOAD_NEXT_EPISODE_PROCENTAGE && hasNext) {
                val ep =
                    episodes[playerData.episodeIndex + 1]

                if ((allEpisodes[ep.id]?.size ?: 0) <= 0) {
                    viewModel.loadEpisode(ep, false) {
                        //NOTHING
                    }
                }
            }
            val nextEp = percentage >= OPENING_PROCENTAGE

            skip_op_text.text = if (nextEp) "Next Episode" else "Skip OP"
            val isVis =
                if (nextEp) hasNext //&& !isCurrentlySkippingEp
                else (data is AnimeLoadResponse && (data.type == TvType.Anime || data.type == TvType.ONA))
            skip_op.visibility = if (isVis) View.VISIBLE else View.GONE
        } else {
            if (data is AnimeLoadResponse) {
                val isVis = ((data.type == TvType.Anime || data.type == TvType.ONA))
                skip_op_text.text = "Skip OP"
                skip_op.visibility = if (isVis) View.VISIBLE else View.GONE
            }
        }
    }

    private fun seekTime(time: Long) {
        changeSkip()
        seekTo(exoPlayer.currentPosition + time)
    }

    private fun seekTo(time: Long) {
        val correctTime = maxOf(minOf(time, exoPlayer.duration), 0)
        exoPlayer.seekTo(correctTime)
        changeSkip(correctTime)
    }

    private var hasUsedFirstRender = false

    private fun releasePlayer() {
        savePos()
        isCurrentlyPlaying = false
        val alphaAnimation = AlphaAnimation(0f, 1f)
        alphaAnimation.duration = 100
        alphaAnimation.fillAfter = true
        video_go_back_holder?.visibility = VISIBLE

        overlay_loading_skip_button?.visibility = VISIBLE
        loading_overlay?.startAnimation(alphaAnimation)
        if (this::exoPlayer.isInitialized) {
            isPlayerPlaying = exoPlayer.playWhenReady
            playbackPosition = exoPlayer.currentPosition
            currentWindow = exoPlayer.currentWindowIndex
            exoPlayer.release()
        }
    }

    private class SettingsContentObserver(handler: Handler?, val activity: Activity) : ContentObserver(handler) {
        private val audioManager = activity.getSystemService(AUDIO_SERVICE) as? AudioManager
        override fun onChange(selfChange: Boolean) {
            val currentVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val progressBarRight = activity.findViewById<ProgressBar>(R.id.progressBarRight)
            if (currentVolume != null && maxVolume != null) {
                progressBarRight?.progress = currentVolume * 100 / maxVolume
            }
        }
    }

    private lateinit var volumeObserver: SettingsContentObserver

    companion object {
        fun newInstance(data: PlayerData, startPos: Long? = null) =
            PlayerFragment().apply {
                arguments = Bundle().apply {
                    //println(data)
                    putString("data", mapper.writeValueAsString(data))
                    println("PUT START: " + startPos)
                    if (startPos != null) {
                        putLong(STATE_RESUME_POSITION, startPos)
                    }
                }
            }
    }

    private fun savePos() {
        if (this::exoPlayer.isInitialized) {
            if (exoPlayer.duration > 0 && exoPlayer.currentPosition > 0) {
                context?.let { ctx ->
                    ctx.setViewPos(getEpisode()?.id, exoPlayer.currentPosition, exoPlayer.duration)
                    viewModel.reloadEpisodes(ctx)
                }
            }
        }
    }

    private val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    )

    private var localData: LoadResponse? = null

    private fun updateLock() {
        video_locked_img.setImageResource(if (isLocked) R.drawable.video_locked else R.drawable.video_unlocked)
        val color = if (isLocked) ContextCompat.getColor(requireContext(), R.color.videoColorPrimary)
        else Color.WHITE

        video_locked_text.setTextColor(color)
        video_locked_img.setColorFilter(color)

        val isClick = !isLocked

        exo_play.isClickable = isClick
        exo_pause.isClickable = isClick
        exo_ffwd.isClickable = isClick
        exo_rew.isClickable = isClick
        exo_prev.isClickable = isClick
        video_go_back.isClickable = isClick
        exo_progress.isClickable = isClick
        //next_episode_btt.isClickable = isClick
        playback_speed_btt.isClickable = isClick
        skip_op.isClickable = isClick
        resize_player.isClickable = isClick
        exo_progress.isEnabled = isClick
        player_media_route_button.isEnabled = isClick
        //video_go_back_holder2.isEnabled = isClick

        // Clickable doesn't seem to work on com.google.android.exoplayer2.ui.DefaultTimeBar
        //exo_progress.visibility = if (isLocked) INVISIBLE else VISIBLE

        val fadeTo = if (!isLocked) 1f else 0f
        val fadeAnimation = AlphaAnimation(1f - fadeTo, fadeTo)

        fadeAnimation.duration = 100
        fadeAnimation.fillAfter = true

        shadow_overlay.startAnimation(fadeAnimation)
    }

    private var resizeMode = 0
    private var playbackSpeed = 0f
    private var allEpisodes: HashMap<Int, ArrayList<ExtractorLink>> = HashMap()
    private var episodes: List<ResultEpisode> = ArrayList()
    var currentPoster: String? = null
    var currentHeaderName: String? = null

    //region PIP MODE
    private fun getPen(code: PlayerEventType): PendingIntent {
        return getPen(code.value)
    }

    private fun getPen(code: Int): PendingIntent {
        return PendingIntent.getBroadcast(
            activity,
            code,
            Intent("media_control").putExtra("control_type", code),
            0
        )
    }

    @SuppressLint("NewApi")
    private fun getRemoteAction(id: Int, title: String, event: PlayerEventType): RemoteAction {
        return RemoteAction(
            Icon.createWithResource(activity, id),
            title,
            title,
            getPen(event)
        )
    }

    @SuppressLint("NewApi")
    private fun updatePIPModeActions() {
        if (!isInPIPMode || !this::exoPlayer.isInitialized) return

        val actions: ArrayList<RemoteAction> = ArrayList()

        actions.add(getRemoteAction(R.drawable.go_back_30, "Go Back", PlayerEventType.SeekBack))

        if (exoPlayer.isPlaying) {
            actions.add(getRemoteAction(R.drawable.netflix_pause, "Pause", PlayerEventType.Pause))
        } else {
            actions.add(getRemoteAction(R.drawable.ic_baseline_play_arrow_24, "Play", PlayerEventType.Play))
        }

        actions.add(getRemoteAction(R.drawable.go_forward_30, "Go Forward", PlayerEventType.SeekForward))
        activity?.setPictureInPictureParams(PictureInPictureParams.Builder().setActions(actions).build())
    }

    private var receiver: BroadcastReceiver? = null
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        isInPIPMode = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            // Hide the full-screen UI (controls, etc.) while in picture-in-picture mode.
            player_holder.alpha = 0f
            receiver = object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    if (ACTION_MEDIA_CONTROL != intent.action) {
                        return
                    }
                    handlePlayerEvent(intent.getIntExtra(EXTRA_CONTROL_TYPE, 0))
                }
            }
            val filter = IntentFilter()
            filter.addAction(
                ACTION_MEDIA_CONTROL
            )
            activity?.registerReceiver(receiver, filter)
            updatePIPModeActions()
        } else {
            // Restore the full-screen UI.
            player_holder.alpha = 1f
            receiver?.let {
                activity?.unregisterReceiver(it)
            }
            activity?.hideSystemUI()
            this.view?.let { activity?.hideKeyboard(it) }
        }
    }

    private fun handlePlayerEvent(event: PlayerEventType) {
        handlePlayerEvent(event.value)
    }

    private fun handlePlayerEvent(event: Int) {
        when (event) {
            PlayerEventType.Play.value -> exoPlayer.play()
            PlayerEventType.Pause.value -> exoPlayer.pause()
            PlayerEventType.SeekBack.value -> seekTime(-30000L)
            PlayerEventType.SeekForward.value -> seekTime(30000L)
        }
    }
//endregion

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        settingsManager = PreferenceManager.getDefaultSharedPreferences(activity)
        swipeEnabled = settingsManager.getBoolean("swipe_enabled", true)
        swipeVerticalEnabled = settingsManager.getBoolean("swipe_vertical_enabled", true)
        playBackSpeedEnabled = settingsManager.getBoolean("playback_speed_enabled", false)
        playerResizeEnabled = settingsManager.getBoolean("player_resize_enabled", true)
        doubleTapEnabled = settingsManager.getBoolean("double_tap_enabled", false)

        isInPlayer = true // NEED REFERENCE TO MAIN ACTIVITY FOR PIP

        navigationBarHeight = requireContext().getNavigationBarHeight()
        statusBarHeight = requireContext().getStatusBarHeight()

        if (activity?.isCastApiAvailable() == true) {
            CastButtonFactory.setUpMediaRouteButton(activity, player_media_route_button)
            val castContext = CastContext.getSharedInstance(requireContext())

            if (castContext.castState != CastState.NO_DEVICES_AVAILABLE) player_media_route_button.visibility = VISIBLE
            castContext.addCastStateListener { state ->
                if (player_media_route_button != null) {
                    if (state == CastState.NO_DEVICES_AVAILABLE) player_media_route_button.visibility = GONE else {
                        if (player_media_route_button.visibility == GONE) player_media_route_button.visibility = VISIBLE
                    }
                    if (state == CastState.CONNECTED) {
                        if (!this::exoPlayer.isInitialized) return@addCastStateListener
                        val links = sortUrls(getUrls() ?: return@addCastStateListener)
                        val epData = getEpisode() ?: return@addCastStateListener

                        val index = links.indexOf(getCurrentUrl())
                        context?.startCast(
                            apiName,
                            currentHeaderName,
                            currentPoster,
                            epData.index,
                            episodes,
                            links,
                            index,
                            exoPlayer.currentPosition)

                        /*
                        val customData =
                            links.map { JSONObject().put("name", it.name) }
                        val jsonArray = JSONArray()
                        for (item in customData) {
                            jsonArray.put(item)
                        }

                        val mediaItems = links.map {
                            val movieMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE)

                            movieMetadata.putString(MediaMetadata.KEY_SUBTITLE,
                                epData.name ?: "Episode ${epData.episode}")

                            if (currentHeaderName != null)
                                movieMetadata.putString(MediaMetadata.KEY_TITLE, currentHeaderName)

                            val srcPoster = epData.poster ?: currentPoster
                            if (srcPoster != null) {
                                movieMetadata.addImage(WebImage(Uri.parse(srcPoster)))
                            }

                            MediaQueueItem.Builder(
                                MediaInfo.Builder(it.url)
                                    .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                                    .setContentType(MimeTypes.VIDEO_UNKNOWN)

                                    .setCustomData(JSONObject().put("data", jsonArray))
                                    .setMetadata(movieMetadata)
                                    .build()
                            )
                                .build()
                        }.toTypedArray()

                        val castPlayer = CastPlayer(castContext)
                        castPlayer.loadItems(
                            mediaItems,
                            if (index > 0) index else 0,
                            exoPlayer.currentPosition,
                            MediaStatus.REPEAT_MODE_REPEAT_SINGLE
                        )*/
                        //  activity?.popCurrentPage(isInPlayer = true, isInExpandedView = false, isInResults = false)
                        releasePlayer()
                        activity?.popCurrentPage()
                    }
                }
            }
        }

        arguments?.getString("data")?.let {
            playerData = mapper.readValue(it, PlayerData::class.java)
        }

        arguments?.getLong(STATE_RESUME_POSITION)?.let {
            playbackPosition = it
        }

        if (savedInstanceState != null) {
            currentWindow = savedInstanceState.getInt(STATE_RESUME_WINDOW)
            playbackPosition = savedInstanceState.getLong(STATE_RESUME_POSITION)
            isFullscreen = savedInstanceState.getBoolean(STATE_PLAYER_FULLSCREEN)
            isPlayerPlaying = savedInstanceState.getBoolean(STATE_PLAYER_PLAYING)
            resizeMode = savedInstanceState.getInt(RESIZE_MODE_KEY)
            playbackSpeed = savedInstanceState.getFloat(PLAYBACK_SPEED)
        }

        resizeMode = requireContext().getKey(RESIZE_MODE_KEY, 0)!!
        playbackSpeed = requireContext().getKey(PLAYBACK_SPEED_KEY, 1f)!!

        volumeObserver = SettingsContentObserver(
            Handler(
                Looper.getMainLooper()
            ), requireActivity()
        )

        activity?.contentResolver
            ?.registerContentObserver(
                android.provider.Settings.System.CONTENT_URI, true, volumeObserver
            )

        viewModel = ViewModelProvider(requireActivity()).get(ResultViewModel::class.java)


        observeDirectly(viewModel.episodes) { _episodes ->
            episodes = _episodes
            if (isLoading) {
                if (playerData.episodeIndex > 0 && playerData.episodeIndex < episodes.size) {

                } else {
                    // WHAT THE FUCK DID YOU DO
                }
            }
        }

        observe(viewModel.apiName) {
            apiName = it
        }

        overlay_loading_skip_button?.alpha = 0.5f
        observeDirectly(viewModel.allEpisodes) { _allEpisodes ->
            allEpisodes = _allEpisodes

            val current = getUrls()
            if (current != null) {
                if (current.isNotEmpty()) {
                    overlay_loading_skip_button?.alpha = 1f
                } else {
                    overlay_loading_skip_button?.alpha = 0.5f
                }
            } else {
                overlay_loading_skip_button?.alpha = 0.5f
            }
        }

        observeDirectly(viewModel.resultResponse) { data ->
            when (data) {
                is Resource.Success -> {
                    val d = data.value
                    if (d is LoadResponse) {
                        localData = d
                        currentPoster = d.posterUrl
                        currentHeaderName = d.name
                    }
                }
                is Resource.Failure -> {
                    //WTF, HOW DID YOU EVEN GET HERE
                }
            }
        }

        val fastForwardTime = settingsManager.getInt("fast_forward_button_time", 10)
        exo_rew_text.text = fastForwardTime.toString()
        exo_ffwd_text.text = fastForwardTime.toString()
        fun rewnd() {
            val rotateLeft = AnimationUtils.loadAnimation(context, R.anim.rotate_left)
            exo_rew.startAnimation(rotateLeft)

            val goLeft = AnimationUtils.loadAnimation(context, R.anim.go_left)
            goLeft.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}

                override fun onAnimationRepeat(animation: Animation?) {}

                override fun onAnimationEnd(animation: Animation?) {
                    exo_rew_text.post { exo_rew_text.text = "$fastForwardTime" }
                }
            })
            exo_rew_text.startAnimation(goLeft)
            exo_rew_text.text = "-$fastForwardTime"
            seekTime(fastForwardTime * -1000L)
        }

        exo_rew.setOnClickListener {
            rewnd()
        }

        fun ffwrd() {
            val rotateRight = AnimationUtils.loadAnimation(context, R.anim.rotate_right)
            exo_ffwd.startAnimation(rotateRight)

            val goRight = AnimationUtils.loadAnimation(context, R.anim.go_right)
            goRight.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}

                override fun onAnimationRepeat(animation: Animation?) {}

                override fun onAnimationEnd(animation: Animation?) {
                    exo_ffwd_text.post { exo_ffwd_text.text = "$fastForwardTime" }
                }
            })
            exo_ffwd_text.startAnimation(goRight)
            exo_ffwd_text.text = "+$fastForwardTime"
            seekTime(fastForwardTime * 1000L)
        }

        exo_ffwd.setOnClickListener {
            ffwrd()
        }

        overlay_loading_skip_button.setOnClickListener {
            setMirrorId(sortUrls(getUrls() ?: return@setOnClickListener).first()
                .getId()) // BECAUSE URLS CANT BE REORDERED
            if (!isCurrentlyPlaying) {
                initPlayer(getCurrentUrl())
            }
        }

        lock_player.setOnClickListener {
            isLocked = !isLocked
            val fadeTo = if (isLocked) 0f else 1f

            val fadeAnimation = AlphaAnimation(1f - fadeTo, fadeTo)
            fadeAnimation.duration = 100
            //   fadeAnimation.startOffset = 100
            fadeAnimation.fillAfter = true

            // MENUS
            centerMenu.startAnimation(fadeAnimation)
            player_media_route_button.startAnimation(fadeAnimation)
            //video_bar.startAnimation(fadeAnimation)

            //TITLE
            video_title_rez.startAnimation(fadeAnimation)
            video_title.startAnimation(fadeAnimation)

            // BOTTOM
            resize_player.startAnimation(fadeAnimation)
            playback_speed_btt.startAnimation(fadeAnimation)
            sources_btt.startAnimation(fadeAnimation)
            skip_op.startAnimation(fadeAnimation)
            video_go_back_holder2.startAnimation(fadeAnimation)

            updateLock()
        }

        class Listener : DoubleClickListener(this) {
            // Declaring a seekAnimation here will cause a bug

            override fun onDoubleClickRight(clicks: Int) {
                if (!isLocked) {
                    ffwrd()
                }
            }

            override fun onDoubleClickLeft(clicks: Int) {
                if (!isLocked) {
                    rewnd()
                }
            }

            override fun onSingleClick() {
                onClickChange()
                activity?.hideSystemUI()
            }

            override fun onMotionEvent(event: MotionEvent) {
                handleMotionEvent(event)
            }
        }

        player_holder.setOnTouchListener(
            Listener()
        )

        click_overlay?.setOnTouchListener(
            Listener()
        )

        video_go_back.setOnClickListener {
            //activity?.popCurrentPage(isInPlayer = true, isInExpandedView = false, isInResults = false)
            activity?.popCurrentPage()
        }
        video_go_back_holder.setOnClickListener {
            println("video_go_back_pressed")
            // activity?.popCurrentPage(isInPlayer = true, isInExpandedView = false, isInResults = false)
            activity?.popCurrentPage()
        }

        playback_speed_btt.visibility = if (playBackSpeedEnabled) VISIBLE else GONE
        playback_speed_btt.setOnClickListener {
            lateinit var dialog: AlertDialog
            // Lmao kind bad
            val speedsText = arrayOf("0.5x", "0.75x", "1x", "1.25x", "1.5x", "1.75x", "2x")
            val speedsNumbers = arrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
            val builder = AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
            builder.setTitle("Pick playback speed")
            builder.setOnDismissListener {
                activity?.hideSystemUI()
            }
            builder.setSingleChoiceItems(speedsText, speedsNumbers.indexOf(playbackSpeed)) { _, which ->

                //val speed = speedsText[which]
                //Toast.makeText(requireContext(), "$speed selected.", Toast.LENGTH_SHORT).show()

                playbackSpeed = speedsNumbers[which]
                requireContext().setKey(PLAYBACK_SPEED_KEY, playbackSpeed)
                val param = PlaybackParameters(playbackSpeed)
                exoPlayer.playbackParameters = param
                player_speed_text.text = "Speed (${playbackSpeed}x)".replace(".0x", "x")

                dialog.dismiss()
                activity?.hideSystemUI()
            }
            dialog = builder.create()
            dialog.show()
        }

        sources_btt.setOnClickListener {
            lateinit var dialog: AlertDialog
            getUrls()?.let { it1 ->
                sortUrls(it1).let { sources ->
                    val sourcesText = sources.map { it.name }
                    val builder = AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
                    builder.setTitle("Pick source")
                    builder.setOnDismissListener {
                        activity?.hideSystemUI()
                    }
                    builder.setSingleChoiceItems(sourcesText.toTypedArray(),
                        sources.indexOf(getCurrentUrl())) { _, which ->
                        //val speed = speedsText[which]
                        //Toast.makeText(requireContext(), "$speed selected.", Toast.LENGTH_SHORT).show()
                        setMirrorId(sources[which].getId())
                        initPlayer(getCurrentUrl())

                        dialog.dismiss()
                        activity?.hideSystemUI()
                    }
                    dialog = builder.create()
                    dialog.show()
                }
            }
        }

        player_view.resizeMode = resizeModes[resizeMode]
        if (playerResizeEnabled) {
            resize_player.visibility = VISIBLE
            resize_player.setOnClickListener {
                resizeMode = (resizeMode + 1) % resizeModes.size

                requireContext().setKey(RESIZE_MODE_KEY, resizeMode)
                player_view.resizeMode = resizeModes[resizeMode]
                //exoPlayer.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            }
        } else {
            resize_player.visibility = GONE
        }

        skip_op.setOnClickListener {
            if (exoPlayer.currentPosition * 100 / exoPlayer.duration >= OPENING_PROCENTAGE) {
                if (hasNextEpisode()) {
                    // skip_op.visibility = View.GONE
                    skipToNextEpisode()
                }
            } else {
                skipOP()
            }
        }

        changeSkip()

        initPlayer()
    }

    private fun getCurrentUrl(): ExtractorLink? {
        val urls = getUrls() ?: return null
        for (i in urls) {
            if (i.getId() == playerData.mirrorId) {
                return i
            }
        }

        return null
        /*ExtractorLink("",
                "TEST",
                "https://v6.4animu.me/Overlord/Overlord-Episode-01-1080p.mp4",
                //"http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                "",
                0)*/
    }

    private fun getUrls(): List<ExtractorLink>? {
        return try {
            allEpisodes[getEpisode()?.id]
        } catch (e: Exception) {
            null
        }
    }

    private fun getEpisode(): ResultEpisode? {
        return try {
            episodes[playerData.episodeIndex]
        } catch (e: Exception) {
            null
        }
    }

    private fun hasNextEpisode(): Boolean {
        return episodes.size > playerData.episodeIndex + 1
    }

    private var isCurrentlySkippingEp = false


    fun tryNextMirror() {
        val urls = getUrls()
        val current = getCurrentUrl()
        if (urls != null && current != null) {
            val id = current.getId()
            val sorted = sortUrls(urls)
            for ((i, item) in sorted.withIndex()) {
                if (item.getId() == id) {
                    if (sorted.size > i + 1) {
                        setMirrorId(sorted[i + 1].getId())
                        initPlayer()
                    }
                }
            }
        }
    }

    private fun skipToNextEpisode() {
        if (isCurrentlySkippingEp) return
        releasePlayer()
        isCurrentlySkippingEp = true
        val copy = playerData.copy(episodeIndex = playerData.episodeIndex + 1)
        playerData = copy
        playbackPosition = 0
        initPlayer()
    }

    private fun setMirrorId(id: Int) {
        val copy = playerData.copy(mirrorId = id)
        playerData = copy
        //initPlayer()
    }

    override fun onStart() {
        super.onStart()
        if (!isCurrentlyPlaying) {
            initPlayer()
        }
        if (player_view != null) player_view.onResume()
    }

    override fun onResume() {
        super.onResume()
        UIHelper.onAudioFocusEvent += ::handlePauseEvent

        activity?.hideSystemUI()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        if (Util.SDK_INT <= 23) {
            if (!isCurrentlyPlaying) {
                initPlayer()
            }
            if (player_view != null) player_view.onResume()
        }
    }

    private fun handlePauseEvent(pause : Boolean) {
        if(pause) {
            handlePlayerEvent(PlayerEventType.Pause)
        }
    }

    override fun onDestroy() {
        savePos()

        super.onDestroy()
        isInPlayer = false
        releasePlayer()

        UIHelper.onAudioFocusEvent -= ::handlePauseEvent

        activity?.showSystemUI()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
    }

    override fun onPause() {
        savePos()
        super.onPause()
        if (Util.SDK_INT <= 23) {
            if (player_view != null) player_view.onPause()
            releasePlayer()
        }
    }

    override fun onStop() {
        savePos()
        super.onStop()
        if (Util.SDK_INT > 23) {
            if (player_view != null) player_view.onPause()
            releasePlayer()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        savePos()

        if (this::exoPlayer.isInitialized) {
            outState.putInt(STATE_RESUME_WINDOW, exoPlayer.currentWindowIndex)
            outState.putLong(STATE_RESUME_POSITION, exoPlayer.currentPosition)
        }
        outState.putBoolean(STATE_PLAYER_FULLSCREEN, isFullscreen)
        outState.putBoolean(STATE_PLAYER_PLAYING, isPlayerPlaying)
        outState.putInt(RESIZE_MODE_KEY, resizeMode)
        outState.putFloat(PLAYBACK_SPEED, playbackSpeed)
        outState.putString("data", mapper.writeValueAsString(playerData))
        super.onSaveInstanceState(outState)
    }

    private var currentWindow = 0
    private var playbackPosition: Long = 0
/*
    private fun updateProgressBar() {
        val duration: Long =exoPlayer.getDuration()
        val position: Long =exoPlayer.getCurrentPosition()

        handler.removeCallbacks(updateProgressAction)
        val playbackState =  exoPlayer.getPlaybackState()
        if (playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED) {
            var delayMs: Long
            if (player.getPlayWhenReady() && playbackState == Player.STATE_READY) {
                delayMs = 1000 - position % 1000
                if (delayMs < 200) {
                    delayMs += 1000
                }
            } else {
                delayMs = 1000
            }
            handler.postDelayed(updateProgressAction, delayMs)
        }
    }

    private val updateProgressAction = Runnable { updateProgressBar() }*/

    @SuppressLint("SetTextI18n")
    fun initPlayer(currentUrl: ExtractorLink?) {
        if (currentUrl == null) return
        isCurrentlyPlaying = true
        hasUsedFirstRender = false

        try {
            if (!isInPlayer) return
            if (this::exoPlayer.isInitialized) {
                savePos()
                exoPlayer.release()
            }
            val isOnline =
                currentUrl.url.startsWith("https://") || currentUrl.url.startsWith("http://")

            if (settingsManager.getBoolean("ignore_ssl", true)) {
                // Disables ssl check
                val sslContext: SSLContext = SSLContext.getInstance("TLS")
                sslContext.init(null, arrayOf(SSLTrustManager()), java.security.SecureRandom())
                sslContext.createSSLEngine()
                HttpsURLConnection.setDefaultHostnameVerifier { _: String, _: SSLSession ->
                    true
                }
                HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
            }

            class CustomFactory : DataSource.Factory {
                override fun createDataSource(): DataSource {
                    return if (isOnline) {
                        val dataSource = DefaultHttpDataSourceFactory(USER_AGENT).createDataSource()
                        /*FastAniApi.currentHeaders?.forEach {
                            dataSource.setRequestProperty(it.key, it.value)
                        }*/
                        dataSource.setRequestProperty("Referer", currentUrl.referer)
                        dataSource
                    } else {
                        DefaultDataSourceFactory(requireContext(), USER_AGENT).createDataSource()
                    }
                }
            }

            val mimeType = if (currentUrl.isM3u8) MimeTypes.APPLICATION_M3U8 else MimeTypes.APPLICATION_MP4
            val _mediaItem = MediaItem.Builder()
                //Replace needed for android 6.0.0  https://github.com/google/ExoPlayer/issues/5983
                .setMimeType(mimeType)

            if (isOnline) {
                _mediaItem.setUri(currentUrl.url)
            } else {
                _mediaItem.setUri(Uri.fromFile(File(currentUrl.url)))
            }

            val mediaItem = _mediaItem.build()
            val trackSelector = DefaultTrackSelector(requireContext())
            // Disable subtitles
            trackSelector.parameters = DefaultTrackSelector.ParametersBuilder(requireContext())
                .setRendererDisabled(C.TRACK_TYPE_VIDEO, true)
                .setRendererDisabled(C.TRACK_TYPE_TEXT, true)
                .setDisabledTextTrackSelectionFlags(C.TRACK_TYPE_TEXT)
                .clearSelectionOverrides()
                .build()

            val _exoPlayer =
                SimpleExoPlayer.Builder(this.requireContext())
                    .setTrackSelector(trackSelector)

            _exoPlayer.setMediaSourceFactory(DefaultMediaSourceFactory(CustomFactory()))
            println("START POS: " + playbackPosition)
            exoPlayer = _exoPlayer.build().apply {
                playWhenReady = isPlayerPlaying
                seekTo(currentWindow, playbackPosition)
                setMediaItem(mediaItem, false)
                prepare()
            }

            val alphaAnimation = AlphaAnimation(1f, 0f)
            alphaAnimation.duration = 300
            alphaAnimation.fillAfter = true
            alphaAnimation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}

                override fun onAnimationRepeat(animation: Animation?) {}

                override fun onAnimationEnd(animation: Animation?) {
                    loading_overlay.post { video_go_back_holder.visibility = GONE; }
                }
            })
            overlay_loading_skip_button.visibility = GONE

            loading_overlay.startAnimation(alphaAnimation)

            exoPlayer.setHandleAudioBecomingNoisy(true) // WHEN HEADPHONES ARE PLUGGED OUT https://github.com/google/ExoPlayer/issues/7288
            player_view.player = exoPlayer
            // Sets the speed
            exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
            player_speed_text?.text = "Speed (${playbackSpeed}x)".replace(".0x", "x")

            if (localData != null) {
                val data = localData!!
                val localEpisode = getEpisode()
                if (localEpisode != null) {
                    val episode = localEpisode.episode
                    val season: Int? = localEpisode.season
                    val isEpisodeBased = data.isEpisodeBased()
                    video_title?.text = data.name +
                            if (isEpisodeBased)
                                if (season == null)
                                    " - Episode $episode"
                                else
                                    " \"S${season}:E${episode}\""
                            else ""
                    video_title_rez?.text = currentUrl.name
                }
            }

/*
            exo_remaining.text = Util.getStringForTime(formatBuilder,
                formatter,
                exoPlayer.contentDuration - exoPlayer.currentPosition)

            */


            //https://stackoverflow.com/questions/47731779/detect-pause-resume-in-exoplayer
            exoPlayer.addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    super.onRenderedFirstFrame()
                    isCurrentlySkippingEp = false

                    val height = exoPlayer.videoFormat?.height
                    val width = exoPlayer.videoFormat?.width
                    video_title_rez?.text =
                        if (height == null || width == null) currentUrl.name else "${currentUrl.name} - ${width}x${height}"

                    if (!hasUsedFirstRender) { // DON'T WANT TO SET MULTIPLE MESSAGES
                        println("FIRST RENDER")
                        changeSkip()
                        exoPlayer
                            .createMessage { _, _ ->
                                changeSkip()
                            }
                            .setLooper(Looper.getMainLooper())
                            .setPosition( /* positionMs= */exoPlayer.contentDuration * OPENING_PROCENTAGE / 100)
                            //   .setPayload(customPayloadData)
                            .setDeleteAfterDelivery(false)
                            .send()
                        exoPlayer
                            .createMessage { _, _ ->
                                changeSkip()
                            }
                            .setLooper(Looper.getMainLooper())
                            .setPosition( /* positionMs= */exoPlayer.contentDuration * AUTOLOAD_NEXT_EPISODE_PROCENTAGE / 100)
                            //   .setPayload(customPayloadData)
                            .setDeleteAfterDelivery(false)

                            .send()

                    } else {
                        changeSkip()
                    }
                    hasUsedFirstRender = true
                }

                override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                    updatePIPModeActions()
                    if (activity == null) return
                    if (playWhenReady) {
                        when (playbackState) {
                            Player.STATE_READY -> {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    activity?.requestLocalAudioFocus(getFocusRequest())
                                }
                            }
                            Player.STATE_ENDED -> {
                                if (hasNextEpisode()) {
                                    skipToNextEpisode()
                                }
                            }
                            Player.STATE_BUFFERING -> {
                                changeSkip()
                            }
                            else -> {
                            }
                        }
                    }
                }

                override fun onPlayerError(error: ExoPlaybackException) {
                    // Lets pray this doesn't spam Toasts :)
                    when (error.type) {
                        ExoPlaybackException.TYPE_SOURCE -> {
                            if (currentUrl.url != "") {
                                Toast.makeText(
                                    activity,
                                    "Source error\n" + error.sourceException.message,
                                    LENGTH_SHORT
                                )
                                    .show()
                                tryNextMirror()
                            }
                        }
                        ExoPlaybackException.TYPE_REMOTE -> {
                            Toast.makeText(activity, "Remote error", LENGTH_SHORT)
                                .show()
                        }
                        ExoPlaybackException.TYPE_RENDERER -> {
                            Toast.makeText(
                                activity,
                                "Renderer error\n" + error.rendererException.message,
                                LENGTH_SHORT
                            )
                                .show()
                        }
                        ExoPlaybackException.TYPE_UNEXPECTED -> {
                            Toast.makeText(
                                activity,
                                "Unexpected player error\n" + error.unexpectedException.message,
                                LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            })
        } catch (e: java.lang.IllegalStateException) {
            println("Warning: Illegal state exception in PlayerFragment")
        }
    }

    //http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4
    @SuppressLint("SetTextI18n")
    private fun initPlayer() {
        println("INIT PLAYER")
        view?.setOnTouchListener { _, _ -> return@setOnTouchListener true } // VERY IMPORTANT https://stackoverflow.com/questions/28818926/prevent-clicking-on-a-button-in-an-activity-while-showing-a-fragment
        val tempCurrentUrls = getUrls()
        if (tempCurrentUrls != null) {
            setMirrorId(sortUrls(tempCurrentUrls).first().getId()) // BECAUSE URLS CANT BE REORDERED
        }
        val tempUrl = getCurrentUrl()
        println("TEMP:" + tempUrl?.name)
        if (tempUrl == null) {
            val localEpisode = getEpisode()
            if (localEpisode != null) {
                viewModel.loadEpisode(localEpisode, false) {
                    //if(it is Resource.Success && it.value == true)
                    val currentUrls = getUrls()
                    if (currentUrls != null && currentUrls.isNotEmpty()) {
                        if (!isCurrentlyPlaying) {
                            setMirrorId(sortUrls(currentUrls).first().getId()) // BECAUSE URLS CANT BE REORDERED
                            initPlayer(getCurrentUrl())
                        }
                    } else {
                        Toast.makeText(context, "No Links Found", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            initPlayer(tempUrl)
        }

        /*
        val currentUrl = tempUrl
        if (currentUrl == null) {
            activity?.runOnUiThread {
                Toast.makeText(activity, "Error getting link", LENGTH_LONG).show()
                //MainActivity.popCurrentPage()
            }
        } else {

        }*/

        //isLoadingNextEpisode = false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_player, container, false)
    }
}