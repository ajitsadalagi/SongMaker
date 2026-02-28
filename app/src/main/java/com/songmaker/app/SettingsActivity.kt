package com.songmaker.app

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private val prefsName = "SongMaker"
    private val prefsMusicApiKey = "musicapi_bearer_key"
    private val prefsMusicApiGender = "musicapi_voice_gender"
    private val prefsMusicApiAge = "musicapi_voice_age"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings)

        val etMusicApiKey = findViewById<EditText>(R.id.etMusicApiKey)
        val spinnerGender = findViewById<Spinner>(R.id.spinnerMusicApiGender)
        val spinnerAge = findViewById<Spinner>(R.id.spinnerMusicApiAge)

        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        prefs.getString(prefsMusicApiKey, null)?.let { if (it.isNotEmpty()) etMusicApiKey.setText(it) }

        val genderOptions = arrayOf(getString(R.string.musicapi_voice_male), getString(R.string.musicapi_voice_female))
        val ageOptions = arrayOf(getString(R.string.musicapi_voice_adult), getString(R.string.musicapi_voice_kid))
        spinnerGender.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, genderOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerAge.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, ageOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerGender.setSelection(prefs.getInt(prefsMusicApiGender, 0).coerceIn(0, 1))
        spinnerAge.setSelection(prefs.getInt(prefsMusicApiAge, 0).coerceIn(0, 1))
    }

    override fun onPause() {
        super.onPause()
        saveSettings()
    }

    override fun onSupportNavigateUp(): Boolean {
        saveSettings()
        finish()
        return true
    }

    private fun saveSettings() {
        val etMusicApiKey = findViewById<EditText>(R.id.etMusicApiKey)
        val spinnerGender = findViewById<Spinner>(R.id.spinnerMusicApiGender)
        val spinnerAge = findViewById<Spinner>(R.id.spinnerMusicApiAge)
        var rawKey = etMusicApiKey.text.toString().replace(Regex("\\s"), "").trim()
        if (rawKey.startsWith("Bearer", ignoreCase = true)) rawKey = rawKey.drop(6).replace(Regex("\\s"), "").trim()
        getSharedPreferences(prefsName, MODE_PRIVATE).edit()
            .putString(prefsMusicApiKey, rawKey)
            .putInt(prefsMusicApiGender, spinnerGender.selectedItemPosition.coerceIn(0, 1))
            .putInt(prefsMusicApiAge, spinnerAge.selectedItemPosition.coerceIn(0, 1))
            .apply()
    }
}
