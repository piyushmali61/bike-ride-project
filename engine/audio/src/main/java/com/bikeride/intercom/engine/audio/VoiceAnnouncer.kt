package com.bikeride.intercom.engine.audio

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Speaks short announcements ("Piyush: Slow down", "SOS sent") so riders never need to look
 * at the screen. Uses the phone's offline text-to-speech voice and the navigation-guidance
 * audio usage, which Android routes to a helmet headset like sat-nav prompts.
 */
@Singleton
class VoiceAnnouncer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    private val pending = ArrayDeque<String>()

    private fun ensure() {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            synchronized(this) {
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    tts?.language = Locale.getDefault()
                    tts?.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    while (pending.isNotEmpty()) speakNow(pending.removeFirst(), queue = true)
                } else {
                    Timber.w("Text-to-speech unavailable ($status)")
                    pending.clear()
                }
            }
        }
    }

    /** Speak [text]. Urgent messages interrupt whatever is being said. */
    @Synchronized
    fun say(text: String, urgent: Boolean = false) {
        val clean = speakable(text)
        if (clean.isBlank()) return
        ensure()
        if (!ready) {
            if (pending.size < 5) pending.addLast(clean)
            return
        }
        speakNow(clean, queue = !urgent)
    }

    private fun speakNow(text: String, queue: Boolean) {
        try {
            tts?.speak(text, if (queue) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
        } catch (e: Exception) {
            Timber.w(e, "TTS speak failed")
        }
    }

    companion object {
        /** Drops emoji and symbols that TTS would read out literally ("fuel pump emoji"). */
        fun speakable(text: String): String = buildString {
            var i = 0
            while (i < text.length) {
                val cp = text.codePointAt(i)
                if (Character.isLetterOrDigit(cp) || Character.isWhitespace(cp) || cp in ".,:;!?'-%()".map { it.code }) {
                    appendCodePoint(cp)
                }
                i += Character.charCount(cp)
            }
        }.replace(Regex("\\s+"), " ").trim()
    }
}
