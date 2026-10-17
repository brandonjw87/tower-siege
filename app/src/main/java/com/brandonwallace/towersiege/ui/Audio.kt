package com.brandonwallace.towersiege.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import com.brandonwallace.towersiege.game.GameEvent
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Fully procedural audio: SFX rendered to WAV in cache and played via SoundPool; music is a rendered loop. */
class Audio {
    private val rate = 22050
    private var pool: SoundPool? = null
    private val ids = HashMap<GameEvent, Int>()
    private var music: AudioTrack? = null
    private val lastPlay = HashMap<GameEvent, Long>()
    var sfxOn = true
    var musicOn = true
        set(v) { field = v; if (v) resumeMusic() else pauseMusic() }
    private var started = false

    fun init(ctx: Context) {
        if (started) return
        started = true
        Thread {
            try {
                val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                val sp = SoundPool.Builder().setMaxStreams(10).setAudioAttributes(attrs).build()
                for (ev in GameEvent.entries) {
                    val f = File(ctx.cacheDir, "sfx_${ev.name}.wav")
                    writeWav(f, synth(ev))
                    ids[ev] = sp.load(f.absolutePath, 1)
                }
                pool = sp
                buildMusic()
            } catch (_: Throwable) { }
        }.start()
    }

    fun play(ev: GameEvent) {
        if (!sfxOn) return
        val sp = pool ?: return
        val now = System.currentTimeMillis()
        val minGap = when (ev) { GameEvent.SHOOT, GameEvent.HIT, GameEvent.DIE, GameEvent.ZAP -> 70L; else -> 30L }
        if (now - (lastPlay[ev] ?: 0L) < minGap) return
        lastPlay[ev] = now
        val vol = when (ev) { GameEvent.SHOOT, GameEvent.HIT -> 0.35f; GameEvent.DIE -> 0.5f; else -> 0.8f }
        ids[ev]?.let { sp.play(it, vol, vol, 1, 0, 1f) }
    }

    private fun env(i: Int, n: Int, decay: Float) = exp(-decay * i / n.toFloat()) * (if (i < 80) i / 80f else 1f)

    private fun synth(ev: GameEvent): ShortArray {
        val r = Random(ev.ordinal)
        fun tone(dur: Float, f0: Float, f1: Float, decay: Float, noise: Float = 0f, square: Boolean = false): ShortArray {
            val n = (rate * dur).toInt(); val out = ShortArray(n); var ph = 0.0
            for (i in 0 until n) {
                val t = i / n.toFloat(); val f = f0 + (f1 - f0) * t
                ph += 2 * PI * f / rate
                var s = if (square) (if (sin(ph) > 0) 0.6 else -0.6) else sin(ph)
                s = s * (1 - noise) + (r.nextFloat() * 2 - 1) * noise
                out[i] = (s * env(i, n, decay) * 12000).toInt().toShort()
            }
            return out
        }
        fun seq(vararg notes: Float, len: Float = 0.09f): ShortArray {
            val parts = notes.map { tone(len, it, it, 3f, square = true) }
            return parts.fold(ShortArray(0)) { a, b -> a + b }
        }
        return when (ev) {
            GameEvent.SHOOT -> tone(0.08f, 900f, 500f, 6f, 0.2f)
            GameEvent.CANNON -> tone(0.3f, 140f, 40f, 5f, 0.6f)
            GameEvent.ZAP -> tone(0.18f, 1400f, 300f, 4f, 0.5f, true)
            GameEvent.FREEZE -> tone(0.3f, 2200f, 1600f, 5f, 0.3f)
            GameEvent.HIT -> tone(0.05f, 300f, 200f, 8f, 0.7f)
            GameEvent.DIE -> tone(0.14f, 500f, 120f, 5f, 0.4f, true)
            GameEvent.LEAK -> tone(0.35f, 220f, 110f, 3f, 0.1f, true)
            GameEvent.WAVE -> seq(392f, 523f, 659f, 784f)
            GameEvent.BUILD -> seq(523f, 784f, len = 0.07f)
            GameEvent.UPGRADE -> seq(523f, 659f, 784f, 1046f, len = 0.07f)
            GameEvent.SELL -> seq(784f, 523f, len = 0.07f)
            GameEvent.WIN -> seq(523f, 659f, 784f, 1046f, 784f, 1046f, len = 0.14f)
            GameEvent.LOSE -> seq(392f, 349f, 311f, 262f, len = 0.2f)
            GameEvent.BOSS -> tone(0.9f, 70f, 55f, 1.5f, 0.35f, true)
            GameEvent.COIN -> seq(988f, 1319f, len = 0.08f)
        }
    }

    private fun buildMusic() {
        // 8 bars, 100 bpm, gentle minor-key arpeggio with bass and soft pad.
        val bpm = 100f; val beat = 60f / bpm; val bars = 8
        val n = (rate * beat * 4 * bars).toInt()
        val buf = FloatArray(n)
        val roots = floatArrayOf(220f, 174.61f, 261.63f, 196f, 220f, 174.61f, 146.83f, 164.81f)
        val chordIv = arrayOf(floatArrayOf(1f, 1.189f, 1.498f), floatArrayOf(1f, 1.26f, 1.498f), floatArrayOf(1f, 1.26f, 1.498f), floatArrayOf(1f, 1.26f, 1.498f),
            floatArrayOf(1f, 1.189f, 1.498f), floatArrayOf(1f, 1.26f, 1.498f), floatArrayOf(1f, 1.189f, 1.498f), floatArrayOf(1f, 1.26f, 1.498f))
        val step = beat / 2
        for (bar in 0 until bars) {
            val root = roots[bar]; val iv = chordIv[bar]
            for (k in 0 until 8) {
                val start = ((bar * 4 * beat + k * step) * rate).toInt()
                val f = root * 2 * iv[k % 3] * (if (k >= 4) 2f else 1f)
                val len = (step * 1.6f * rate).toInt()
                for (i in 0 until len) { val idx = start + i; if (idx >= n) break
                    buf[idx] += (sin(2 * PI * f * i / rate) * exp(-4.0 * i / len) * 0.18).toFloat() }
            }
            val bs = (bar * 4 * beat * rate).toInt(); val bl = (4 * beat * rate).toInt()
            for (i in 0 until bl) { val idx = bs + i; if (idx >= n) break
                val t = i.toFloat() / rate
                buf[idx] += (sin(2 * PI * root / 2 * t) * 0.22 * exp(-1.2 * (i % (beat * rate).toInt()) / (beat * rate))).toFloat()
                for (m in iv) buf[idx] += (sin(2 * PI * root * m * t) * 0.035).toFloat()
            }
        }
        val pcm = ShortArray(n) { (buf[it].coerceIn(-1f, 1f) * 14000).toInt().toShort() }
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(n * 2).build()
        track.write(pcm, 0, n)
        track.setLoopPoints(0, n, -1)
        track.setVolume(0.5f)
        music = track
        if (musicOn) track.play()
    }

    fun pauseMusic() { try { music?.pause() } catch (_: Throwable) {} }
    fun resumeMusic() { try { if (musicOn) music?.play() } catch (_: Throwable) {} }
    fun release() { try { music?.release(); pool?.release() } catch (_: Throwable) {} ; music = null; pool = null }

    private fun writeWav(f: File, pcm: ShortArray) {
        val data = ByteBuffer.allocate(44 + pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()).putInt(36 + pcm.size * 2).put("WAVE".toByteArray())
        data.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        data.put("data".toByteArray()).putInt(pcm.size * 2)
        for (s in pcm) data.putShort(s)
        FileOutputStream(f).use { it.write(data.array()) }
    }
}
