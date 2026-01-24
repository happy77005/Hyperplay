package com.example.first

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Int,
    val uri: Uri,
    val path: String? = null
)

data class Album(
    val name: String,
    val songs: List<Song>
)
