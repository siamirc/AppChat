package com.example

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PlaybackState {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    ERROR
}

data class RadioStation(
    val id: String,
    val name: String,
    val url: String,
    val description: String
)

class RadioPlayerManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentStation = MutableStateFlow<RadioStation?>(null)
    val currentStation: StateFlow<RadioStation?> = _currentStation.asStateFlow()

    private val _volume = MutableStateFlow(1.0f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    val stations = listOf(
        RadioStation(
            id = "icecast",
            name = "ThaiIRC Icecast",
            url = "http://icecast.thaiiirc.com:8000",
            description = "ฟังเพลงออนไลน์ 24 ชม. ผ่านเซิร์ฟเวอร์ Icecast"
        ),
        RadioStation(
            id = "radio",
            name = "ThaiIRC Radio",
            url = "http://radio.thaiirc.com:8002",
            description = "วิทยุออนไลน์คลื่นชุมชน ThaiIRC"
        )
    )

    init {
        // Default to the first station
        _currentStation.value = stations.first()
    }

    fun selectStation(station: RadioStation) {
        val wasPlaying = _playbackState.value == PlaybackState.PLAYING || _playbackState.value == PlaybackState.BUFFERING
        _currentStation.value = station
        if (wasPlaying) {
            play()
        } else {
            stop()
        }
    }

    fun play() {
        val station = _currentStation.value ?: return
        stop()

        _playbackState.value = PlaybackState.BUFFERING
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(station.url)
                setVolume(_volume.value, _volume.value)
                
                setOnPreparedListener {
                    _playbackState.value = PlaybackState.PLAYING
                    it.start()
                    Log.d("RadioPlayer", "Started playing: ${station.name}")
                }
                
                setOnErrorListener { mp, what, extra ->
                    Log.e("RadioPlayer", "MediaPlayer Error: $what, $extra")
                    _playbackState.value = PlaybackState.ERROR
                    true
                }
                
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("RadioPlayer", "Error preparing MediaPlayer", e)
            _playbackState.value = PlaybackState.ERROR
        }
    }

    fun pause() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    _playbackState.value = PlaybackState.PAUSED
                    Log.d("RadioPlayer", "Paused playing")
                }
            }
        } catch (e: Exception) {
            Log.e("RadioPlayer", "Error pausing player", e)
        }
    }

    fun togglePlayPause() {
        when (_playbackState.value) {
            PlaybackState.PLAYING -> pause()
            PlaybackState.PAUSED -> {
                mediaPlayer?.let {
                    it.start()
                    _playbackState.value = PlaybackState.PLAYING
                } ?: play()
            }
            PlaybackState.BUFFERING -> stop()
            else -> play()
        }
    }

    fun stop() {
        _playbackState.value = PlaybackState.IDLE
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e("RadioPlayer", "Error releasing player", e)
        }
        mediaPlayer = null
    }

    fun setVolume(vol: Float) {
        val boundedVol = vol.coerceIn(0.0f, 1.0f)
        _volume.value = boundedVol
        try {
            mediaPlayer?.setVolume(boundedVol, boundedVol)
        } catch (e: Exception) {}
    }
}
