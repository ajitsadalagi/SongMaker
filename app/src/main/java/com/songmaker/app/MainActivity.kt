package com.songmaker.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.ProgressBar
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var etTextInput: EditText
    private lateinit var etLyricPrompt: EditText
    private lateinit var btnVoiceInput: Button
    private lateinit var btnConvertToSong: Button
    private lateinit var btnGenerateLyrics: Button
    private lateinit var tvVoiceTranscription: TextView
    private lateinit var tvLyricStatus: TextView
    private lateinit var tvLyricSource: TextView
    private lateinit var progressLyrics: ProgressBar
    private lateinit var spinnerVoice: Spinner
    private lateinit var sbPitch: SeekBar
    private lateinit var sbSpeed: SeekBar
    private lateinit var tvPitchValue: TextView
    private lateinit var tvSpeedValue: TextView
    private lateinit var btnCreateSongMusicApi: Button
    private lateinit var btnPlayMusicApi: ImageButton
    private lateinit var btnStopMusicApi: ImageButton
    private lateinit var btnDownloadMusicApi: ImageButton
    private lateinit var btnMyDownloadsMusicApi: Button
    private lateinit var progressMusicApi: ProgressBar

    private var musicApiMediaPlayer: android.media.MediaPlayer? = null
    private var lastMusicApiAudioUrl: String? = null

    private val downloadedSongsDir by lazy { File(filesDir, "downloaded_songs").apply { mkdirs() } }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isVoiceInputActive = false

    private var textToSpeech: TextToSpeech? = null
    private var isPlaying = false
    private var voiceOptions: List<VoiceOption> = emptyList()
    private var selectedVoice: Voice? = null
    private var selectedVoiceIsFemale: Boolean = false

    /** Phrase-by-phrase playback for singing tone; pitch varies per phrase */
    private var songPhrases: List<String> = emptyList()
    private var songPhraseIndex: Int = 0
    private val singingPitchSteps = floatArrayOf(0.92f, 1.0f, 1.08f, 1.04f, 0.96f, 1.02f)

    private val RECORD_AUDIO_PERMISSION_CODE = 100

    private var llmInference: LlmInference? = null
    private var modelLoadInProgress = false
    private val executor = Executors.newSingleThreadExecutor()
    private val modelDir by lazy { File(filesDir, "models").apply { mkdirs() } }
    private val modelFile by lazy { File(modelDir, "model.task") }
    /** Gemma2 .task in subfolder: model.task or full name Gemma2-2B-IT_multi-prefill-seq_q8_ekv1280.task */
    private val gemma2SubdirTaskFile by lazy { File(File(modelDir, "gemma2"), "model.task") }
    private val gemma2SubdirTaskFileFullName by lazy { File(File(modelDir, "gemma2"), "Gemma2-2B-IT_multi-prefill-seq_q8_ekv1280.task") }
    /** Gemma2 TFLite in assets (e.g. model/gemma2/Gemma2-2B-IT_multi-prefill-seq_q8_ekv1280.tflite) */
    private val gemma2AssetPath = "model/gemma2/Gemma2-2B-IT_multi-prefill-seq_q8_ekv1280.tflite"
    private val gemma2ModelFile by lazy { File(modelDir, "Gemma2-2B-IT_multi-prefill-seq_q8_ekv1280.tflite") }
    /** Gemma2 .tflite in subfolder (standalone .tflite not supported by MediaPipe; use .task instead) */
    private val gemma2SubdirModelFile by lazy { File(File(modelDir, "gemma2"), "Gemma2-2B-IT_multi-prefill-seq_q8_ekv1280.tflite") }

    private data class VoiceOption(val voice: Voice, val displayLabel: String)

    private val musicApiClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    private val musicApiBaseUrls = listOf("https://api.musicapi.ai", "https://api.aimusicapi.ai")
    private val prefsMusicApiKey = "musicapi_bearer_key"
    private val prefsMusicApiGender = "musicapi_voice_gender"
    private val prefsMusicApiAge = "musicapi_voice_age"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        checkPermissions()
        setupTextToSpeech()
        setupVoiceInput()
        setupTextInput()
        setupVoiceControls()
        setupLyricGeneration()
        setupMusicApi()
        tryLoadLlmModel()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) {
            startActivity(android.content.Intent(this, SettingsActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun initializeViews() {
        etTextInput = findViewById(R.id.etTextInput)
        etLyricPrompt = findViewById(R.id.etLyricPrompt)
        btnVoiceInput = findViewById(R.id.btnVoiceInput)
        btnConvertToSong = findViewById(R.id.btnConvertToSong)
        btnGenerateLyrics = findViewById(R.id.btnGenerateLyrics)
        tvVoiceTranscription = findViewById(R.id.tvVoiceTranscription)
        tvLyricStatus = findViewById(R.id.tvLyricStatus)
        tvLyricSource = findViewById(R.id.tvLyricSource)
        progressLyrics = findViewById(R.id.progressLyrics)
        spinnerVoice = findViewById(R.id.spinnerVoice)
        sbPitch = findViewById(R.id.sbPitch)
        sbSpeed = findViewById(R.id.sbSpeed)
        tvPitchValue = findViewById(R.id.tvPitchValue)
        tvSpeedValue = findViewById(R.id.tvSpeedValue)
        btnCreateSongMusicApi = findViewById(R.id.btnCreateSongMusicApi)
        btnPlayMusicApi = findViewById(R.id.btnPlayMusicApi)
        btnStopMusicApi = findViewById(R.id.btnStopMusicApi)
        btnDownloadMusicApi = findViewById(R.id.btnDownloadMusicApi)
        btnMyDownloadsMusicApi = findViewById(R.id.btnMyDownloadsMusicApi)
        progressMusicApi = findViewById(R.id.progressMusicApi)
        setupEditTextScrolling(etLyricPrompt)
        setupEditTextScrolling(etTextInput)
    }

    /** Stops the parent ScrollView from stealing vertical drags so the EditText scrolls its content. */
    private fun setupEditTextScrolling(editText: EditText) {
        editText.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                editText.parent?.requestDisallowInterceptTouchEvent(true)
            }
            false
        }
    }

    private fun tryLoadLlmModel() {
        copyModelFromAssetsIfPresent()
        // MediaPipe requires .task bundle (model + tokenizer), not raw .tflite
        val pathToLoad = when {
            gemma2SubdirTaskFileFullName.exists() -> gemma2SubdirTaskFileFullName.absolutePath
            gemma2SubdirTaskFile.exists() -> gemma2SubdirTaskFile.absolutePath
            modelFile.exists() -> modelFile.absolutePath
            else -> null
        }
        if (pathToLoad != null) {
            modelLoadInProgress = true
            tvLyricStatus.text = "Loading model…"
            tvLyricStatus.visibility = TextView.VISIBLE
            executor.execute {
                try {
                    val options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(pathToLoad)
                        .setMaxTokens(512)
                        .build()
                    llmInference = LlmInference.createFromOptions(this, options)
                    runOnUiThread {
                        modelLoadInProgress = false
                        tvLyricStatus.text = getString(R.string.model_ready)
                        tvLyricStatus.visibility = TextView.VISIBLE
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SongMaker", "LLM load failed", e)
                    runOnUiThread {
                        modelLoadInProgress = false
                        val detail = e.message?.take(80) ?: ""
                        tvLyricStatus.text = getString(R.string.model_load_failed) + if (detail.isNotEmpty()) " ($detail)" else ""
                        tvLyricStatus.visibility = TextView.VISIBLE
                    }
                }
            }
        } else {
            tvLyricStatus.text = "Copy model.task to: $modelDir/gemma2/model.task (or $modelDir/model.task). Get .task from Hugging Face: litert-community/Gemma2-2B-IT"
            tvLyricStatus.visibility = TextView.VISIBLE
        }
    }

    private fun copyModelFromAssetsIfPresent() {
        // Prefer Gemma2 TFLite from assets/model/gemma2/
        if (!gemma2ModelFile.exists()) {
            try {
                assets.open(gemma2AssetPath).use { input ->
                    FileOutputStream(gemma2ModelFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (_: Exception) {
                // Not in assets
            }
        }
        // Fallback: model.task in assets root
        if (!modelFile.exists()) {
            try {
                assets.open("model.task").use { input ->
                    FileOutputStream(modelFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (_: Exception) {
                // No model in assets
            }
        }
    }

    private fun setupLyricGeneration() {
        btnGenerateLyrics.setOnClickListener { generateLyrics() }
    }

    private fun setupMusicApi() {
        btnCreateSongMusicApi.setOnClickListener { createSongWithMusicApi() }
        btnPlayMusicApi.setOnClickListener {
            lastMusicApiAudioUrl?.let { url -> playMusicApiAudio(url) }
                ?: Toast.makeText(this, "Create a song first", Toast.LENGTH_SHORT).show()
        }
        btnStopMusicApi.setOnClickListener { stopMusicApiPlayback() }
        btnDownloadMusicApi.setOnClickListener { downloadMusicApiSong() }
        btnMyDownloadsMusicApi.setOnClickListener { showMyDownloadsAndPlay() }
    }

    /** Plays a short "done" alert tone (e.g. when lyrics or MusicAPI song is ready). */
    private fun playDoneTone() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 200)
            Handler(Looper.getMainLooper()).postDelayed({ toneGen.release() }, 250)
        } catch (_: Exception) { /* ignore if tone fails */ }
    }

    private fun stopMusicApiPlayback() {
        musicApiMediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        musicApiMediaPlayer = null
        Toast.makeText(this, getString(R.string.stopped), Toast.LENGTH_SHORT).show()
    }

    /** Builds MusicAPI sound prompt from saved vocal gender and age (e.g. "pop, male adult vocal"). */
    private fun getMusicApiSoundPrompt(): String {
        val prefs = getSharedPreferences("SongMaker", MODE_PRIVATE)
        val gender = if (prefs.getInt(prefsMusicApiGender, 0) == 0) "male" else "female"
        val age = if (prefs.getInt(prefsMusicApiAge, 0) == 0) "adult" else "child"
        return "pop, $gender $age vocal"
    }

    private fun createSongWithMusicApi() {
        val lyrics = etTextInput.text.toString().trim()
        if (lyrics.length < 30) {
            Toast.makeText(this, "Enter lyrics in the text box above (at least a few lines)", Toast.LENGTH_LONG).show()
            return
        }
        var rawKey = getSharedPreferences("SongMaker", MODE_PRIVATE).getString(prefsMusicApiKey, null)
            ?.replace(Regex("\\s"), "")?.trim() ?: ""
        if (rawKey.isEmpty()) {
            Toast.makeText(this, "Set your MusicAPI key in Settings (⋮)", Toast.LENGTH_LONG).show()
            return
        }
        if (rawKey.startsWith("Bearer", ignoreCase = true)) rawKey = rawKey.drop(6).replace(Regex("\\s"), "").trim()
        val authHeader = "Bearer $rawKey"

        progressMusicApi.visibility = ProgressBar.VISIBLE
        btnCreateSongMusicApi.isEnabled = false
        Toast.makeText(this, getString(R.string.musicapi_generating), Toast.LENGTH_SHORT).show()

        val title = lyrics.lines().firstOrNull()?.take(80)?.trim()?.ifEmpty { "My Song" } ?: "My Song"
        val soundPrompt = getMusicApiSoundPrompt()
        val body = JSONObject().apply {
            put("task_type", "create_music")
            put("mv", "FUZZ-2.0")
            put("lyrics", lyrics)
            put("sound", soundPrompt)
            put("title", title)
            put("make_instrumental", false)
        }.toString()

        executor.execute {
            try {
                var baseUrl: String? = null
                var createJson = ""
                val jsonBody = body.toRequestBody("application/json".toMediaType())
                // Try each base URL; for each, try Bearer then api-key header (some dashboards use different auth)
                for (base in musicApiBaseUrls) {
                    for (authVariant in 0..2) {
                        val createReqBuilder = Request.Builder()
                            .url("$base/api/v1/producer/create")
                            .addHeader("Content-Type", "application/json")
                            .addHeader("User-Agent", "SongMaker/1.0 (Android)")
                            .post(jsonBody)
                        when (authVariant) {
                            0 -> createReqBuilder.addHeader("Authorization", authHeader)
                            1 -> createReqBuilder.addHeader("Authorization", rawKey)
                            2 -> createReqBuilder.addHeader("api-key", rawKey).addHeader("Authorization", authHeader)
                        }
                        val createReq = createReqBuilder.build()
                        val createRes = musicApiClient.newCall(createReq).execute()
                        createJson = createRes.body?.string() ?: throw Exception("Empty response")
                        if (createRes.isSuccessful) {
                            baseUrl = base
                            break
                        }
                        if (createRes.code != 401) {
                            val errObj = try { JSONObject(createJson) } catch (_: Exception) { null }
                            val msg = errObj?.optString("message")?.takeIf { it.isNotEmpty() }
                                ?: errObj?.optString("error")?.takeIf { it.isNotEmpty() }
                                ?: createJson.take(300)
                            if (createRes.code == 403) {
                                throw Exception("403 Forbidden. Your plan may not include Producer API or credits may be used up. Check musicapi.ai dashboard. $msg")
                            }
                            throw Exception("HTTP ${createRes.code}: $msg")
                        }
                    }
                    if (baseUrl != null) break
                }
                if (baseUrl == null) {
                    val err = try { JSONObject(createJson).optString("error", createJson) } catch (_: Exception) { createJson }
                    throw Exception("API key incorrect. Regenerate key at musicapi.ai/dashboard/apikey or contact MusicAPI support (e.g. Telegram). $err")
                }
                val createObj = JSONObject(createJson)
                val dataObj = createObj.optJSONObject("data")
                val taskId = listOfNotNull(
                    createObj.optString("task_id").takeIf { it.isNotEmpty() },
                    createObj.optString("id").takeIf { it.isNotEmpty() },
                    createObj.optString("taskId").takeIf { it.isNotEmpty() },
                    dataObj?.optString("task_id")?.takeIf { it.isNotEmpty() },
                    dataObj?.optString("id")?.takeIf { it.isNotEmpty() },
                    dataObj?.optString("taskId")?.takeIf { it.isNotEmpty() },
                    createObj.optString("data").takeIf { it.isNotEmpty() }
                ).firstOrNull() ?: throw Exception("No task_id in response. Response: ${createJson.take(400)}")
                var audioUrl: String? = null
                var attempts = 0
                val maxAttempts = 200
                while (attempts < maxAttempts) {
                    Thread.sleep(3000)
                    attempts++
                    val taskReq = Request.Builder()
                        .url("$baseUrl/api/v1/producer/task/$taskId")
                        .addHeader("Authorization", authHeader)
                        .addHeader("User-Agent", "SongMaker/1.0 (Android)")
                        .get()
                        .build()
                    val taskRes = musicApiClient.newCall(taskReq).execute()
                    val taskBody = taskRes.body?.string() ?: continue
                    val taskObj = JSONObject(taskBody)
                    // API returns { "code": 200, "data": [ { "state": "succeeded", "audio_url": "..." } ] } - data is the array
                    val tasks = taskObj.optJSONArray("data")
                        ?: taskObj.optJSONObject("data")?.optJSONArray("tasks")
                        ?: taskObj.optJSONObject("data")?.optJSONArray("task")
                    val task = (0 until (tasks?.length() ?: 0)).firstOrNull()?.let { tasks!!.getJSONObject(it) }
                    val state = task?.optString("state", "") ?: taskObj.optString("state", "")
                    if (state == "succeeded" || state == "completed") {
                        audioUrl = task?.optString("audio_url") ?: task?.optString("audio")
                        if (!audioUrl.isNullOrEmpty()) break
                    } else if (state == "failed") {
                        throw Exception(task?.optString("message", "Generation failed") ?: "Generation failed")
                    }
                }
                val urlToPlay = audioUrl ?: throw Exception("No audio URL after waiting (task may still be processing). Try again in a minute.")
                runOnUiThread {
                    progressMusicApi.visibility = ProgressBar.GONE
                    btnCreateSongMusicApi.isEnabled = true
                    playMusicApiAudio(urlToPlay)
                    playDoneTone()
                }
            } catch (e: Exception) {
                android.util.Log.e("SongMaker", "MusicAPI error", e)
                runOnUiThread {
                    progressMusicApi.visibility = ProgressBar.GONE
                    btnCreateSongMusicApi.isEnabled = true
                    Toast.makeText(this, getString(R.string.musicapi_error) + ": " + (e.message ?: "Unknown"), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun playMusicApiAudio(url: String) {
        stopMusicApiPlayback()
        lastMusicApiAudioUrl = url
        try {
            musicApiMediaPlayer = android.media.MediaPlayer().apply {
                setDataSource(url)
                prepareAsync()
                setOnPreparedListener { start() }
                setOnCompletionListener {
                    musicApiMediaPlayer?.release()
                    musicApiMediaPlayer = null
                }
                setOnErrorListener { _, _, _ ->
                    musicApiMediaPlayer = null
                    true.also { release() }
                }
            }
            Toast.makeText(this, getString(R.string.playing), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            musicApiMediaPlayer = null
            Toast.makeText(this, "Playback failed: " + e.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun downloadMusicApiSong() {
        val url = lastMusicApiAudioUrl
        if (url.isNullOrEmpty()) {
            Toast.makeText(this, "Create a song first", Toast.LENGTH_SHORT).show()
            return
        }
        val editText = EditText(this).apply {
            hint = getString(R.string.musicapi_save_song_hint)
            setPadding(
                (48 * resources.displayMetrics.density).toInt(),
                (24 * resources.displayMetrics.density).toInt(),
                (48 * resources.displayMetrics.density).toInt(),
                (24 * resources.displayMetrics.density).toInt()
            )
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.musicapi_save_song_title))
            .setView(editText)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = editText.text.toString().trim()
                val baseName = if (name.isEmpty()) null else sanitizeFileName(name)
                performDownloadWithFileName(url, baseName)
            }
            .setNeutralButton(getString(R.string.musicapi_use_default_name)) { _, _ ->
                performDownloadWithFileName(url, null)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "song" }
    }

    private fun performDownloadWithFileName(url: String, fileNameBase: String?) {
        btnDownloadMusicApi.isEnabled = false
        Toast.makeText(this, getString(R.string.musicapi_downloading), Toast.LENGTH_SHORT).show()
        executor.execute {
            var errorMsg: String? = null
            try {
                val request = Request.Builder().url(url).get().build()
                val response = musicApiClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    errorMsg = "HTTP ${response.code}"
                } else {
                    val body = response.body ?: run { errorMsg = "Empty response"; null }
                    if (body != null) {
                        val ext = when {
                            url.contains(".mp3", ignoreCase = true) -> "mp3"
                            url.contains(".m4a", ignoreCase = true) -> "m4a"
                            else -> "mp3"
                        }
                        val base = fileNameBase ?: "song_${System.currentTimeMillis()}"
                        val file = File(downloadedSongsDir, "$base.$ext")
                        body.byteStream().use { input ->
                            FileOutputStream(file).use { output ->
                                input.copyTo(output)
                            }
                        }
                        runOnUiThread {
                            btnDownloadMusicApi.isEnabled = true
                            Toast.makeText(this, getString(R.string.musicapi_downloaded), Toast.LENGTH_SHORT).show()
                        }
                        return@execute
                    }
                }
            } catch (e: Exception) {
                errorMsg = e.message ?: "Unknown error"
                android.util.Log.e("SongMaker", "Download failed", e)
            }
            runOnUiThread {
                btnDownloadMusicApi.isEnabled = true
                Toast.makeText(this, getString(R.string.musicapi_download_failed) + ": " + (errorMsg ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showMyDownloadsAndPlay() {
        val files = downloadedSongsDir.listFiles()?.filter { it.isFile && it.length() > 0 }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
        if (files.isEmpty()) {
            Toast.makeText(this, getString(R.string.musicapi_no_downloads), Toast.LENGTH_LONG).show()
            return
        }
        val labels = files.map { f ->
            val date = java.text.SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(java.util.Date(f.lastModified()))
            "${f.nameWithoutExtension} ($date)"
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.musicapi_my_downloads))
            .setItems(labels.toTypedArray()) { _, which ->
                playLocalFile(files[which])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun playLocalFile(file: File) {
        stopMusicApiPlayback()
        try {
            musicApiMediaPlayer = android.media.MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepareAsync()
                setOnPreparedListener { start() }
                setOnCompletionListener {
                    musicApiMediaPlayer?.release()
                    musicApiMediaPlayer = null
                }
                setOnErrorListener { _, _, _ ->
                    musicApiMediaPlayer = null
                    true.also { release() }
                }
            }
            Toast.makeText(this, getString(R.string.playing), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            musicApiMediaPlayer = null
            Toast.makeText(this, "Playback failed: " + e.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateLyrics() {
        val prompt = etLyricPrompt.text.toString().trim()
        if (prompt.isEmpty()) {
            Toast.makeText(this, "Enter a prompt (e.g. a song about summer)", Toast.LENGTH_SHORT).show()
            return
        }
        if (llmInference == null) {
            if (modelLoadInProgress) {
                Toast.makeText(this, "Model still loading, please wait…", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Need .task file (not .tflite). See status text.", Toast.LENGTH_LONG).show()
            }
            return
        }
        progressLyrics.visibility = ProgressBar.VISIBLE
        btnGenerateLyrics.isEnabled = false
        tvLyricStatus.text = getString(R.string.lyrics_generating)
        tvLyricStatus.visibility = TextView.VISIBLE
        tvLyricSource.visibility = TextView.GONE

        executor.execute {
            try {
                // First ask LLM for full lyrics if it knows them (e.g. for a known song)
                val lookupPrompt = "Consider this request: \"$prompt\"\n\n" +
                    "If this refers to a known song and you have the full lyrics, output ONLY the complete lyrics (no title, no explanation).\n" +
                    "If this is not a specific known song or you don't have the full lyrics, output exactly this line and nothing else: NO_LYRICS"
                val lookupResult = llmInference!!.generateResponse(lookupPrompt)
                val trimmed = lookupResult.trim()

                val isFromExistingLyrics = !(trimmed.uppercase().startsWith("NO_LYRICS") && trimmed.length < 200)
                val lyrics = if (isFromExistingLyrics) {
                    lookupResult
                } else {
                    // LLM doesn't have full lyrics — generate new ones
                    val createPrompt = "Write short song lyrics (2 to 4 verses, simple rhyme) based on this idea. Output only the lyrics, no title or explanation.\n\nIdea: $prompt"
                    llmInference!!.generateResponse(createPrompt)
                }

                runOnUiThread {
                    progressLyrics.visibility = ProgressBar.GONE
                    btnGenerateLyrics.isEnabled = true
                    tvLyricStatus.visibility = TextView.GONE
                    tvLyricSource.text = if (isFromExistingLyrics) getString(R.string.lyrics_source_existing) else getString(R.string.lyrics_source_new)
                    tvLyricSource.visibility = TextView.VISIBLE
                    etTextInput.setText(lyrics)
                    playDoneTone()
                }
            } catch (e: Exception) {
                android.util.Log.e("SongMaker", "Lyric generation failed", e)
                runOnUiThread {
                    progressLyrics.visibility = ProgressBar.GONE
                    btnGenerateLyrics.isEnabled = true
                    tvLyricStatus.text = getString(R.string.lyrics_error) + " " + e.message
                }
            }
        }
    }

    private fun setupTextToSpeech() {
        // Prefer Google TTS engine for more voices (including female); fallback to default
        textToSpeech = TextToSpeech(this, { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.getDefault())
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(this, "Language not supported", Toast.LENGTH_SHORT).show()
                } else {
                    setupVoiceSelection()
                    updatePitchAndSpeed()
                }
            } else {
                // Google TTS not available, fallback to default engine
                textToSpeech = TextToSpeech(this) { fallbackStatus ->
                    if (fallbackStatus == TextToSpeech.SUCCESS) {
                        textToSpeech?.setLanguage(Locale.getDefault())
                        attachUtteranceListener()
                        setupVoiceSelection()
                        updatePitchAndSpeed()
                    } else {
                        Toast.makeText(this, "TextToSpeech initialization failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }, "com.google.android.tts")

        attachUtteranceListener()
    }

    private fun attachUtteranceListener() {
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                runOnUiThread {
                    if (utteranceId?.startsWith("phrase_") != true) isPlaying = true
                    updateConvertButton()
                }
            }

            override fun onDone(utteranceId: String?) {
                runOnUiThread {
                    if (utteranceId?.startsWith("phrase_") == true) {
                        playNextSingingPhrase() // advances index and plays next or sets isPlaying = false
                    } else {
                        isPlaying = false
                        updateConvertButton()
                    }
                }
            }

            override fun onError(utteranceId: String?) {
                runOnUiThread {
                    isPlaying = false
                    songPhrases = emptyList()
                    updateConvertButton()
                    Toast.makeText(this@MainActivity, "Error playing audio", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun isFemaleVoice(voice: Voice): Boolean {
        val name = voice.name.lowercase()
        if (name.contains("female") || name.contains("woman") || name.contains("girl")) return true
        if (name.contains("male") && !name.contains("female")) return false
        // Google / common engine patterns for female
        if (name.contains("mtf") || name.contains("x-f") || name.contains("-f-") || name.contains("f-")) return true
        if (name.contains("samantha") || name.contains("kate") || name.contains("victoria") || name.contains("linda")) return true
        if (name.contains("en-us-x-iof") || name.contains("en-gb-x-fis") || name.contains("en-us-x-sfh") || name.contains("en-us-x-mtf")) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && voice.features.contains("female")) return true
        return false
    }

    private fun setupVoiceSelection() {
        textToSpeech?.let { tts ->
            val voices = tts.voices
            if (!voices.isNullOrEmpty()) {
                val defaultLocale = Locale.getDefault()
                val maleVoices = voices.filter { !isFemaleVoice(it) }
                val femaleVoices = voices.filter { isFemaleVoice(it) }
                // Prefer a voice that matches the default locale
                fun preferLocale(voiceList: List<Voice>): Voice? {
                    val match = voiceList.firstOrNull { it.locale?.language == defaultLocale.language }
                    return match ?: voiceList.firstOrNull()
                }
                val maleVoice = preferLocale(maleVoices)
                val femaleVoice = preferLocale(femaleVoices)
                voiceOptions = buildList {
                    maleVoice?.let { add(VoiceOption(it, getString(R.string.male_voice))) }
                    femaleVoice?.let { add(VoiceOption(it, getString(R.string.female_voice))) }
                }
                if (voiceOptions.isNotEmpty()) {
                    val labels = voiceOptions.map { it.displayLabel }
                    val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels).apply {
                        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    }
                    spinnerVoice.adapter = adapter
                    spinnerVoice.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                            val option = voiceOptions.getOrNull(position)
                            selectedVoice = option?.voice
                            selectedVoiceIsFemale = option?.let { isFemaleVoice(it.voice) } ?: false
                            applyVoiceSelection()
                        }
                        override fun onNothingSelected(parent: AdapterView<*>?) {}
                    }
                    selectedVoice = voiceOptions.firstOrNull()?.voice
                    selectedVoiceIsFemale = voiceOptions.firstOrNull()?.let { isFemaleVoice(it.voice) } ?: false
                    applyVoiceSelection()
                } else {
                    android.util.Log.w("SongMaker", "No male/female voices found. Download voices in Settings > Accessibility > Text-to-speech")
                }
            } else {
                android.util.Log.w("SongMaker", "No voices available. Download voices in Settings > Accessibility > Text-to-speech")
            }
        }
    }

    private fun applyVoiceSelection() {
        textToSpeech?.let { tts ->
            selectedVoice?.let { voice ->
                tts.voice = voice
                android.util.Log.d("SongMaker", "Selected voice: ${voice.name}")
            }
        }
        updatePitchAndSpeed()
    }

    private fun setupVoiceControls() {
        sbPitch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val pitch = progress / 100f
                tvPitchValue.text = String.format("%.2f", pitch)
                if (fromUser) {
                    updatePitchAndSpeed()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        sbSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = progress / 100f
                tvSpeedValue.text = String.format("%.2f", speed)
                if (fromUser) {
                    updatePitchAndSpeed()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updatePitchAndSpeed() {
        textToSpeech?.let { tts ->
            val pitch = sbPitch.progress / 100f
            val speed = sbSpeed.progress / 100f
            
            // Adjust pitch based on voice type for more natural sound
            val adjustedPitch = if (selectedVoiceIsFemale) pitch * 1.15f else pitch * 0.85f
            
            tts.setPitch(adjustedPitch.coerceIn(0.5f, 2.0f))
            tts.setSpeechRate(speed.coerceIn(0.5f, 2.0f))
        }
    }

    private fun setupTextInput() {
        btnConvertToSong.setOnClickListener {
            if (isPlaying) {
                stopSong()
            } else {
                convertToSong()
            }
        }
    }

    private fun convertToSong() {
        val text = etTextInput.text.toString().trim()
        if (text.isEmpty()) {
            // Try to get text from voice transcription
            val voiceText = tvVoiceTranscription.text.toString().trim()
            if (voiceText.isNotEmpty() && voiceText != getString(R.string.not_recording)) {
                etTextInput.setText(voiceText)
                playSong(voiceText)
            } else {
                Toast.makeText(this, "Please enter text or use voice input first", Toast.LENGTH_SHORT).show()
            }
        } else {
            playSong(text)
        }
    }

    private fun playSong(text: String) {
        if (textToSpeech == null) {
            Toast.makeText(this, "TextToSpeech not initialized", Toast.LENGTH_SHORT).show()
            return
        }

        applyVoiceSelection()

        // Split into phrases for singing tone (each phrase gets a different pitch)
        songPhrases = splitIntoPhrases(text)
        if (songPhrases.isEmpty()) {
            songPhrases = listOf(text.trim()).filter { it.isNotEmpty() }
        }
        if (songPhrases.isEmpty()) return

        songPhraseIndex = 0
        isPlaying = true
        updateConvertButton()
        playNextSingingPhrase()
    }

    /** Splits text into short phrases for phrase-by-phrase pitch variation (singing effect). */
    private fun splitIntoPhrases(text: String): List<String> {
        val trimmed = text.replace(Regex("\\s+"), " ").trim()
        if (trimmed.isEmpty()) return emptyList()
        // Split on sentence boundaries and commas so each phrase is a short chunk
        val sentences = trimmed.split(Regex("(?<=[.!?])\\s+"))
        return sentences.flatMap { sentence ->
            sentence.split(Regex("\\s*,\\s*")).map { it.trim() }.filter { it.isNotEmpty() }
        }.filter { it.isNotEmpty() }
    }

    /** Plays the next phrase in the song with the next pitch step; called from UtteranceProgressListener. */
    private fun playNextSingingPhrase() {
        if (songPhrases.isEmpty()) {
            isPlaying = false
            updateConvertButton()
            return
        }
        val tts = textToSpeech ?: return
        val index = songPhraseIndex
        if (index >= songPhrases.size) {
            isPlaying = false
            updateConvertButton()
            return
        }
        val phrase = songPhrases[index]
        val pitchStep = singingPitchSteps[index % singingPitchSteps.size]
        val basePitch = sbPitch.progress / 100f
        val speed = sbSpeed.progress / 100f
        val adjustedPitch = (if (selectedVoiceIsFemale) basePitch * 1.15f else basePitch * 0.85f) * pitchStep
        tts.setPitch(adjustedPitch.coerceIn(0.5f, 2.0f))
        tts.setSpeechRate(speed.coerceIn(0.5f, 2.0f))

        val utteranceId = "phrase_$index"
        val result = tts.speak(phrase, if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, utteranceId)

        if (result == TextToSpeech.ERROR) {
            isPlaying = false
            songPhrases = emptyList()
            updateConvertButton()
            Toast.makeText(this, "Error playing audio", Toast.LENGTH_SHORT).show()
            return
        }
        songPhraseIndex = index + 1
        if (songPhraseIndex >= songPhrases.size) {
            // Last phrase; listener will set isPlaying = false when this utterance completes
        }
        updateConvertButton()
    }

    private fun stopSong() {
        textToSpeech?.stop()
        songPhrases = emptyList()
        isPlaying = false
        updateConvertButton()
    }

    private fun updateConvertButton() {
        btnConvertToSong.text = if (isPlaying) {
            getString(R.string.stop_song)
        } else {
            getString(R.string.convert_to_song)
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_PERMISSION_CODE
            )
        }
    }

    private fun setupVoiceInput() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    runOnUiThread { tvVoiceTranscription.text = "Listening..." }
                }

                override fun onBeginningOfSpeech() {
                    runOnUiThread { tvVoiceTranscription.text = "Listening..." }
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    runOnUiThread { tvVoiceTranscription.text = "Processing..." }
                }

                override fun onError(error: Int) {
                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                        else -> "Unknown error"
                    }
                    runOnUiThread {
                        tvVoiceTranscription.text = "Error: $errorMessage"
                        isVoiceInputActive = false
                        updateVoiceButton()
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val transcribedText = matches[0]
                        runOnUiThread {
                            tvVoiceTranscription.text = transcribedText
                            etTextInput.setText(transcribedText)
                        }
                    }
                    runOnUiThread {
                        isVoiceInputActive = false
                        updateVoiceButton()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        runOnUiThread {
                            tvVoiceTranscription.text = text
                            etTextInput.setText(text)
                        }
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            btnVoiceInput.setOnClickListener {
                if (isVoiceInputActive) {
                    stopVoiceInput()
                } else {
                    startVoiceInput()
                }
            }
        } else {
            btnVoiceInput.isEnabled = false
            tvVoiceTranscription.text = "Speech recognition not available on this device"
        }
    }

    private fun startVoiceInput() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_SHORT).show()
            checkPermissions()
            return
        }

        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Record as long as you're talking: long silence before stopping (only stop after ~20s silence)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 20_000)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 15_000)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 0)
            }
        }

        speechRecognizer?.startListening(intent)
        isVoiceInputActive = true
        updateVoiceButton()
    }

    private fun stopVoiceInput() {
        speechRecognizer?.stopListening()
        isVoiceInputActive = false
        updateVoiceButton()
    }

    private fun updateVoiceButton() {
        btnVoiceInput.text = if (isVoiceInputActive) {
            getString(R.string.stop_voice_input)
        } else {
            getString(R.string.start_voice_input)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Microphone permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        musicApiMediaPlayer?.release()
        musicApiMediaPlayer = null
        executor.shutdown()
        llmInference?.close()
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }
}
