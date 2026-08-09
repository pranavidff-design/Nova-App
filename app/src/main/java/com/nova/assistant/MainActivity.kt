package com.nova.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nova.assistant.memory.MemoryManager
import kotlinx.coroutines.launch

/**
 * MainActivity — Part 2 adds memory: short-term conversation context,
 * long-term "remember that..." facts, and recall/forget commands —
 * all handled before a command falls through to the local AI.
 */
class MainActivity : AppCompatActivity(), RecognitionListener {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var statusLabel: TextView
    private lateinit var logText: TextView
    private lateinit var voiceEngine: NovaVoiceEngine
    private lateinit var brain: NovaBrain
    private lateinit var memory: MemoryManager
    private lateinit var localRouter: LocalCommandRouter
    private var isPaused = false
    private var wakeWordListener: WakeWordListener? = null
    private var wakeWordOn = false

    private val modelPickerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            statusLabel.text = "Copying model file… this can take a minute for a large file."
            brain.copyPickedModelFile(uri) { copiedOk ->
                if (copiedOk) {
                    brain.initialize { ready ->
                        runOnUiThread {
                            statusLabel.text = if (ready) getString(R.string.status_idle) else "Model copied but failed to load — try picking it again."
                        }
                    }
                } else {
                    runOnUiThread { statusLabel.text = "Couldn't copy that file — make sure it's the .task file." }
                }
            }
        }
    }

    private val micPermissionRequestCode = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusLabel = findViewById(R.id.statusLabel)
        logText = findViewById(R.id.logText)
        voiceEngine = NovaVoiceEngine(this)
        memory = MemoryManager(this)
        localRouter = LocalCommandRouter(this)
        brain = NovaBrain(this)
        statusLabel.text = "Loading Nova's local brain…"
        brain.initialize { success ->
            runOnUiThread {
                statusLabel.text = if (success) {
                    getString(R.string.status_idle)
                } else {
                    "Model not found — see README to download it."
                }
            }
        }

        val orb = findViewById<android.widget.FrameLayout>(R.id.orbContainer)
        orb.setOnClickListener { onOrbTapped() }

        val memoryButton = findViewById<android.widget.Button?>(R.id.memoryButton)
        memoryButton?.setOnClickListener {
            startActivity(android.content.Intent(this, MemoryActivity::class.java))
        }

        val pauseButton = findViewById<android.widget.Button?>(R.id.pauseButton)
        pauseButton?.setOnClickListener {
            isPaused = !isPaused
            pauseButton.text = if (isPaused) "Resume Nova" else "Pause Nova"
            statusLabel.text = if (isPaused) "Paused — tap Resume to continue" else getString(R.string.status_idle)
            if (isPaused) { wakeWordListener?.stop(); wakeWordOn = false; findViewById<android.widget.Button?>(R.id.wakeWordButton)?.text = "Wake word: OFF" }
        }

        val wakeWordButton = findViewById<android.widget.Button?>(R.id.wakeWordButton)
        wakeWordButton?.setOnClickListener {
            if (isPaused) { statusLabel.text = "Nova is paused — tap Resume first"; return@setOnClickListener }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), micPermissionRequestCode)
                return@setOnClickListener
            }
            wakeWordOn = !wakeWordOn
            wakeWordButton.text = if (wakeWordOn) "Wake word: ON" else "Wake word: OFF"
            if (wakeWordOn) {
                wakeWordListener = WakeWordListener(
                    context = this,
                    onWakeDetected = { runOnUiThread { statusLabel.text = "Yes? I'm listening…" } },
                    onCommandHeard = { command -> runOnUiThread { appendLog("You", command); handleCommand(command) } }
                )
                wakeWordListener?.start()
            } else {
                wakeWordListener?.stop()
                statusLabel.text = getString(R.string.status_idle)
            }
        }

        val loadModelButton = findViewById<android.widget.Button?>(R.id.loadModelButton)
        loadModelButton?.setOnClickListener {
            modelPickerLauncher.launch(arrayOf("*/*"))
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(this)
    }

    private fun onOrbTapped() {
        if (isPaused) {
            statusLabel.text = "Nova is paused — tap Resume first"
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                micPermissionRequestCode
            )
            return
        }
        startListening()
    }

    private fun startListening() {
        statusLabel.text = getString(R.string.status_listening)
        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
        }
        speechRecognizer.startListening(intent)
    }

    override fun onResults(results: Bundle) {
        val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val heard = matches?.firstOrNull() ?: return
        appendLog("You", heard)
        handleCommand(heard)
    }

    private fun handleCommand(text: String) {
        if (isPaused) return

        // Step 1: memory-specific commands, handled directly — never need the AI model for these.
        val rememberFact = memory.extractRememberCommand(text)
        if (rememberFact != null) {
            lifecycleScope.launch {
                memory.remember(rememberFact)
                val reply = if (memory.memoryEnabled) "Got it, I'll remember that." else "Memory is currently turned off, so I won't save that — you can re-enable it from the Memory screen."
                appendLog("Nova", reply)
                voiceEngine.speak(reply)
                memory.addTurn(text, reply)
            }
            return
        }
        if (memory.isRecallQuery(text)) {
            lifecycleScope.launch {
                val facts = memory.recallAll()
                val reply = if (facts.isEmpty()) "I don't have anything saved yet."
                    else "Here's what I remember: " + facts.joinToString(". ") { it.fact }
                appendLog("Nova", reply)
                voiceEngine.speak(reply)
                memory.addTurn(text, reply)
            }
            return
        }
        if (memory.isForgetAllCommand(text)) {
            lifecycleScope.launch {
                memory.clearAll()
                val reply = "Done — I've cleared everything I had saved."
                appendLog("Nova", reply)
                voiceEngine.speak(reply)
            }
            return
        }

        // Step 2: local, free, instant handling — with real approval dialogs for sensitive actions.
        when (val routed = localRouter.tryHandle(text)) {
            is LocalCommandRouter.RouteResult.Executed -> {
                appendLog("Nova", routed.message)
                voiceEngine.speak(routed.message)
                memory.addTurn(text, routed.message)
                statusLabel.text = getString(R.string.status_idle)
                return
            }
            is LocalCommandRouter.RouteResult.NeedsApproval -> {
                PermissionGate.request(this, routed.actionLabel) { approved ->
                    val reply = if (approved) routed.onApproved() else "Understood — I won't do that."
                    appendLog("Nova", reply)
                    voiceEngine.speak(reply)
                    memory.addTurn(text, reply)
                    statusLabel.text = getString(R.string.status_idle)
                }
                return
            }
            null -> { /* fall through to the AI model below */ }
        }

        // Step 3: needs reasoning -> local AI model, with short-term + long-term context injected.
        statusLabel.text = getString(R.string.status_thinking)
        lifecycleScope.launch {
            val contextBlock = listOf(memory.longTermContext(), memory.shortTermContext())
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
            brain.ask(text, contextBlock) { reply ->
                runOnUiThread {
                    appendLog("Nova", reply)
                    voiceEngine.speak(reply)
                    memory.addTurn(text, reply)
                    statusLabel.text = getString(R.string.status_idle)
                }
            }
        }
    }

    private fun appendLog(who: String, text: String) {
        logText.append("\n\n$who: $text")
    }

    // --- RecognitionListener boilerplate ---
    override fun onError(error: Int) {
        statusLabel.text = getString(R.string.status_idle)
    }
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    override fun onDestroy() {
        speechRecognizer.destroy()
        wakeWordListener?.stop()
        voiceEngine.shutdown()
        brain.shutdown()
        super.onDestroy()
    }
}
