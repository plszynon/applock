package com.plszynon.applock

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

class LockActivity : AppCompatActivity() {
    companion object {
        fun intent(c: Context, pkg: String): Intent =
            Intent(c, LockActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
                .putExtra("pkg", pkg)
    }

    private var target = ""
    private val input = StringBuilder()
    private var msg: TextView? = null
    private var dots: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { goHome() }
        })
        build()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        build()
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isInteractive) goHome() else finish()
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    private fun success() {
        if (target == packageName) Session.mainUnlocked = true else Session.unlockedPkg = target
        finish()
        overridePendingTransition(0, 0)
    }

    private fun buzz() {
        try {
            getSystemService(Vibrator::class.java)
                ?.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }

    private fun tv(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color); gravity = Gravity.CENTER
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun build() {
        target = intent.getStringExtra("pkg") ?: packageName
        input.setLength(0)
        val type = Prefs.lockType(this)
        if (type == null) { success(); return }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(BG)
            setPadding(dp(24), dp(48), dp(24), dp(24))
        }

        var label: String = target
        var icon: Drawable? = null
        try {
            val ai = packageManager.getApplicationInfo(target, 0)
            label = packageManager.getApplicationLabel(ai).toString()
            icon = packageManager.getApplicationIcon(ai)
        } catch (_: Exception) {}
        if (target == packageName) label = "AppLock"

        val iv = ImageView(this)
        if (icon != null) iv.setImageDrawable(icon) else iv.setImageResource(R.drawable.ic_logo)
        root.addView(iv, LinearLayout.LayoutParams(dp(72), dp(72)))
        root.addView(tv(label, 22f, Color.WHITE, true), lpm(12))
        val hint = when (type) {
            "pin" -> "Wpisz PIN"
            "pattern" -> "Narysuj wzór"
            else -> "Wpisz hasło"
        }
        root.addView(tv(hint, 14f, GREY), lpm(4))
        val m = tv(" ", 14f, 0xFFF87171.toInt())
        msg = m
        root.addView(m, lpm(8))

        when (type) {
            "pin" -> buildPin(root)
            "pattern" -> buildPattern(root)
            else -> buildPassword(root)
        }

        if (bioAvailable()) {
            root.addView(styledButton("Użyj odcisku palca", 0xFF334155.toInt()).apply {
                setOnClickListener { showBio() }
            }, lpm(16))
            root.post { showBio() }
        }
        setContentView(root)
    }

    private fun verify(s: String) {
        if (Prefs.check(this, s)) success() else {
            msg?.text = "Błędny kod"
            buzz()
            input.setLength(0)
            refreshDots()
        }
    }

    private fun refreshDots() {
        dots?.text = if (input.isEmpty()) " " else input.map { "●" }.joinToString(" ")
    }

    private fun buildPin(root: LinearLayout) {
        val d = tv(" ", 28f, Color.WHITE)
        dots = d
        root.addView(d, lpm(8))
        val rows = listOf(
            listOf("1", "2", "3"), listOf("4", "5", "6"),
            listOf("7", "8", "9"), listOf("⌫", "0", "✓")
        )
        for (row in rows) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (k in row) {
                val b = styledButton(k, CARD).apply {
                    textSize = 22f
                    setOnClickListener {
                        msg?.text = " "
                        when (k) {
                            "⌫" -> if (input.isNotEmpty()) input.setLength(input.length - 1)
                            "✓" -> verify(input.toString())
                            else -> if (input.length < 12) input.append(k)
                        }
                        refreshDots()
                    }
                }
                r.addView(b, LinearLayout.LayoutParams(dp(80), dp(64)).apply {
                    setMargins(dp(6), dp(4), dp(6), dp(4))
                })
            }
            root.addView(r)
        }
    }

    private fun buildPassword(root: LinearLayout) {
        val e = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(Color.WHITE)
            setHintTextColor(GREY)
            hint = "Hasło"
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine()
            setOnEditorActionListener { _, _, _ ->
                val s = text.toString(); setText(""); verify(s); true
            }
        }
        root.addView(e, lpm(16))
        root.addView(styledButton("Odblokuj").apply {
            setOnClickListener { val s = e.text.toString(); e.setText(""); verify(s) }
        }, lpm(12))
    }

    private fun buildPattern(root: LinearLayout) {
        val p = PatternView(this)
        p.onComplete = { code ->
            if (Prefs.check(this, code)) success() else {
                msg?.text = "Błędny wzór"
                buzz()
            }
        }
        root.addView(p, LinearLayout.LayoutParams(dp(300), dp(300)).apply { topMargin = dp(16) })
    }

    private fun bioAvailable(): Boolean =
        Prefs.biometric(this) &&
            BiometricManager.from(this)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    private fun showBio() {
        val prompt = BiometricPrompt(
            this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { success() }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Odblokuj")
            .setNegativeButtonText("Użyj kodu")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(info)
    }
}
