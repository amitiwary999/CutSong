package com.example.meeera.cutsong.Activity

import android.app.ProgressDialog
import android.content.ContentValues
import android.content.DialogInterface
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
import android.view.animation.Animation
import android.widget.*
import com.bq.markerseekbar.MarkerSeekBar
import com.example.meeera.cutsong.Mediafile.MediaFile
import com.example.meeera.cutsong.R
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
    private var mediaPlayer: MediaPlayer =  MediaPlayer()
    private var tv_from: TextView? = null
    private var tv_to:TextView? = null
    private var fab_cut: FloatingActionButton? = null
    private var imageLoader: ImageLoader? = null
    private var options: DisplayImageOptions? = null
    private var animation: Animation? = null
    private var mMediaFile: MediaFile? = null
    var mProgressDialog: ProgressDialog ?= null
    //UI
    private var bt_save: Button? = null

    //DTO and VO
    private var start_point = 0.0
    private var end_point = 0.0


    //Thread
    private var mLoadSoundFileThread: Thread? = null
    private val mRecordAudioThread: Thread? = null
    private var mSaveSoundFileThread: Thread? = null

    //Handler
    private var mHandler: Handler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_songcut)
        mLoadSoundFileThread = null
        mHandler = Handler()
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
        marker_seekbar_from?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                tv_from?.setText(getDisplayTextFrompProgress(seekBar.progress))

                start_point = getSecondFromProgress(seekBar.progress).toDouble()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {

            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {

            }
        })

        marker_seekbar_to = findViewById(R.id.marker_seekbar_to) as MarkerSeekBar

        marker_seekbar_to?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {

                tv_to?.setText(getDisplayTextFrompProgress(seekBar.progress))
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
                    current_time = mediaPlayer?.getCurrentPosition()?.toLong()
                }

            }
        })

        chronometer_song_play?.setOnChronometerTickListener { chronometer ->
            val elapsedMillis = SystemClock.elapsedRealtime() - chronometer.base
            seekbar_song_play?.setProgress(getProgress(elapsedMillis))
        }

      /*  chronometer_song_play?.setOnChronometerTickListener(Chronometer.OnChronometerTickListener { chronometer ->
            val elapsedMillis = SystemClock.elapsedRealtime() - chronometer.base
            seekbar_song_play?.setProgress(getProgress(elapsedMillis))
        })*/

        mediaPlayer?.setOnCompletionListener(MediaPlayer.OnCompletionListener {
            current_time = 0
            chronometer_song_play?.stop()
            iv_play_pause?.setImageResource(R.drawable.play_button)
            iv_artwork?.clearAnimation()
        })

        bt_save = findViewById(R.id.bt_save) as Button
        bt_save?.setOnClickListener(View.OnClickListener {
            if (end_point > 0) {
                saveRingtone(fPath, start_point, end_point)
            }
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
        mProgressDialog?.setCancelable(true)
        mProgressDialog?.setOnCancelListener(
                DialogInterface.OnCancelListener { })
        mProgressDialog?.show()


        mLoadSoundFileThread = object : Thread() {

            override fun run() {
                try {
                    mMediaFile = MediaFile.create(File(fPath).getAbsolutePath())
                    if (mMediaFile == null) {
                        mProgressDialog?.dismiss()
                        return
                    }
                    mediaPlayer.setDataSource(fPath)
                    mediaPlayer.prepare()
                } catch (e: IOException) {
                    e.printStackTrace()
                    return
                } catch (e: MediaFile.InvalidInputException) {
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
            R.id.fab_cut -> saveRingtone(track_title.subSequence(0, track_title?.length.toString().toInt()), start_point, end_point)
        }
    }


    /***
     * method to handle play pause of song
     */
    internal fun handlePlayPause() {

        if (mediaPlayer.isPlaying) {
            iv_play_pause?.setImageResource(R.drawable.play_button)
            mediaPlayer.pause()
            current_time = mediaPlayer.currentPosition.toLong()
            chronometer_song_play?.stop()
            iv_artwork?.clearAnimation()
        } else {
            iv_play_pause?.setImageResource(R.drawable.pause_button)

            mediaPlayer.start()
            val eclapsedtime = SystemClock.elapsedRealtime()
            chronometer_song_play?.setBase(eclapsedtime - current_time)
            chronometer_song_play?.start()
            iv_artwork?.startAnimation(animation)

        }

    }

    private fun getProgress(d: Long): Int {
        var x = 0
        val p = d * 100 / total_duration
        x = p.toInt()
        return x
    }

    private fun setmediaProgress(p: Int) {
        val progress = (total_duration * p / 100).toInt()
        mediaPlayer.seekTo(progress)
        val eclapsedtime = SystemClock.elapsedRealtime()
        chronometer_song_play?.setBase(eclapsedtime - progress)
        chronometer_song_play?.start()

    }

    private fun getDisplayTextFrompProgress(p: Int): String {
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


    override fun onBackPressed() {
        super.onBackPressed()
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
        }
        mediaPlayer.release()
        animation?.cancel()
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

                    MediaFile.create(outPath)
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

        /*values.put(MediaStore.Audio.Media.IS_RINGTONE,
                mNewFileKind == FileSaveDialog.FILE_KIND_RINGTONE);
        values.put(MediaStore.Audio.Media.IS_NOTIFICATION,
                mNewFileKind == FileSaveDialog.FILE_KIND_NOTIFICATION);
        values.put(MediaStore.Audio.Media.IS_ALARM,
                mNewFileKind == FileSaveDialog.FILE_KIND_ALARM);
        values.put(MediaStore.Audio.Media.IS_MUSIC,
                mNewFileKind == FileSaveDialog.FILE_KIND_MUSIC);*/

        // Insert it into the database
        val uri = MediaStore.Audio.Media.getContentUriForPath(outPath)
        val newUri = contentResolver.insert(uri, values)
        setResult(RESULT_OK, Intent().setData(newUri))

        // If Ringdroid was launched to get content, just return
        /* if (mWasGetContentIntent) {
            finish();
            return;
        }*/

        /*// There's nothing more to do with music or an alarm.  Show a
        // success message and then quit.
        if (mNewFileKind == FileSaveDialog.FILE_KIND_MUSIC ||
                mNewFileKind == FileSaveDialog.FILE_KIND_ALARM) {
            Toast.makeText(this,
                    R.string.save_success_message,
                    Toast.LENGTH_SHORT)
                    .show();
            finish();
            return;
        }

        // If it's a notification, give the user the option of making
        // this their default notification.  If they saye no, w're finished.
        if (mNewFileKind == FileSaveDialog.FILE_KIND_NOTIFICATION) {
            new AlertDialog.Builder(RingdroidEditActivity.this)
                    .setTitle(R.string.alert_title_success)
                    .setMessage(R.string.set_default_notification)
                    .setPositiveButton(R.string.alert_yes_button,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                                    int whichButton) {
                                    RingtoneManager.setActualDefaultRingtoneUri(
                                            RingdroidEditActivity.this,
                                            RingtoneManager.TYPE_NOTIFICATION,
                                            newUri);
                                    finish();
                                }
                            })
                    .setNegativeButton(
                            R.string.alert_no_button,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    finish();
                                }
                            })
                    .setCancelable(false)
                    .show();
            return;
        }
*/
        // If we get here, that means the type is a ringtone.  There are
        // three choices: make this your default ringtone, assign it to a
        // contact, or do nothing.

        val handler = object : Handler() {
            override fun handleMessage(response: Message) {
                val actionId = response.arg1
                when (actionId) {
                /* case R.id.button_make_default:
                        RingtoneManager.setActualDefaultRingtoneUri(
                                Mp3Cutter.this,
                                RingtoneManager.TYPE_RINGTONE,
                                newUri);
                        Toast.makeText(
                                Mp3Cutter.this,
                                R.string.default_ringtone_success_message,
                                Toast.LENGTH_SHORT)
                                .show();
                        finish();
                        break;
                    case R.id.button_choose_contact:
                        chooseContactForRingtone(newUri);
                        break;
                    default:
                    case R.id.button_do_nothing:
                        finish();
                        break;*/
                    else -> finish()
                }
            }
        }

        Toast.makeText(this, "Save success", Toast.LENGTH_SHORT).show()
        /* Message message = Message.obtain(handler);
        AfterSaveActionDialog dlog = new AfterSaveActionDialog(
                this, message);
        dlog.show();*/
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
}