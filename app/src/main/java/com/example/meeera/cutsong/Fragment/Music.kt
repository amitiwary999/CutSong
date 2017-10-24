package com.example.meeera.cutsong.Fragment

import android.Manifest
import android.support.v4.app.Fragment
import android.provider.MediaStore
import android.content.ContentResolver
import android.Manifest.permission
import android.Manifest.permission.READ_EXTERNAL_STORAGE
import com.example.meeera.cutsong.Activity.MainActivity
import android.support.v4.app.ActivityCompat
import android.content.pm.PackageManager
import android.support.v4.content.ContextCompat



/**
 * Created by meeera on 24/10/17.
 */
class Music : Fragment() {

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
        }
    }
}