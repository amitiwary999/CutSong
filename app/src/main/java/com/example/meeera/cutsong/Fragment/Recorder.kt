package com.example.meeera.cutsong.Fragment

import android.content.Intent
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.support.v4.app.Fragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.ImageView
import android.widget.LinearLayout
import com.example.meeera.cutsong.Activity.SongCutActivity
import com.example.meeera.cutsong.R
import com.triggertrap.seekarc.SeekArc
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Created by meeera on 24/10/17.
 */
class Recorder() : Fragment(), View.OnClickListener {

    private var iv_record: ImageView? = null
    private var isRecording: Boolean = false
    private var mRecorder: MediaRecorder? = null
    private val LOG_TAG = "AudioRecordTest"
    private var mFileName: String? = null
    private var chronometer: Chronometer? = null
    private var outfile: File? = null
    private val maxtime = 2
    private var ll_popup: LinearLayout? = null
    private var ll_discard:LinearLayout? = null
    private var ll_save:LinearLayout? = null

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        var view = inflater?.inflate(R.layout.record_fragment, container, false)
        iv_record = view?.findViewById(R.id.iv_record)
        chronometer = view?.findViewById(R.id.chronometer)
        ll_popup = view?.findViewById(R.id.ll_popup)
        ll_discard = view?.findViewById(R.id.ll_discard)
        ll_save = view?.findViewById(R.id.ll_save)
        iv_record?.setOnClickListener(this)
        ll_discard?.setOnClickListener(this)
        ll_save?.setOnClickListener(this)

        chronometer?.setOnChronometerTickListener { chronometer ->
            chronometer.text
            val elapsedMillis = SystemClock.elapsedRealtime() - chronometer.base
            if (elapsedMillis > TimeUnit.MINUTES.toMillis(maxtime.toLong())) {
                stopRecording()
            }
        }
        return view
    }

    private fun startRecording() {
        mRecorder = MediaRecorder()
        mRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
        mRecorder?.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
        val sdcard = Environment.getExternalStorageDirectory()
        val storagePath = File(sdcard.absolutePath + "/AwRecording")
        if (!storagePath.exists()) {
            storagePath.mkdir()
        }
        mFileName = "aw_recording_" + Calendar.getInstance().timeInMillis.toString()
        outfile = File(storagePath.toString() + "/" + mFileName + ".3gp")
        mRecorder?.setOutputFile(outfile?.getAbsolutePath())
        mRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)

        try {
            mRecorder?.prepare()
            mRecorder?.start()
        } catch (e: IOException) {
            Log.e(LOG_TAG, "prepare() failed")
            Log.e(LOG_TAG, e.message)
        }

        chronometer?.setBase(SystemClock.elapsedRealtime())
        chronometer?.start()
    }

    private fun stopRecording() {
        chronometer?.stop()
        try {
            mRecorder?.stop()
            mRecorder?.release()
            mRecorder = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onClick(v: View) {
        when(v.id){
            R.id.ll_discard -> ll_popup?.visibility = View.GONE
            R.id.ll_save -> {
                ll_popup?.visibility = View.GONE
                val intent = Intent(activity, SongCutActivity::class.java)
                intent.putExtra("fpath", outfile?.getAbsolutePath())
                intent.putExtra ("artwork", "")
                startActivity(intent)
            }
            R.id.iv_record -> {
                if (isRecording) {
                    iv_record?.setImageResource(R.drawable.mic)
                    isRecording = !isRecording
                    stopRecording()
                    ll_popup?.visibility = View.VISIBLE
                } else {

                    iv_record?.setImageResource(R.drawable.stop)
                    isRecording = !isRecording
                    startRecording()
                    ll_popup?.visibility = View.GONE
                }
            }
        }
    }
}