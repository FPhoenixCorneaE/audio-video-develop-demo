package com.fphoenixcorneae.mediacodec

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import android.view.SurfaceHolder

object MediaPlayerHelper {
    private var mediaPlayer: MediaPlayer? = null

    fun prepare(
        videoPath: String,
        holder: SurfaceHolder? = null,
        prepareListener: MediaPlayer.OnPreparedListener? = null,
    ) {
        mediaPlayer = MediaPlayer()
        runCatching {
            mediaPlayer?.apply {
                reset()
                setDataSource(videoPath)
                setAudioStreamType(AudioManager.STREAM_MUSIC)
                setDisplay(holder)
                prepareAsync()
                setOnPreparedListener(prepareListener)
            }
        }.onFailure {
            Log.d("MediaPlayer", "prepare: $it")
        }
    }

    fun prepare(context: Context, rawId: Int, holder: SurfaceHolder) {
        runCatching {
            MediaPlayer.create(context, rawId)
                .apply {
                    mediaPlayer = this
                    setDisplay(holder)
                }
        }.onFailure {
            Log.d("MediaPlayer", "prepare: $it")
        }
    }

    fun start() {
        mediaPlayer?.start()
    }

    fun pause() {
        mediaPlayer?.pause()
    }

    fun release() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.stop()
        }
        mediaPlayer?.release()
        mediaPlayer = null
    }
}