package com.example.meeera.cutsong

import android.net.Uri
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.crashlytics.android.Crashlytics
import com.example.meeera.cutsong.Activity.MainActivity
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.TransferListener
import com.google.android.exoplayer2.util.Util
import kotlinx.android.synthetic.main.play_layout.*

class AudioPlayer(var mainActivity: MainActivity): LifecycleObserver {
    lateinit var mExoPlayer: ExoPlayer
    lateinit var mDataSourceFactory: DefaultDataSourceFactory
    var mIsSeekbarDragging = false
    var mSeekbarDraggingPosition = 0
    var shouldPlay=true
    var mCurrentUri: Uri = Uri.EMPTY

    var mCurrentAudioPosition: Long = 0
    var mCurrentAudioDuration: Long = 0
    var mCurrentAudioSeekbarInPercentage: Int = 0

    var handler: Handler = Handler()
    private var mHomePlayerCallback: PlayerCallback? = null

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onCreate() {
        val loadControl: LoadControl = DefaultLoadControl.Builder().setBufferDurationsMs(
                15 * 1000, // Min buffer size
                30 * 1000, // Max buffer size
                500, // Min playback time buffered before starting video
                100).createDefaultLoadControl()

        val bandwidthMeter = DefaultBandwidthMeter()
        mDataSourceFactory = DefaultDataSourceFactory(mainActivity, Util.getUserAgent(mainActivity,
                "mediaPlayerSample"), bandwidthMeter as TransferListener)

        mExoPlayer = ExoPlayerFactory.newSimpleInstance(mainActivity, DefaultRenderersFactory(mainActivity),
                DefaultTrackSelector(), loadControl)

    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun stop(){

    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun onPause() {
        shouldPlay=mExoPlayer.playWhenReady
        mExoPlayer.playWhenReady=false

        pauseAudio()
    }
    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        stopMedia()
    }

    private fun stopMedia() {
        mExoPlayer.stop()
        mainActivity.parent_layout_music.visibility = View.GONE
    }


    private var vSeekBar: SeekBar? = null

    private fun onSeekBarDragging(progress: Int) {
        mSeekbarDraggingPosition = progress
    }

    private fun onSeekbarStopDragging() {
        mIsSeekbarDragging = false
        val playerPosition = ((mSeekbarDraggingPosition.toFloat() / 100) * mCurrentAudioDuration.toFloat()).toLong()
        mExoPlayer.seekTo(playerPosition)
    }

    private fun initSeekBar() {
        vSeekBar = mainActivity.media_audio_seek_bar
        vSeekBar?.requestFocus()

        vSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                onSeekBarDragging(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                mIsSeekbarDragging = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                onSeekbarStopDragging()
            }
        })

    }

    fun openAudio(uri: Uri) {
        mainActivity.parent_layout_music.visibility = View.VISIBLE

        mCurrentUri = uri
        initExo()
        playAudio()

        handleAudioMedia()
    }

    private fun initExo(){
        mExoPlayer.playWhenReady=shouldPlay
        mExoPlayer.addListener(object : Player.DefaultEventListener() {
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                if (playWhenReady && playbackState == Player.STATE_READY ) {
                    if (mHomePlayerCallback != null) {
                        mHomePlayerCallback!!.onPlayerReady(mExoPlayer)
                        updateSeekBar()
                    }
                } else if (playWhenReady && playbackState == Player.STATE_ENDED) {
                    //playNextPlayerAudio()
                    mExoPlayer.playWhenReady = false

                    mainActivity.media_audio_play_btn?.visibility = View.VISIBLE
                    mainActivity.media_audio_pause_btn?.visibility = View.GONE

                } else {
                    if (mHomePlayerCallback != null ) {                        //updateSeekBar()

                        mHomePlayerCallback!!.onExoPlayerStateChange(mExoPlayer, playbackState)
                    }
                }
            }
        })
    }

    private fun playAudio() {
        val mediaSource = ExtractorMediaSource.Factory(mDataSourceFactory)
                .createMediaSource(mCurrentUri)
        mExoPlayer.prepare(mediaSource)
    }

    private val updateProgressAction = Runnable { updateSeekBar() }


    fun updateSeekBar() {

        var playbackState = mExoPlayer.playbackState

        if (playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED) {
            mCurrentAudioDuration = mExoPlayer.duration
            mCurrentAudioPosition = mExoPlayer.currentPosition

            try {
                mCurrentAudioSeekbarInPercentage = ((mCurrentAudioPosition.toFloat() / mCurrentAudioDuration.toFloat()) * 100).toInt()
            }catch (e: Exception){
                e.printStackTrace()
                Crashlytics.logException(e)
                mCurrentAudioSeekbarInPercentage = 0
            }
            if (mHomePlayerCallback != null) {
                mHomePlayerCallback!!.onPlayerSeekBarProgressUpdate(mCurrentAudioPosition,
                        mCurrentAudioSeekbarInPercentage, mCurrentAudioDuration)
            }

            //info { "duration: "+ mCurrentAudioDuration }
            //info { "position: "+ position }
            // info { "percentage: "+ mCurrentAudioSeekbarInPercentage }
            //info { "*********************************************************" }

            // Remove scheduled updates.
            if (mCurrentAudioSeekbarInPercentage == 100) {
                handler.removeCallbacks(updateProgressAction)
            }

            //update seek bar after every 1 seconds
            handler.postDelayed(updateProgressAction, 1000)
        }
    }

    private fun pauseAudio()
    {
        mainActivity.media_audio_play_btn?.visibility = View.VISIBLE
        mainActivity.media_audio_pause_btn?.visibility = View.GONE

    }

    private fun handleAudioMedia(){
        initSeekBar()
        mHomePlayerCallback = mPlayerCallback
        mExoPlayer.playWhenReady = true

        mainActivity.media_audio_current_time.visibility = View.VISIBLE
        mainActivity.media_audio_play_btn.visibility = View.GONE
        mainActivity.media_audio_pause_btn.visibility = View.VISIBLE

        mainActivity.media_audio_play_btn?.setOnClickListener {
            Log.d("play ", "audio")
            mainActivity.media_audio_play_btn?.visibility = View.GONE
            mainActivity.media_audio_pause_btn?.visibility = View.VISIBLE
            mExoPlayer.playWhenReady = true
        }

        mainActivity.media_audio_pause_btn?.setOnClickListener {
            mainActivity.media_audio_play_btn?.visibility = View.VISIBLE
            mainActivity.media_audio_pause_btn?.visibility = View.GONE
            mExoPlayer.playWhenReady = false
        }
    }

    private var mPlayerCallback: PlayerCallback = object : PlayerCallback {
        override fun onPlayerReady(exoPlayer: ExoPlayer) {
            mainActivity.media_audio_current_time?.let { it.text = UtilTime.timeFormatted(mCurrentAudioPosition) }
            vSeekBar?.let {
                it.progress = mCurrentAudioSeekbarInPercentage
                it.max = 100
            }
            mainActivity.media_audio_total_time?.text = UtilTime.timeFormatted(exoPlayer.duration)
        }

        override fun onPlayerSeekBarProgressUpdate(pAudioCurrentPosition: Long, pAudioPositionInPercentage: Int, pAudioDuration: Long) {
            mainActivity.media_audio_current_time?.let { it.text = UtilTime.timeFormatted(mCurrentAudioPosition) }
            vSeekBar?.let { it.progress = mCurrentAudioSeekbarInPercentage }
        }

        override fun onExoPlayerStateChange(exoPlayer: ExoPlayer, playerState: Int) {
            //To change body of created functions use File | Settings | File Templates.
            if (playerState == Player.STATE_ENDED) {
                mExoPlayer.seekTo(0)
                mExoPlayer.playWhenReady=false
                pauseAudio()
            }
        }
    }

    interface PlayerCallback {
        fun onPlayerReady(exoPlayer: ExoPlayer)
        fun onPlayerSeekBarProgressUpdate(pAudioCurrentPosition: Long, pAudioPositionInPercentage: Int,
                                          pAudioDuration: Long)

        fun onExoPlayerStateChange(exoPlayer: ExoPlayer, playerState: Int)
    }
}