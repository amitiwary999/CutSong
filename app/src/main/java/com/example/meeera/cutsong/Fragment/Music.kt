package com.example.meeera.cutsong.Fragment

import android.Manifest
import android.support.v4.app.Fragment
import android.provider.MediaStore
import android.content.ContentResolver
import android.Manifest.permission
import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import com.example.meeera.cutsong.Activity.MainActivity
import android.support.v4.app.ActivityCompat
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.meeera.cutsong.Activity.SongCutActivity
import com.example.meeera.cutsong.Adapter.SongAdapter
import com.example.meeera.cutsong.Model.SongModel
import com.example.meeera.cutsong.R


/**
 * Created by meeera on 24/10/17.
 */
class Music() : Fragment(), SongAdapter.itemClick {

    private val albumArtUri = Uri.parse("content://media/external/audio/albumart")
    private val malbumArtUri = Uri.parse("content://media/internal/audio/albumart")
    var songList : ArrayList<SongModel> = ArrayList()
    var adapter : SongAdapter ?= null
    var recyclerView : RecyclerView ?= null

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        var view = inflater?.inflate(R.layout.music_fragment, container, false)
        recyclerView = view?.findViewById(R.id.rv_song_list)
        adapter = SongAdapter(songList, context, this)
        recyclerView?.adapter = adapter
        recyclerView?.layoutManager = LinearLayoutManager(context)
        return view
    }

    fun retrieveSong() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 12345)
            return
        }
        val musicResolver = activity.contentResolver
        val musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val musicCursor = musicResolver.query(musicUri, null, null, null, null)
        if(musicCursor != null && musicCursor.moveToFirst()){
            val titleColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val idCloumn = musicCursor.getColumnIndex(MediaStore.Audio.Media._ID)
            val artistColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val data = musicCursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            val duration = musicCursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val albumId = musicCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
            do {
                var artist : String = musicCursor.getString(artistColumn)
                var path : String = musicCursor.getString(data)
                var duration : String = musicCursor.getString(duration)
                var title : String = musicCursor.getString(titleColumn)
                var img : String = ContentUris.withAppendedId(albumArtUri, musicCursor.getLong(albumId)).toString()
                songList.add(SongModel(title, path, img, artist, duration))
            }while (musicCursor.moveToNext())
        }
    }

    override fun onItemClick(position: Int) {
        val intent = Intent(activity as MainActivity, SongCutActivity::class.java)
        intent.putExtra("fpath", songList[position].song_path)
        intent.putExtra("artwork", songList[position].song_pic)
        startActivity(intent)
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        retrieveSong()
    }

}