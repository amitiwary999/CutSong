package com.example.meeera.cutsong

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.example.meeera.cutsong.Activity.MainActivity
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.TransferListener
import com.google.android.exoplayer2.util.Util

class AudioPlayer(var mainActivity: MainActivity): LifecycleObserver {
    lateinit var mExoPlayer: ExoPlayer
    lateinit var mDataSourceFactory: DefaultDataSourceFactory

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

    private var mPlayerCallback: PlayerCallback = object : PlayerCallback {
        override fun onPlayerReady(exoPlayer: ExoPlayer) {

        }

        override fun onPlayerSeekBarProgressUpdate(pAudioCurrentPosition: Long, pAudioPositionInPercentage: Int, pAudioDuration: Long) {

        }

        override fun onExoPlayerStateChange(exoPlayer: ExoPlayer, playerState: Int) {
            //To change body of created functions use File | Settings | File Templates.
            if (playerState == Player.STATE_ENDED) {
                mExoPlayer.seekTo(0)
                mExoPlayer.playWhenReady=false
                //pauseAudio()
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