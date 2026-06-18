package me.ash.reader.infrastructure.android

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Html
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextLanguage
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.preference.SettingsProvider
import me.ash.reader.infrastructure.preference.TtsSpeechRatePreference
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextToSpeechManager @Inject constructor(
    @ApplicationContext
    private val context: Context,
    @ApplicationScope
    private val coroutineScope: CoroutineScope,
    private val settingsProvider: SettingsProvider
) {

    private val _stateFlow = MutableStateFlow<State>(State.Idle)
    val stateFlow = _stateFlow.asStateFlow()

    var state
        get() = stateFlow.value
        private set(value) {
            _stateFlow.value = value
            if (value is State.Reading || value is State.Preparing) {
                acquireWakeLock()
            } else {
                releaseWakeLock()
            }
        }

    // WakeLock for keeping screen on / CPU running
    private var wakeLock: PowerManager.WakeLock? = null

    // Local TTS Engine
    private val localTts: TextToSpeech = initLocalTts()

    // Network TTS
    private var mediaPlayer: MediaPlayer? = null
    private var ttsUrlTemplate: String = ""

    init {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Reader:TextToSpeechWakeLock"
        ).apply {
            setReferenceCounted(false)
        }
    }

    private fun acquireWakeLock() {
        wakeLock?.let {
            if (!it.isHeld) {
                // Set a safe timeout (e.g., 30 minutes) to prevent infinite lock in case of bugs.
                // Since acquire() is called every time a new segment starts, this timeout is constantly refreshed.
                it.acquire(30 * 60 * 1000L)
            } else {
                // If already held, calling acquire again (with ref count false) might not update timeout 
                // depending on implementation, but typically we want to ensure it stays held.
                // For safety with setReferenceCounted(false), we can release and re-acquire to refresh timeout
                // or just rely on the fact that segments are short.
                // A simple acquire with timeout updates the timeout for the lock.
                it.acquire(30 * 60 * 1000L)
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Error releasing WakeLock")
        }
    }

    private fun initLocalTts(): TextToSpeech {
        return TextToSpeech(context, TextToSpeech.OnInitListener {
            when (it) {
                TextToSpeech.SUCCESS -> {}
                else -> {
                    Timber.e("TextToSpeech initialization failed $it")
                }
            }
        })
    }
    
    // --- Configuration Logic ---

    private fun loadNetworkConfig(): Boolean {
        val jsonString = settingsProvider.settings.ttsConfig
        if (jsonString.isBlank()) return false
        
        return try {
            val json = JSONObject(jsonString)
            ttsUrlTemplate = json.optString("url", "")
            ttsUrlTemplate.isNotEmpty()
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse TTS config")
            false
        }
    }

    // --- State Definition ---

    sealed interface State {
        object Idle : State
        object Preparing : State
        class Reading(val current: Int, val total: Int) : State {
            val progress: Float
                get() = current.toFloat() / total
        }

        object Error : State
    }


    // --- Public API ---

    fun readHtml(htmlContent: String) {
        coroutineScope.launch {
            val plainText = Html.fromHtml(htmlContent, Html.FROM_HTML_MODE_LEGACY).toString()
            readText(plainText)
        }
    }

    private fun readText(text: String) {
        stop()

        if (state != State.Idle) return

        state = State.Preparing
        
        // Split text into manageable segments
        val textSegments = splitTextIntoSegments(text)
        val total = textSegments.size
        state = State.Reading(0, total)

        // Determine which engine to use
        if (loadNetworkConfig()) {
            readTextWithNetwork(textSegments)
        } else {
            readTextWithLocal(text, textSegments)
        }
    }
    
    // Improved splitting logic: Paragraphs -> Sentences -> Chunks
    private fun splitTextIntoSegments(text: String): List<String> {
        val maxSegmentLength = 300 
        
        return text.split("\n")
            .filterNot { it.isBlank() }
            .flatMap { paragraph ->
                if (paragraph.length <= maxSegmentLength) {
                    listOf(paragraph)
                } else {
                    val sentences = paragraph.split(Regex("(?<=[。？！.?!])"))
                        .filter { it.isNotBlank() }
                    
                    sentences.flatMap { sentence ->
                        if (sentence.length <= maxSegmentLength) {
                            listOf(sentence)
                        } else {
                            val parts = sentence.split(Regex("(?<=[，,、;；])"))
                                .filter { it.isNotBlank() }
                            
                            parts.flatMap { part ->
                                if (part.length <= maxSegmentLength) {
                                    listOf(part)
                                } else {
                                    part.chunked(maxSegmentLength)
                                }
                            }
                        }
                    }
                }
            }
    }

    fun stop() {
        // Ensure resources are released
        try {
            if (localTts.isSpeaking) {
                localTts.stop()
            }
        } catch (e: Exception) {
            Timber.w(e, "Error stopping local TTS")
        }
        
        try {
            stopMediaPlayer()
        } catch (e: Exception) {
            Timber.w(e, "Error stopping media player")
        }

        // Must reset state to Idle to release WakeLock (via setter)
        if (state != State.Idle) {
            state = State.Idle
        }
    }
    
    private fun stopMediaPlayer() {
         mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            } catch (e: Exception) {
                Timber.w(e, "Error releasing MediaPlayer")
            }
        }
        mediaPlayer = null
    }
    
    // --- Local TTS Implementation ---

    private fun readTextWithLocal(fullText: String, segments: List<String>) {
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (segments.isNotEmpty()) {
                val sampleText = segments.first().take(500)
                val locale = context.detectLocaleFromText(sampleText).firstOrNull()?.locale
                if (locale != null) {
                    localTts.language = locale
                }
            }
        }

        localTts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                val index = utteranceId?.toIntOrNull() ?: 0
                if (state !is State.Idle) {
                    // Update state, which refreshes WakeLock timeout
                    state = State.Reading(index, segments.size)
                }
            }

            override fun onDone(utteranceId: String?) {
                val index = utteranceId?.toIntOrNull() ?: 0
                val cur = state
                if (cur is State.Reading && index >= cur.total - 1) { 
                   state = State.Idle
                }
            }

            override fun onError(utteranceId: String?) {
                Timber.e("Local TTS Error: $utteranceId")
                state = State.Error
            }
        })

        localTts.setSpeechRate(settingsProvider.settings.ttsSpeechRate.coerceSpeechRate())

        segments.forEachIndexed { index, segment ->
            if (segment.length < TextToSpeech.getMaxSpeechInputLength()) {
                val params = android.os.Bundle()
                localTts.speak(segment, TextToSpeech.QUEUE_ADD, params, index.toString())
            } else {
                 Timber.e("Segment still too long for local TTS: ${segment.length}")
            }
        }
    }
    
    // --- Network TTS Implementation ---

    private fun readTextWithNetwork(segments: List<String>) {
        if (segments.isEmpty()) {
            state = State.Idle
            return
        }
        
        playNextSegmentNetwork(segments, 0)
    }

    private fun playNextSegmentNetwork(segments: List<String>, index: Int) {
        if (index >= segments.size) {
            state = State.Idle
            return
        }
        if (state == State.Idle) return

        // Update state, which refreshes WakeLock timeout
        state = State.Reading(index + 1, segments.size)
        val text = segments[index]
        
        if (text.isBlank()) {
            playNextSegmentNetwork(segments, index + 1)
            return
        }

        val speechRate = settingsProvider.settings.ttsSpeechRate.coerceSpeechRate()

        try {
            val url = generateNetworkUrl(text, speechRate)
            if (url == null) {
                Timber.e("Generated URL is null for segment: $text")
                playNextSegmentNetwork(segments, index + 1)
                return
            }
            
            playAudioStream(url, 
                onCompletion = {
                    playNextSegmentNetwork(segments, index + 1)
                },
                onError = {
                    Timber.e("Network TTS playback failed for segment $index")
                    playNextSegmentNetwork(segments, index + 1)
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Error processing network TTS loop")
            state = State.Error
        }
    }

    private fun generateNetworkUrl(speakText: String, speechRate: Float): String? {
        if (ttsUrlTemplate.isEmpty()) return null
        
        var finalUrl = ttsUrlTemplate.replace("{{speakSpeed/10}}", speechRate.toString())

        val escapedText = speakText
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        val urlEncodedText = URLEncoder.encode(escapedText, "UTF-8")
        
        val textTemplateRegex = "\\{\\{.*?speakText.*?\\}\\}".toRegex()
        
        return finalUrl.replace(textTemplateRegex, urlEncodedText)
    }

    private fun Float.coerceSpeechRate(): Float =
        coerceIn(TtsSpeechRatePreference.min, TtsSpeechRatePreference.max)

    private fun playAudioStream(url: String, onCompletion: () -> Unit, onError: () -> Unit) {
        stopMediaPlayer()
        
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            // Hold a separate WakeLock within MediaPlayer during playback
            setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            
            try {
                setDataSource(url)
                prepareAsync()
                
                setOnPreparedListener { 
                    it.start() 
                }
                
                setOnCompletionListener {
                    onCompletion()
                }
                
                setOnErrorListener { _, what, extra ->
                    Timber.e("MediaPlayer error: what=$what, extra=$extra")
                    onError()
                    true
                }
            } catch (e: Exception) {
                Timber.e(e, "Error setting data source or preparing MediaPlayer")
                onError()
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun Context.detectLocaleFromText(
    text: CharSequence,
    minConfidence: Float = 80.0f,
): Sequence<LocaleWithConfidence> {
    val textClassificationManager =
        getSystemService<TextClassificationManager>() ?: return emptySequence()
    val textClassifier = textClassificationManager.textClassifier

    val textRequest = TextLanguage.Request.Builder(text).build()
    val detectedLanguage = textClassifier.detectLanguage(textRequest)

    return sequence {
        for (i in 0 until detectedLanguage.localeHypothesisCount) {
            val localeDetected = detectedLanguage.getLocale(i)
            val confidence = detectedLanguage.getConfidenceScore(localeDetected) * 100.0f
            if (confidence >= minConfidence) {
                yield(
                    LocaleWithConfidence(
                        locale = localeDetected.toLocale(),
                        confidence = confidence,
                    ),
                )
            }
        }
    }
}

data class LocaleWithConfidence(
    val locale: Locale,
    val confidence: Float,
)
