package com.example.meeera.cutsong.Activity

import android.app.ProgressDialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.*
import android.provider.MediaStore
import android.support.design.widget.FloatingActionButton
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.*
import com.bq.markerseekbar.MarkerSeekBar
import com.example.meeera.cutsong.R
import com.example.meeera.cutsong.soundfile.SoundFile
import com.nostra13.universalimageloader.core.DisplayImageOptions
import com.nostra13.universalimageloader.core.ImageLoader
import com.triggertrap.seekarc.SeekArc
import de.hdodenhof.circleimageview.CircleImageView
import java.io.*

/**
 * Created by meeera on 25/10/17.
 */
class SongCutActivity : AppCompatActivity(), View.OnClickListener {

    private var fPath = ""
    private var artwork = ""
    private var track_title:String = " "
    internal var total_duration:Long = 0
    internal var current_time:Long = 0
    private var marker_seekbar_from: MarkerSeekBar? = null
    private var marker_seekbar_to:MarkerSeekBar? = null
    private var retriever: MediaMetadataRetriever? = null
    private var iv_play_pause: ImageView? = null
    private var seekbar_song_play: SeekArc? = null
    private var iv_artwork: CircleImageView? = null
    private var chronometer_song_play: Chronometer? = null
    private var mediaPlayer: MediaPlayer ?=  null
    private var tv_from: TextView? = null
    private var tv_to:TextView? = null
    private var fab_cut: FloatingActionButton? = null
    private var imageLoader: ImageLoader? = null
    private var options: DisplayImageOptions? = null
   // private var animation: Animation? = null
    private var mMediaFile: SoundFile? = null
    var mProgressDialog: ProgressDialog ?= null
    var mLoadingKeepGoing : Boolean = true

    //DTO and VO
    private var start_point = 0.0
    private var end_point = 0.0


    //Thread
    private var mLoadSoundFileThread: Thread? = null
    private var mSaveSoundFileThread: Thread? = null

