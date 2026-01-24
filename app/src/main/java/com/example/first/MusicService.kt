package com.example.first

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Binder
import android.os.IBinder
import android.util.Log

class MusicService : Service() {

    private val TAG = "MusicService"
    private var mediaPlayer: MediaPlayer? = null
    private val binder = MusicBinder()
    private var onCompletionListener: (() -> Unit)? = null

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    fun playSong(song: Song) {
        Log.d(TAG, "Playing song: ${song.title}")
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(applicationContext, song.uri)
                setOnPreparedListener { 
                    Log.d(TAG, "MediaPlayer prepared, starting playback")
                    it.start() 
                }
                setOnCompletionListener { 
                    Log.d(TAG, "MediaPlayer playback completed")
                    onCompletionListener?.invoke() 
                }
                setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "MediaPlayer Error: what=$what, extra=$extra")
                    false
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting playback", e)
        }
    }

    fun pauseSong() {
        Log.d(TAG, "Pausing playback")
        mediaPlayer?.pause()
    }

    fun resumeSong() {
        Log.d(TAG, "Resuming playback")
        mediaPlayer?.start()
    }

    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }

    fun setOnCompletionListener(listener: () -> Unit) {
        this.onCompletionListener = listener
    }

    override fun onDestroy() {
        Log.d(TAG, "Service being destroyed")
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
