package com.example.meeera.cutsong.Adapter

import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.meeera.cutsong.Model.SongModel
import com.example.meeera.cutsong.R
import com.nostra13.universalimageloader.core.DisplayImageOptions
import com.nostra13.universalimageloader.core.ImageLoader

/**
 * Created by meeera on 24/10/17.
 */
class SongAdapter(var data : ArrayList<SongModel>, var context : Context, var itemclick : itemClick) : RecyclerView.Adapter<SongAdapter.viewHolder>() {

    var imageLoader : ImageLoader = ImageLoader.getInstance()
    var displayImageOption : DisplayImageOptions = DisplayImageOptions.Builder()
            .showImageOnLoading(R.drawable.place_holder)
            .showImageForEmptyUri(R.drawable.place_holder)
            .showImageOnFail(R.drawable.place_holder)
            .cacheInMemory(true)
            .cacheOnDisk(true)
            .considerExifParams(true)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .build()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): viewHolder {
        var view = LayoutInflater.from(parent?.context).inflate(R.layout.song_item, parent, false)
        var myViewholder = viewHolder(view)
        return myViewholder
    }

    override fun onBindViewHolder(holder: viewHolder, position: Int) {
        holder?.title?.text = data[position].song_name
        holder?.artict?.text = data[position].song_artist
        holder?.duration?.text = data[position].song_duration
        imageLoader.displayImage(data[position].song_pic, holder?.img, displayImageOption)
        holder?.ll?.setOnClickListener{
            itemclick.onItemClick(position)
        }

        holder.img.setOnClickListener{
            itemclick.playMusic(position)
        }
    }

    override fun getItemCount(): Int {
        return data.size
    }

    class viewHolder(itemView : View) : RecyclerView.ViewHolder(itemView) {
        var ll = itemView.findViewById<LinearLayout>(R.id.linearl)
        var img = itemView.findViewById<ImageView>(R.id.iv_song_thumb)
        var title = itemView.findViewById<TextView>(R.id.tv_song_title)
        var artict = itemView.findViewById<TextView>(R.id.tv_song_artist)
        var duration = itemView.findViewById<TextView>(R.id.tv_duration)
    }

    interface itemClick {
        fun onItemClick(position : Int)
        fun playMusic(position: Int)
    }
}