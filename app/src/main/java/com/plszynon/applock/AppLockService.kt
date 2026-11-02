package com.plszynon.applock

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat

class AppLockService : AccessibilityService() {
    private var imePkgs: Set<String> = emptySet()
    private var imeStamp = 0L
    private var pendingPkg: String? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    pendingPkg = Session.unlockedPkg
                    Session.unlockedPkg = null
                    Session.mainUnlocked = false
                }
                Intent.ACTION_USER_PRESENT -> {
                    val p = pendingPkg
                    pendingPkg = null
                    if (p != null && Prefs.isLocked(c, p)) startActivity(LockActivity.intent(c, p))
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, receiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        refreshIme()
    }

    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun refreshIme() {
        val now = System.currentTimeMillis()
        if (now - imeStamp < 30_000 && imePkgs.isNotEmpty()) return
        imeStamp = now
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imePkgs = imm.enabledInputMethodList.map { it.packageName }.toSet()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        val cls = e.className?.toString() ?: ""
        refreshIme()
        if (pkg == packageName || pkg == "com.android.systemui" || pkg in imePkgs || cls.startsWith("android.")) return
        if (Prefs.isLocked(this, pkg)) {
            if (Session.unlockedPkg == pkg) return
            startActivity(LockActivity.intent(this, pkg))
        } else {
            Session.unlockedPkg = null
        }
    }

    override fun onInterrupt() {}
}
