package com.example.meeera.cutsong.Adapter

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.meeera.cutsong.Model.SongModel
import com.example.meeera.cutsong.R
import com.example.meeera.cutsong.Utils.UtilDpToPixel
import com.nostra13.universalimageloader.core.DisplayImageOptions
import com.nostra13.universalimageloader.core.ImageLoader
import kotlinx.android.synthetic.main.song_item_layout.view.*
import kotlin.math.roundToInt

/**
 * Created by meeera on 24/10/17.
 */
class SongAdapter(var data : ArrayList<SongModel>, var context : Context, var itemclick : itemClick, var screenWidth: Float, var gridCount: Int) : RecyclerView.Adapter<SongAdapter.viewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): viewHolder {
        var view = LayoutInflater.from(parent.context).inflate(R.layout.song_item_layout, parent, false)
        var myViewholder = viewHolder(view)
        return myViewholder
    }

    override fun onBindViewHolder(holder: viewHolder, position: Int) {

        holder.itemView.song_name?.text = data[position].song_name
        holder.itemView.song_artist?.text = data[position].song_artist
        holder.itemView.linearl?.setOnClickListener{
            itemclick.onItemClick(position)
        }

        holder.itemView.play_btn.setOnClickListener{
            itemclick.playMusic(position)
        }

        holder.bindData(data[position].song_pic, screenWidth, context, gridCount)
    }

    override fun getItemCount(): Int {
        return data.size
    }

    class viewHolder(itemView : View) : RecyclerView.ViewHolder(itemView) {
        fun bindData(uri: String, screenWidth: Float, context: Context, count: Int){
            val imageDimension: Int = UtilDpToPixel.convertDpToPixel(screenWidth/count, context).roundToInt()
            val imagePaddingHorizontal: Int = UtilDpToPixel.convertDpToPixel(2F, itemView.context).roundToInt()
            val imagePaddingVertical: Int = UtilDpToPixel.convertDpToPixel(2F, itemView.context).roundToInt()
            val mLinearLayoutParam: LinearLayout.LayoutParams = LinearLayout.LayoutParams(imageDimension-imagePaddingHorizontal, imageDimension-imagePaddingVertical)
            itemView.song_item_card.layoutParams= mLinearLayoutParam

            Log.d("image height ","val $imageDimension $screenWidth")
            Glide.with(itemView).applyDefaultRequestOptions(RequestOptions()
                    .placeholder(R.drawable.place_holder)
                    .error(R.drawable.place_holder).centerCrop().override(imageDimension-imagePaddingHorizontal, imageDimension-imagePaddingVertical))
                    .load(Uri.parse(uri))
                    .into(itemView.song_image)
        }
    }

    interface itemClick {
        fun onItemClick(position : Int)
        fun playMusic(position: Int)
    }
}