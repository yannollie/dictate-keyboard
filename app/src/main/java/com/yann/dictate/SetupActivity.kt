package com.yann.dictate

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** One screen that walks through the three setup steps and lets you edit "My words". */
class SetupActivity : Activity() {

    private lateinit var micStatus: TextView
    private lateinit var wordsBox: EditText

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun label(text: String, size: Float, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        if (bold) t.setTypeface(t.typeface, android.graphics.Typeface.BOLD)
        t.setPadding(0, dp(10), 0, dp(4))
        return t
    }

    private fun button(text: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        return b
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(16), dp(16), dp(32))
        scroll.addView(col)

        col.addView(label("Dictate Keyboard", 24f, true))
        col.addView(
            label(
                "A keyboard with a big mic key. Tap the mic, speak, and your words are typed into " +
                    "WhatsApp or any other app. Do the three steps below once.",
                15f
            )
        )

        micStatus = label("", 15f, true)
        col.addView(micStatus)

        col.addView(button("1. Allow microphone") {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Microphone is already allowed", Toast.LENGTH_SHORT).show()
            } else {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            }
        })
        col.addView(button("   If no pop-up appears: open app settings > Permissions > Microphone") {
            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            i.data = Uri.parse("package:$packageName")
            startActivity(i)
        })
        col.addView(button("2. Turn the keyboard on (switch on Dictate Keyboard)") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        col.addView(button("3. Choose Dictate Keyboard as the current keyboard") {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        })

        col.addView(label("My words", 18f, true))
        col.addView(
            label(
                "One per line: what it hears = what to type. Example:  meta trader = MetaTrader. " +
                    "Lines starting with # are ignored.",
                14f
            )
        )
        wordsBox = EditText(this)
        wordsBox.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        wordsBox.minLines = 5
        wordsBox.gravity = Gravity.TOP or Gravity.START
        wordsBox.setText(
            getSharedPreferences("dictate", Context.MODE_PRIVATE)
                .getString("words", TextCleaner.DEFAULT_WORDS)
        )
        col.addView(wordsBox)
        col.addView(button("Save my words") {
            getSharedPreferences("dictate", Context.MODE_PRIVATE)
                .edit()
                .putString("words", wordsBox.text.toString())
                .apply()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        })

        col.addView(label("Say these out loud", 18f, true))
        col.addView(
            label(
                "\"full stop\", \"comma\", \"question mark\", \"exclamation mark\", \"new line\", " +
                    "\"new paragraph\". The words um, uh and erm are removed automatically. " +
                    "(\"period\" and \"colon\" are left alone because they are real words.)",
                14f
            )
        )

        col.addView(label("Try it here", 18f, true))
        val test = EditText(this)
        test.hint = "Tap here, switch to Dictate Keyboard, tap the mic"
        test.minLines = 3
        test.gravity = Gravity.TOP or Gravity.START
        col.addView(test)

        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        refreshMicStatus()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshMicStatus()
    }

    private fun refreshMicStatus() {
        val ok = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        micStatus.text = if (ok) "Microphone: allowed ✔" else "Microphone: NOT allowed yet"
    }
}
