package com.yann.dictate

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

private val PANEL_BG = Color.parseColor("#1C1C1E")
private val KEY_BG = Color.parseColor("#3A3A3C")
private val KEY_DARK = Color.parseColor("#2C2C2E")
private val MIC_IDLE = Color.parseColor("#1B6EF3")
private val MIC_LIVE = Color.parseColor("#D32F2F")
private const val IDLE_TEXT = "Tap the mic, speak, tap again to stop"

/**
 * A small keyboard with a big microphone key. Speech is turned into text by the phone's own
 * speech recogniser (free, no account or API key), cleaned up by TextCleaner, and typed into
 * whatever app is open. Letter keys are included so you can still type normally.
 */
class DictateIME : InputMethodService() {

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var finishing = false
    private var emptyStreak = 0
    private var shift = false

    private lateinit var statusView: TextView
    private lateinit var micButton: Button
    private lateinit var shiftButton: Button
    private val letterButtons = mutableListOf<Button>()

    private val restartRunnable = Runnable { if (listening && !finishing) beginRecognition() }
    private val finishRunnable = Runnable { endSession(IDLE_TEXT) }
    private val repeatDelete = object : Runnable {
        override fun run() {
            deleteOne()
            handler.postDelayed(this, 60)
        }
    }

    // ---------------------------------------------------------------- keyboard UI

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        letterButtons.clear()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(PANEL_BG)
        root.setPadding(dp(4), dp(4), dp(4), dp(6))

        statusView = TextView(this)
        statusView.setTextColor(Color.parseColor("#CCCCCC"))
        statusView.textSize = 14f
        statusView.setPadding(dp(8), dp(4), dp(8), dp(2))
        statusView.maxLines = 2
        statusView.text = IDLE_TEXT
        root.addView(statusView)

        val digits = "1234567890".map { ch ->
            makeKey(ch.toString(), 1f, KEY_BG) { typeText(ch.toString()) }
        }
        root.addView(row(digits))

        root.addView(row(letterKeys("qwertyuiop")))
        root.addView(row(letterKeys("asdfghjkl")))

