package com.example.meeera.cutsong.Activity

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import com.crashlytics.android.Crashlytics;
import io.fabric.sdk.android.Fabric;
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.meeera.cutsong.Adapter.SongAdapter
import com.example.meeera.cutsong.AudioPlayer
import com.example.meeera.cutsong.Model.SongModel
import com.example.meeera.cutsong.R
import com.nostra13.universalimageloader.core.ImageLoader
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration
import kotlinx.android.synthetic.main.activity_main.*
import java.util.ArrayList



class MainActivity : AppCompatActivity(), SongAdapter.itemClick{

    private val albumArtUri = Uri.parse("content://media/external/audio/albumart")
    private val iAlbumArtUri = Uri.parse("content://media/internal/audio/albumart")
    var songList : ArrayList<SongModel> = ArrayList()
    var adapter : SongAdapter?= null
    lateinit var audioPlayer : AudioPlayer
    var width: Float = 0F
    val spanCount = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Fabric.with(this, Crashlytics())
        val configuration = ImageLoaderConfiguration.Builder(this).build()
        ImageLoader.getInstance().init(configuration)
        audioPlayer = AudioPlayer(this)
        lifecycle.addObserver(audioPlayer)
        retrieveSong()

        width = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        adapter = SongAdapter(songList, this, this, width, spanCount)
        rv_song_list?.adapter = adapter
        rv_song_list?.layoutManager = GridLayoutManager(this, spanCount)
    }

    private fun retrieveSong() {
        val musicResolver = contentResolver
        val musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val musicInternalUri = MediaStore.Audio.Media.INTERNAL_CONTENT_URI
        val musicCursor = musicResolver.query(musicUri, null, null, null, null)
        val musicInternalCursor = musicResolver.query(musicInternalUri, null, null, null, null)
        if(musicCursor != null && musicCursor.moveToFirst()){
            val titleColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val artistColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val data = musicCursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            val duration = musicCursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val albumId = musicCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
            do {
                var artist : String = musicCursor.getString(artistColumn)
                var path : String = musicCursor.getString(data)
                var duration : String = setCorrectDuration(musicCursor.getString(duration)).toString()
                var title : String = musicCursor.getString(titleColumn)
                var img : String = ContentUris.withAppendedId(albumArtUri, musicCursor.getLong(albumId)).toString()
                songList.add(SongModel(title, path, img, artist, duration))
            }while (musicCursor.moveToNext())
            musicCursor.close()
        }
    }

    private fun setCorrectDuration(duration: String): String? {
        var songs_duration = duration

        val time = songs_duration.toInt()

        var seconds = time / 1000
        val minutes = seconds / 60
        seconds %=  60

        if (seconds < 10) {
            songs_duration = minutes.toString() + ":0" + seconds.toString()
        } else {

            songs_duration = minutes.toString() + ":" + seconds.toString()
        }
        return songs_duration


    }

    override fun onItemClick(position: Int) {
        val intent = Intent(this, SongCutActivity::class.java)
        intent.putExtra("fpath", songList[position].song_path)
        intent.putExtra("artwork", songList[position].song_pic)
        startActivity(intent)
    }

    override fun playMusic(position: Int) {
        Log.d("MainActivity ","uri "+Uri.parse(songList[position].song_path))
        audioPlayer?.openAudio(Uri.parse(songList[position].song_path))
    }
}
