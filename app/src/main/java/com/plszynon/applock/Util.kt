package com.plszynon.applock

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.LinearLayout
import java.security.MessageDigest
import java.util.UUID

const val BG = 0xFF0F172A.toInt()
const val CARD = 0xFF1E293B.toInt()
const val GREY = 0xFF94A3B8.toInt()
const val INDIGO = 0xFF4F46E5.toInt()

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.roundBg(color: Int, r: Int = 14): GradientDrawable =
    GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat() }

fun Context.styledButton(label: String, bg: Int = INDIGO): Button =
    Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = roundBg(bg)
        stateListAnimator = null
    }

fun Context.lpm(top: Int = 8): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        .apply { topMargin = dp(top) }

/** Stan w pamięci (czyszczony po ubiciu procesu). */
object Session {
    @Volatile var mainUnlocked = false
    @Volatile var unlockedPkg: String? = null
}

/** Zapis ustawień. Kod jest przechowywany tylko jako hash SHA-256 z solą. */
object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("applock", Context.MODE_PRIVATE)

    private fun hash(c: Context, s: String): String {
        var salt = sp(c).getString("salt", null)
        if (salt == null) {
            salt = UUID.randomUUID().toString()
            sp(c).edit().putString("salt", salt).apply()
        }
        val d = MessageDigest.getInstance("SHA-256").digest((salt + s).toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }

    fun lockType(c: Context): String? = sp(c).getString("type", null)
    fun hasLock(c: Context) = lockType(c) != null

    fun setLock(c: Context, type: String, secret: String) {
        sp(c).edit().putString("type", type).putString("hash", hash(c, secret)).apply()
    }

    fun check(c: Context, secret: String) = sp(c).getString("hash", null) == hash(c, secret)

    fun isLocked(c: Context, pkg: String) =
        sp(c).getStringSet("apps", emptySet())?.contains(pkg) == true

    fun setLocked(c: Context, pkg: String, on: Boolean) {
        val s = HashSet(sp(c).getStringSet("apps", emptySet()) ?: emptySet())
        if (on) s.add(pkg) else s.remove(pkg)
        sp(c).edit().putStringSet("apps", s).apply()
    }

    fun biometric(c: Context) = sp(c).getBoolean("bio", false)
    fun setBiometric(c: Context, v: Boolean) { sp(c).edit().putBoolean("bio", v).apply() }
}
