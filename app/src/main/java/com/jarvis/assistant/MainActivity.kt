package com.jarvis.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var languageCode by mutableStateOf("en-IN")
    private var status by mutableStateOf("IDLE")
    private var transcript by mutableStateOf("")
    private var reply by mutableStateOf("")

    private val microphonePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening() else reply = "Microphone permission is needed for voice input."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { speech ->
                speech.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { status = "LISTENING" }
                    override fun onBeginningOfSpeech() { status = "LISTENING" }
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() { status = "THINKING" }
                    override fun onError(error: Int) {
                        status = "IDLE"
                        reply = "I couldn't recognize that. Please try again or type your request."
                    }
                    override fun onResults(results: Bundle?) {
                        transcript = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull().orEmpty()
                        status = "IDLE"
                        handleCommand(transcript)
                    }
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
            }
        } else {
            reply = "Speech recognition is unavailable on this device. You can still use text input."
        }

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF050B16)) {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("J.A.R.V.I.S.", color = Color(0xFF00E5FF), style = MaterialTheme.typography.headlineMedium)
                            Text("Personal AI Voice Assistant", color = Color.LightGray)
                            Spacer(Modifier.height(16.dp))
                            Text("STATUS: $status", color = Color(0xFF80DEEA))
                        }

                        Button(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO)
                                    == PackageManager.PERMISSION_GRANTED) startListening()
                                else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            modifier = Modifier.size(150.dp),
                            shape = CircleShape
                        ) { Text("TAP\nTO SPEAK") }

                        Column {
                            Text("You: $transcript", color = Color.White)
                            Spacer(Modifier.height(8.dp))
                            Text("JARVIS: $reply", color = Color(0xFF00E5FF))
                            Spacer(Modifier.height(12.dp))
                            var typed by remember { mutableStateOf("") }
                            OutlinedTextField(
                                value = typed,
                                onValueChange = { typed = it },
                                label = { Text("Type a command", color = Color.LightGray) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row {
                                TextButton(onClick = {
                                    languageCode = if (languageCode == "en-IN") "te-IN" else "en-IN"
                                    reply = if (languageCode == "te-IN") "Language set to Telugu." else "Language set to English."
                                }) { Text(if (languageCode == "te-IN") "తెలుగు" else "English") }
                                Spacer(Modifier.weight(1f))
                                Button(onClick = { transcript = typed; handleCommand(typed); typed = "" }) {
                                    Text("Send")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startListening() {
        val speech = recognizer ?: run {
            reply = "Speech recognition is unavailable."
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try { speech.startListening(intent) } catch (_: Exception) {
            status = "IDLE"
            reply = "Couldn't start voice input. Check microphone access and try again."
        }
    }

    private fun handleCommand(raw: String) {
        val command = raw.trim()
        if (command.isBlank()) return
        val lower = command.lowercase(Locale.ROOT)
        val result = when {
            lower.contains("open youtube") -> openApp("com.google.android.youtube", "https://www.youtube.com", "YouTube")
            lower.contains("open whatsapp") -> openApp("com.whatsapp", "https://www.whatsapp.com", "WhatsApp")
            lower.contains("open instagram") -> openApp("com.instagram.android", "https://www.instagram.com", "Instagram")
            lower.startsWith("search youtube for ") -> {
                val q = command.substringAfter("search youtube for ").trim()
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(q)}")))
                "Searching YouTube for $q."
            }
            lower.startsWith("search the web for ") -> {
                val q = command.substringAfter("search the web for ").trim()
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(q)}")))
                "Searching the web for $q."
            }
            lower.contains("what time is it") -> "The current time is ${java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(java.util.Date())}."
            lower.contains("stop speaking") -> { tts?.stop(); "Stopped speaking." }
            else -> "I heard: $command. AI answers need a secure backend, which is not configured in this starter."
        }
        reply = result
        tts?.speak(result, TextToSpeech.QUEUE_FLUSH, null, "jarvis-reply")
    }

    private fun openApp(packageName: String, webUrl: String, label: String): String {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        return if (launch != null) {
            startActivity(launch)
            "Opening $label."
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)))
            "$label app not found; opening its website."
        }
    }

    override fun onInit(statusCode: Int) {
        if (statusCode == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.speak(
                "Hello, Boss. I am your Personal AI Assistant. How can I help you today?",
                TextToSpeech.QUEUE_FLUSH, null, "jarvis-greeting"
            )
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
