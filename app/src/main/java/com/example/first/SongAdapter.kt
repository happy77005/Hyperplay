package com.example.first

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SongAdapter(private val onSongClick: (Song) -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items = mutableListOf<Any>()

    companion object {
        private const val TYPE_SONG = 0
        private const val TYPE_ALBUM_HEADER = 1
    }

    class SongViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvArtist: TextView = view.findViewById(R.id.tvArtist)
    }

    class AlbumViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAlbumName: TextView = view.findViewById(R.id.tvAlbumName)
    }

    fun setData(newItems: List<Any>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position] is Song) TYPE_SONG else TYPE_ALBUM_HEADER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_SONG) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
            SongViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_album_header, parent, false)
            AlbumViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is SongViewHolder && item is Song) {
            holder.tvTitle.text = item.title
            holder.tvArtist.text = item.artist
            holder.itemView.setOnClickListener { onSongClick(item) }
        } else if (holder is AlbumViewHolder && item is String) {
            holder.tvAlbumName.text = item
        }
    }

    override fun getItemCount() = items.size
}
