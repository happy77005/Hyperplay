package com.example.first

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SongAdapter
    private val allSongs = mutableListOf<Song>()

    private var musicService: MusicService? = null
    private var isBound = false
    private var currentSong: Song? = null

    private lateinit var miniPlayer: View
    private lateinit var tvMiniTitle: TextView
    private lateinit var tvMiniArtist: TextView
    private lateinit var btnPlayPause: ImageButton

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            Log.d(TAG, "Service Connected")
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            Log.d(TAG, "Service Disconnected")
            isBound = false
            musicService = null
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            loadAllSongs()
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            loadSongsFromFolder(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        miniPlayer = findViewById(R.id.miniPlayer)
        tvMiniTitle = findViewById(R.id.tvMiniTitle)
        tvMiniArtist = findViewById(R.id.tvMiniArtist)
        btnPlayPause = findViewById(R.id.btnPlayPause)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = SongAdapter { song ->
            playSong(song)
        }
        recyclerView.adapter = adapter

        findViewById<Button>(R.id.btnAllSongs).setOnClickListener {
            checkPermissionAndLoadAll()
        }

        findViewById<Button>(R.id.btnSelectFolder).setOnClickListener {
            folderPickerLauncher.launch(null)
        }

        btnPlayPause.setOnClickListener {
            togglePlayback()
        }

        Intent(this, MusicService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
            startService(intent) // Also start it so it keeps running
        }

        checkPermissionAndLoadAll()
    }

    private fun playSong(song: Song) {
        Log.d(TAG, "playSong called for: ${song.title}")
        if (!isBound || musicService == null) {
            Log.w(TAG, "MusicService not bound yet")
            Toast.makeText(this, "Service not ready", Toast.LENGTH_SHORT).show()
            return
        }

        currentSong = song
        miniPlayer.visibility = View.VISIBLE
        tvMiniTitle.text = song.title
        tvMiniArtist.text = song.artist

        musicService?.playSong(song)
        btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
        
        musicService?.setOnCompletionListener {
            Log.d(TAG, "Song completed")
            runOnUiThread {
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            }
        }
    }

    private fun togglePlayback() {
        musicService?.let {
            if (it.isPlaying()) {
                it.pauseSong()
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            } else {
                it.resumeSong()
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            }
        }
    }

    private fun checkPermissionAndLoadAll() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadAllSongs()
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    private fun loadAllSongs() {
        val songs = fetchSongs(null)
        allSongs.clear()
        allSongs.addAll(songs)
        adapter.setData(songs)
    }

    private fun loadSongsFromFolder(treeUri: Uri) {
        val folderPath = getPathFromUri(treeUri)
        val songs = fetchSongs(folderPath)
        
        val groupedItems = mutableListOf<Any>()
        val albums = songs.groupBy { it.album }
        
        albums.forEach { (albumName, albumSongs) ->
            groupedItems.add(albumName)
            groupedItems.addAll(albumSongs)
        }
        
        adapter.setData(groupedItems)
    }

    private fun fetchSongs(filterPath: String?): List<Song> {
        val songs = mutableListOf<Song>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )

        val selection = if (filterPath != null) {
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DATA} LIKE ?"
        } else {
            "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        }
        
        val selectionArgs = if (filterPath != null) {
            arrayOf("$filterPath%")
        } else {
            null
        }

        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                songs.add(Song(
                    id,
                    cursor.getString(titleCol),
                    cursor.getString(artistCol),
                    cursor.getString(albumCol),
                    cursor.getInt(durationCol),
                    uri,
                    cursor.getString(dataCol)
                ))
            }
        }
        return songs
    }

    private fun getPathFromUri(uri: Uri): String? {
        if ("com.android.externalstorage.documents" == uri.authority) {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val split = docId.split(":")
            if (split.size >= 2) {
                val type = split[0]
                if ("primary".equals(type, ignoreCase = true)) {
                    return "/storage/emulated/0/" + split[1]
                }
            }
        }
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}
