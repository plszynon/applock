package com.plszynon.applock

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.biometric.BiometricManager

class MainActivity : AppCompatActivity() {
    private class AppItem(val pkg: String, val label: String, val icon: Drawable)
    private class Holder(val icon: ImageView, val name: TextView, val sw: SwitchCompat)

    private var apps: List<AppItem>? = null
    private var skipRelock = false

    override fun onResume() {
        super.onResume()
        if (Prefs.hasLock(this) && !Session.mainUnlocked) {
            setContentView(View(this).apply { setBackgroundColor(BG) })
            startActivity(LockActivity.intent(this, packageName))
            return
        }
        skipRelock = false
        buildUi()
    }

    override fun onStop() {
        super.onStop()
        if (!skipRelock && !isChangingConfigurations) Session.mainUnlocked = false
    }

    private fun tv(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun serviceEnabled(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return s.split(':').any { it.startsWith("$packageName/") && it.endsWith("AppLockService") }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(8))
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(ImageView(this).apply { setImageResource(R.drawable.ic_logo) }, LinearLayout.LayoutParams(dp(56), dp(56)))
        val ht = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        ht.addView(tv("AppLock", 24f, Color.WHITE, true))
        ht.addView(tv("Blokada aplikacji: PIN, hasło, wzór", 13f, GREY))
        head.addView(ht)
        root.addView(head)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundBg(CARD, 16)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val on = serviceEnabled()
        card.addView(
            tv(
                if (on) "✅ Usługa blokowania włączona" else "⚠️ Usługa blokowania wyłączona",
                14f, if (on) 0xFF4ADE80.toInt() else 0xFFFBBF24.toInt()
            )
        )
        if (!on) {
            card.addView(styledButton("Włącz usługę dostępności").apply {
                setOnClickListener {
                    skipRelock = true
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }, lpm())
            card.addView(
                tv("Jeśli przełącznik jest szary (Android 13+): Info o aplikacji → ⋮ → „Zezwól na ustawienia z ograniczeniami”.", 12f, GREY),
                lpm(6)
            )
            card.addView(styledButton("Info o aplikacji", 0xFF334155.toInt()).apply {
                setOnClickListener {
                    skipRelock = true
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }
            }, lpm(6))
        }
        card.addView(
            styledButton(if (Prefs.hasLock(this)) "Zmień blokadę" else "Ustaw blokadę (PIN / hasło / wzór)").apply {
                setOnClickListener { chooseType() }
            }, lpm()
        )
        val bioOk = Prefs.hasLock(this) &&
            BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
        if (bioOk) {
            card.addView(SwitchCompat(this).apply {
                text = "Odblokowuj odciskiem palca"
                setTextColor(Color.WHITE)
                isChecked = Prefs.biometric(this@MainActivity)
                setOnCheckedChangeListener { _, v -> Prefs.setBiometric(this@MainActivity, v) }
            }, lpm(10))
        }
        root.addView(card, lpm(16))

        root.addView(tv("Wybierz aplikacje do zablokowania", 15f, Color.WHITE, true), lpm(16))
        val lv = ListView(this).apply { divider = null; dividerHeight = 0 }
        root.addView(lv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(8) })
        setContentView(root)

        val cached = apps
        if (cached != null) lv.adapter = AppAdapter(cached) else Thread {
            val pm = packageManager
            val list = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { AppItem(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
                .filter { it.pkg != packageName }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
            runOnUiThread { apps = list; lv.adapter = AppAdapter(list) }
        }.start()
    }

    private inner class AppAdapter(val items: List<AppItem>) : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row: LinearLayout
            val h: Holder
            if (convertView == null) {
                row = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(4), dp(8), dp(4), dp(8))
                }
                val ic = ImageView(this@MainActivity)
                row.addView(ic, LinearLayout.LayoutParams(dp(40), dp(40)))
                val nm = tv("", 15f, Color.WHITE)
                row.addView(nm, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(12) })
                val sw = SwitchCompat(this@MainActivity)
                row.addView(sw)
                h = Holder(ic, nm, sw)
                row.tag = h
                row.setOnClickListener { sw.performClick() }
            } else {
                row = convertView as LinearLayout
                h = row.tag as Holder
            }
            val item = items[position]
            h.icon.setImageDrawable(item.icon)
            h.name.text = item.label
            h.sw.isChecked = Prefs.isLocked(this@MainActivity, item.pkg)
            h.sw.setOnClickListener {
                if (!Prefs.hasLock(this@MainActivity)) {
                    h.sw.isChecked = false
                    Toast.makeText(this@MainActivity, "Najpierw ustaw blokadę", Toast.LENGTH_SHORT).show()
                } else {
                    Prefs.setLocked(this@MainActivity, item.pkg, h.sw.isChecked)
                }
            }
            return row
        }
    }

    private fun chooseType() {
        AlertDialog.Builder(this)
            .setTitle("Typ blokady")
            .setItems(arrayOf("PIN (cyfry)", "Hasło", "Wzór")) { _, i ->
                when (i) {
                    0 -> setupText("pin")
                    1 -> setupText("password")
                    else -> setupPattern()
                }
            }.show()
    }

    private fun setupText(type: String) {
        val inType = if (type == "pin") InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), 0) }
        val e1 = EditText(this).apply { inputType = inType; hint = if (type == "pin") "Nowy PIN (min. 4 cyfry)" else "Nowe hasło (min. 4 znaki)" }
        val e2 = EditText(this).apply { inputType = inType; hint = "Powtórz" }
        box.addView(e1); box.addView(e2)
        val d = AlertDialog.Builder(this).setTitle("Ustaw blokadę").setView(box)
            .setPositiveButton("Zapisz", null).setNegativeButton("Anuluj", null).create()
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val a = e1.text.toString()
                val b = e2.text.toString()
                when {
                    a.length < 4 -> Toast.makeText(this, "Minimum 4 znaki", Toast.LENGTH_SHORT).show()
                    a != b -> Toast.makeText(this, "Wpisy się różnią", Toast.LENGTH_SHORT).show()
                    else -> {
                        Prefs.setLock(this, type, a)
                        Session.mainUnlocked = true
                        d.dismiss()
                        Toast.makeText(this, "Blokada zapisana", Toast.LENGTH_SHORT).show()
                        buildUi()
                    }
                }
            }
        }
        d.show()
    }

    private fun setupPattern() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(16), dp(8), dp(16), 0) }
        val info = tv("Narysuj wzór (min. 4 punkty)", 14f, Color.WHITE)
        val pv = PatternView(this)
        box.addView(info)
        box.addView(pv, LinearLayout.LayoutParams(dp(260), dp(260)).apply { topMargin = dp(12) })
        val d = AlertDialog.Builder(this).setTitle("Ustaw wzór").setView(box).setNegativeButton("Anuluj", null).create()
        var first: String? = null
        pv.onComplete = { code ->
            val f = first
            when {
                code.split("-").size < 4 -> info.text = "Za krótki — min. 4 punkty"
                f == null -> { first = code; info.text = "Narysuj ten sam wzór ponownie" }
                f == code -> {
                    Prefs.setLock(this, "pattern", code)
                    Session.mainUnlocked = true
                    d.dismiss()
                    Toast.makeText(this, "Blokada zapisana", Toast.LENGTH_SHORT).show()
                    buildUi()
                }
                else -> { first = null; info.text = "Wzory się różnią — zacznij od nowa" }
            }
        }
        d.show()
    }
}