    //Handler
    private var mHandler: Handler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_songcut)
        mLoadSoundFileThread = null
        mHandler = Handler()
        mediaPlayer = MediaPlayer()
        fPath = intent.getStringExtra("fpath")
        artwork = intent.getStringExtra("artwork")
        imageLoader = ImageLoader.getInstance()
        options = DisplayImageOptions.Builder()
                .showImageOnLoading(R.drawable.place_holder)
                .showImageForEmptyUri(R.drawable.place_holder)
                .showImageOnFail(R.drawable.place_holder)
                .cacheInMemory(true)
                .cacheOnDisk(true)
                .considerExifParams(true)
                .bitmapConfig(Bitmap.Config.RGB_565)
                .build()
        iv_artwork = findViewById(R.id.iv_artwork) as CircleImageView
        imageLoader?.displayImage(artwork, iv_artwork, options)
        tv_to = findViewById(R.id.tv_to) as TextView
        tv_from = findViewById(R.id.tv_from) as TextView
        fab_cut = findViewById(R.id.fab_cut) as FloatingActionButton
        fab_cut?.setOnClickListener(this)
        chronometer_song_play = findViewById(R.id.chronometer_song_play) as Chronometer
        marker_seekbar_from = findViewById(R.id.marker_seekbar_from) as MarkerSeekBar
        marker_seekbar_from?.setProgressAdapter(object : MarkerSeekBar.ProgressAdapter{
            override fun toText(progress: Int): String {
                return  getDisplayTextFromProgress(progress)
            }

            override fun onMeasureLongestText(seekBarMax: Int): String {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

        })
        marker_seekbar_from?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                tv_from?.setText(getDisplayTextFromProgress(seekBar.progress))
                start_point = getSecondFromProgress(seekBar.progress).toDouble()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {

            }
        })

        marker_seekbar_to = findViewById(R.id.marker_seekbar_to) as MarkerSeekBar
        marker_seekbar_to?.setProgressAdapter(object : MarkerSeekBar.ProgressAdapter{
            override fun toText(progress: Int): String {
                return  getDisplayTextFromProgress(progress)
            }

            override fun onMeasureLongestText(seekBarMax: Int): String {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

        })

        marker_seekbar_to?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {

                tv_to?.setText(getDisplayTextFromProgress(seekBar.progress))
                end_point = getSecondFromProgress(seekBar.progress).toDouble()

            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {

            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {

            }
        })


        iv_play_pause = findViewById(R.id.iv_play_pause) as ImageView
        iv_play_pause?.setOnClickListener(this)
        seekbar_song_play = findViewById(R.id.seekbar_song_play) as SeekArc
        seekbar_song_play?.setOnSeekArcChangeListener(object : SeekArc.OnSeekArcChangeListener {
            override fun onProgressChanged(seekArc: SeekArc, i: Int, b: Boolean) {

            }

            override fun onStartTrackingTouch(seekArc: SeekArc) {

            }

            override fun onStopTrackingTouch(seekArc: SeekArc) {
                setmediaProgress(seekArc.progress)
                if (!mediaPlayer?.isPlaying().toString().toBoolean()) {
                    current_time = mediaPlayer?.currentPosition?.toLong().toString().toLong()
                }

            }
        })

        chronometer_song_play?.setOnChronometerTickListener { chronometer ->
            val elapsedMillis = SystemClock.elapsedRealtime() - chronometer.base
            seekbar_song_play?.setProgress(getProgress(elapsedMillis))
        }

        mediaPlayer?.setOnCompletionListener(MediaPlayer.OnCompletionListener {
            current_time = 0
            chronometer_song_play?.stop()
            iv_play_pause?.setImageResource(R.drawable.play_button)
        })

        getMusicDataFromPath()
    }

    /**
     * method to get music data from path
     */
    internal fun getMusicDataFromPath() {
        retriever = MediaMetadataRetriever()
        retriever?.setDataSource(fPath)
        val duration = retriever?.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        track_title = retriever?.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).toString()
        total_duration = java.lang.Long.parseLong(duration)
        Log.d("loading", "loadingloading")
        mProgressDialog = ProgressDialog(this)
        mProgressDialog?.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        mProgressDialog?.setTitle("Loading")
        mProgressDialog?.setCancelable(false)
        mProgressDialog?.show()

        var mLoadingLastUpdateTime = getCurrentTime()

        val listener = object : SoundFile.ProgressListener {
           override fun reportProgress(fractionComplete: Double): Boolean {
                val now = getCurrentTime()
                if (now - mLoadingLastUpdateTime > 100) {
                    mProgressDialog?.progress = (
                            (mProgressDialog?.max.toString().toInt() * fractionComplete).toInt())
                    mLoadingLastUpdateTime = now
                }
                return mLoadingKeepGoing
            }
        }

        mLoadSoundFileThread = object : Thread() {
            override fun run() {
                try {
                    mMediaFile = SoundFile.create(File(fPath).getAbsolutePath(), listener)
                    if (mMediaFile == null) {
                        mProgressDialog?.dismiss()
                        return
                    }
                    mediaPlayer?.setDataSource(fPath)
                    mediaPlayer?.prepare()
                } catch (e: IOException) {
                    e.printStackTrace()
                    return
                } catch (e: SoundFile.InvalidInputException) {
                    e.printStackTrace()
                    return
                }

                mProgressDialog?.dismiss()
            }
        }
        mLoadSoundFileThread?.start()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.iv_play_pause -> handlePlayPause()
            R.id.fab_cut -> saveRingtone(track_title.subSequence(0, track_title.length.toString().toInt()), start_point, end_point)
        }
    }

    /***
     * method to handle play pause of song
     */
    internal fun handlePlayPause() {

        if (mediaPlayer?.isPlaying.toString().toBoolean()) {
            iv_play_pause?.setImageResource(R.drawable.play_button)
            mediaPlayer?.pause()
            current_time = mediaPlayer?.currentPosition?.toLong().toString().toLong()
            chronometer_song_play?.stop()
        } else {
            iv_play_pause?.setImageResource(R.drawable.pause_button)
            mediaPlayer?.start()
            val eclapsedtime = SystemClock.elapsedRealtime()
            chronometer_song_play?.setBase(eclapsedtime - current_time)
            chronometer_song_play?.start()

        }

    }

    private fun getProgress(d: Long): Int {
        val p = d * 100 / total_duration
        var x = p.toInt()
        return x
    }

    private fun setmediaProgress(p: Int) {
        val progress = (total_duration * p / 100).toInt()
        mediaPlayer?.seekTo(progress)
        val eclapsedtime = SystemClock.elapsedRealtime()
        chronometer_song_play?.setBase(eclapsedtime - progress)
        chronometer_song_play?.start()

    }

    private fun getDisplayTextFromProgress(p: Int): String {
        var displayText = ""
        val millis = total_duration * p / 100
        val seconds = (millis / 1000).toInt() % 60
        val minutes = (millis / (1000 * 60) % 60).toInt()
        val hours = (millis / (1000 * 60 * 60) % 24).toInt()

        if (hours > 0) {
            displayText = hours.toString() + ":" + minutes + ":" + seconds
        } else {
            displayText = minutes.toString() + ":" + seconds
        }
        return displayText
    }

    private fun getSecondFromProgress(p: Int): Int {
        val millis = total_duration * p / 100

        return (millis / 1000).toInt() % 60
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        var intent = Intent(this@SongCutActivity, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
       // animation?.cancel()
    }

    override fun onPause() {
        super.onPause()
        Log.d("pause", "pause")
        if (mediaPlayer?.isPlaying.toString().toBoolean()) {
            iv_play_pause?.setImageResource(R.drawable.play_button)
            mediaPlayer?.pause()
            current_time = mediaPlayer?.currentPosition?.toLong().toString().toLong()
            chronometer_song_play?.stop()
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d("stop", "stop")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("destroy", "destroy")
    }

    private fun saveRingtone(title: CharSequence, mStartPos: Double, mEndPos: Double) {
        val startTime = mStartPos.toFloat()
        val endTime = mEndPos.toFloat()
        val duration = (endTime - startTime + 0.5).toInt()


        // Save the sound file in a background thread
        mSaveSoundFileThread = object : Thread() {
            override fun run() {
                // Try AAC first.
                var outPath = makeRingtoneFilename(title, ".m4a")
                if (outPath == null) {
                    Toast.makeText(this@SongCutActivity, "Fail to Create", Toast.LENGTH_SHORT).show()
                    return
                }
                var outFile = File(outPath)
                var fallbackToWAV: Boolean? = false
                try {
                    // Write the new file
                    Log.d("startTimetimetimetime", "hiiiiiHiiii$startTime $endTime")
                    mMediaFile?.WriteFile(outFile, startTime, endTime)
                } catch (e: Exception) {
                    // log the error and try to create a .wav file instead
                    if (outFile.exists()) {
                        outFile.delete()
                    }
                    val writer = StringWriter()
                    e.printStackTrace(PrintWriter(writer))
                    Log.e("Ringdroid", "Error: Failed to create " + outPath)
                    Log.e("Ringdroid", writer.toString())
                    fallbackToWAV = true
                }

                // Try to create a .wav file if creating a .m4a file failed.
                if (fallbackToWAV!!) {
                    outPath = makeRingtoneFilename(title, ".wav")
                    if (outPath == null) {

                        return
                    }
                    outFile = File(outPath)
                    try {
                        // create the .wav file
                        mMediaFile?.WriteWAVFile(outFile, startTime, endTime)
                    } catch (e: Exception) {
                        // Creating the .wav file also failed. Stop the progress dialog, show an
                        // error message and exit.
                        if (outFile.exists()) {
                            outFile.delete()
                        }
                        Toast.makeText(this@SongCutActivity, " " + e.toString(), Toast.LENGTH_SHORT).show()
                        return
                    }

                }

                // Try to load the new file to make sure it worked
                try {
                    val listener = object : SoundFile.ProgressListener {
                        override fun reportProgress(frac: Double): Boolean {
                            // Do nothing - we're not going to try to
                            // estimate when reloading a saved sound
                            // since it's usually fast, but hard to
                            // estimate anyway.
                            return true  // Keep going
                        }
                    }
                    SoundFile.create(outPath, listener)
                } catch (e: Exception) {
                    Toast.makeText(this@SongCutActivity, "" + e.toString(), Toast.LENGTH_SHORT).show()
                    return
                }


                val finalOutPath = outPath
                val runnable = Runnable {
                    afterSavingRingtone(title,
                            finalOutPath,
                            duration)
                }
                mHandler?.post(runnable)
            }
        }
        mSaveSoundFileThread?.start()
    }

    private fun afterSavingRingtone(title: CharSequence,
                                    outPath: String?,
                                    duration: Int) {
        val outFile = File(outPath!!)
        val fileSize = outFile.length()
        if (fileSize <= 512) {
            outFile.delete()
            Toast.makeText(this, "Too small to save!", Toast.LENGTH_SHORT).show()
            return
        }

        // Create the database record, pointing to the existing file path
        val mimeType: String
        if (outPath.endsWith(".m4a")) {
            mimeType = "audio/mp4a-latm"
        } else if (outPath.endsWith(".wav")) {
            mimeType = "audio/wav"
        } else {
            // This should never happen.
            mimeType = "audio/mpeg"
        }

        val artist = getString(R.string.app_name)

        val values = ContentValues()
        values.put(MediaStore.MediaColumns.DATA, outPath)
        values.put(MediaStore.MediaColumns.TITLE, title.toString())
        values.put(MediaStore.MediaColumns.SIZE, fileSize)
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        values.put(MediaStore.Audio.Media.ARTIST, artist)
        values.put(MediaStore.Audio.Media.DURATION, duration)

        // Insert it into the database
        val uri = MediaStore.Audio.Media.getContentUriForPath(outPath)
        val newUri = contentResolver.insert(uri, values)
        setResult(RESULT_OK, Intent().setData(newUri))

        Toast.makeText(this, "Save success", Toast.LENGTH_SHORT).show()
        var intent = Intent(this@SongCutActivity, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }


    private fun makeRingtoneFilename(title: CharSequence, extension: String): String? {
        val subdir: String
        var externalRootDir = Environment.getExternalStorageDirectory().path
        if (!externalRootDir.endsWith("/")) {
            externalRootDir += "/"
        }

        subdir = "media/audio/music/"

        var parentdir = externalRootDir + subdir

        // Create the parent directory
        val parentDirFile = File(parentdir)
        parentDirFile.mkdirs()

        // If we can't write to that special path, try just writing
        // directly to the sdcard
        if (!parentDirFile.isDirectory) {
            parentdir = externalRootDir
        }

        // Turn the title into a filename
        var filename = ""
        for (i in 0..title.length - 1) {
            if (Character.isLetterOrDigit(title[i])) {
                filename += title[i]
            }
        }

        // Try to make the filename unique
        var path: String? = null
        for (i in 0..99) {
            val testPath: String
            if (i > 0)
                testPath = parentdir + filename + i + extension
            else
                testPath = parentdir + filename + extension

            try {
                val f = RandomAccessFile(File(testPath), "r")
                f.close()
            } catch (e: Exception) {
                // Good, the file didn't exist
                path = testPath
                break
            }

        }

        return path
    }

    private fun getCurrentTime(): Long {
        return System.nanoTime() / 1000000
    }
}