        shiftButton = makeKey("⇧", 1.5f, KEY_DARK) {
            shift = !shift
            refreshLetters()
        }
        val backspace = makeKey("⌫", 1.5f, KEY_DARK) { }
        backspace.setOnTouchListener { v, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    deleteOne()
                    handler.postDelayed(repeatDelete, 400)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    handler.removeCallbacks(repeatDelete)
                    true
                }
                else -> true
            }
        }
        val thirdRow = ArrayList<View>()
        thirdRow.add(shiftButton)
        thirdRow.addAll(letterKeys("zxcvbnm"))
        thirdRow.add(backspace)
        root.addView(row(thirdRow))

        val globe = makeKey("🌐", 1.2f, KEY_DARK) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
        val comma = makeKey(",", 1f, KEY_BG) { typeText(",") }
        micButton = makeKey("🎤 Speak", 3f, MIC_IDLE) { toggleListening() }
        val space = makeKey("space", 3f, KEY_BG) { typeText(" ") }
        val dot = makeKey(".", 1f, KEY_BG) { typeText(".") }
        val enter = makeKey("⏎", 1.4f, KEY_DARK) { pressEnter() }
        root.addView(row(listOf<View>(globe, comma, micButton, space, dot, enter)))

        return root
    }

    private fun row(keys: List<View>): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        for (k in keys) r.addView(k)
        return r
    }

    private fun letterKeys(letters: String): List<View> {
        val out = ArrayList<View>()
        for (ch in letters) {
            val b = makeKey(ch.toString(), 1f, KEY_BG) { typeLetter(ch) }
            letterButtons.add(b)
            out.add(b)
        }
        return out
    }

    private fun makeKey(label: String, weight: Float, bg: Int, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        styleKey(b, bg)
        val lp = LinearLayout.LayoutParams(0, dp(44), weight)
        lp.setMargins(dp(2), dp(3), dp(2), dp(3))
        b.layoutParams = lp
        b.setOnClickListener { onClick() }
        return b
    }

    private fun styleKey(b: Button, bg: Int) {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.cornerRadius = dp(8).toFloat()
        d.setColor(bg)
        b.background = d
        b.setTextColor(Color.WHITE)
        b.isAllCaps = false
        b.setPadding(0, 0, 0, 0)
        b.minWidth = 0
        b.minimumWidth = 0
        b.minHeight = 0
        b.minimumHeight = 0
        b.textSize = 18f
    }

    private fun refreshLetters() {
        for (b in letterButtons) {
            val t = b.text.toString()
            b.text = if (shift) t.uppercase() else t.lowercase()
        }
        styleKey(shiftButton, if (shift) MIC_IDLE else KEY_DARK)
    }

    private fun updateMic() {
        if (!::micButton.isInitialized) return
        if (finishing) {
            micButton.text = "…"
        } else if (listening) {
            micButton.text = "⏹ Stop"
        } else {
            micButton.text = "🎤 Speak"
        }
        styleKey(micButton, if (listening) MIC_LIVE else MIC_IDLE)
    }

    private fun setStatus(t: String) {
        if (::statusView.isInitialized) statusView.text = t
    }

    // ---------------------------------------------------------------- typing keys

    private fun typeText(s: String) {
        currentInputConnection?.commitText(s, 1)
    }

    private fun typeLetter(ch: Char) {
        val s = if (shift) ch.uppercaseChar().toString() else ch.toString()
        currentInputConnection?.commitText(s, 1)
        if (shift) {
            shift = false
            refreshLetters()
        }
    }

    private fun deleteOne() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (selected.isNullOrEmpty()) {
            ic.deleteSurroundingTextInCodePoints(1, 0)
        } else {
            ic.commitText("", 1)
        }
    }

    private fun pressEnter() {
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo
        if (ei != null) {
            val action = ei.imeOptions and EditorInfo.IME_MASK_ACTION
            val noEnterAction = (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
            if (!noEnterAction &&
                action != EditorInfo.IME_ACTION_NONE &&
                action != EditorInfo.IME_ACTION_UNSPECIFIED
            ) {
                ic.performEditorAction(action)
                return
            }
        }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        endSession(IDLE_TEXT)
        if (shift && ::shiftButton.isInitialized) {
            shift = false
            refreshLetters()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        endSession(IDLE_TEXT)
        handler.removeCallbacks(repeatDelete)
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        endSession(IDLE_TEXT)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ---------------------------------------------------------------- dictation

    private fun toggleListening() {
        if (finishing) return
        if (listening) requestStop() else startListening()
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            setStatus("Microphone is not allowed yet – opening setup…")
            val i = Intent(this, SetupActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setStatus("No speech recogniser found on this phone (install/enable Google app)")
            return
        }
        listening = true
        finishing = false
        emptyStreak = 0
        updateMic()
        beginRecognition()
    }

    private fun beginRecognition() {
        try {
            recognizer?.destroy()
            val r = SpeechRecognizer.createSpeechRecognizer(this)
            r.setRecognitionListener(listener)
            recognizer = r

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            r.startListening(intent)
            setStatus("Listening…")
        } catch (e: Exception) {
            endSession("Could not start the microphone (${e.javaClass.simpleName})")
        }
    }

    /** User tapped Stop: let the recogniser deliver what it has heard so far, then end. */
    private fun requestStop() {
        finishing = true
        handler.removeCallbacks(restartRunnable)
        updateMic()
        setStatus("Finishing…")
        try {
            recognizer?.stopListening()
        } catch (e: Exception) {
            endSession(IDLE_TEXT)
            return
        }
        handler.postDelayed(finishRunnable, 1800)
    }

    private fun endSession(message: String) {
        listening = false
        finishing = false
        emptyStreak = 0
        handler.removeCallbacks(restartRunnable)
        handler.removeCallbacks(finishRunnable)
        try {
            recognizer?.cancel()
        } catch (e: Exception) {
            // ignore
        }
        recognizer?.destroy()
        recognizer = null
        updateMic()
        setStatus(message)
    }

    private fun scheduleRestart() {
        handler.removeCallbacks(restartRunnable)
        handler.postDelayed(restartRunnable, 250)
    }

    private fun noteEmpty() {
        emptyStreak++
        if (emptyStreak >= 3) {
            endSession("Stopped – I didn't hear anything")
        } else {
            scheduleRestart()
        }
    }

    private fun firstResult(b: Bundle?): String {
        val list = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        return list?.firstOrNull()?.trim() ?: ""
    }

    private fun commitDictation(raw: String) {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(3, 0)?.toString() ?: ""
        val saved = getSharedPreferences("dictate", Context.MODE_PRIVATE)
            .getString("words", TextCleaner.DEFAULT_WORDS)
        val words = TextCleaner.parseCustomWords(saved)
        val out = TextCleaner.clean(raw, before, words)
        if (out.isNotEmpty()) ic.commitText(out, 1)
        setStatus("Listening…")
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            if (!listening) return
            val t = firstResult(partialResults)
            if (t.isNotEmpty()) setStatus("… $t")
        }

        override fun onResults(results: Bundle?) {
            if (!listening) return
            val t = firstResult(results)
            if (t.isNotEmpty()) {
                emptyStreak = 0
                commitDictation(t)
            }
            if (finishing) {
                endSession(IDLE_TEXT)
            } else if (t.isEmpty()) {
                noteEmpty()
            } else {
                scheduleRestart()
            }
        }

        override fun onError(error: Int) {
            if (!listening) return
            if (finishing) {
                endSession(IDLE_TEXT)
                return
            }
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> noteEmpty()
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    endSession("Microphone not allowed – open the Dictate Keyboard app")
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER ->
                    endSession("Speech service unreachable – check your internet")
                else -> endSession("Stopped (error $error)")
            }
        }
    }
}
