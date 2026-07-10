package com.noah.iosnotifications

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/**
 * Détecte l'ouverture de l'écran multitâche (Recents / Overview), la referme aussitôt,
 * et lance à la place notre propre écran de tâches récentes façon iOS 26.
 *
 * ATTENTION : la détection se base sur le package/la classe de la fenêtre qui s'affiche.
 * Ça varie selon le launcher installé (SystemUI, Lawnchair Quickstep, etc.) et peut nécessiter
 * des ajustements. La liste ci-dessous couvre les cas les plus courants mais n'est pas garantie
 * à 100% sur tous les appareils/launchers.
 */
class RecentsInterceptorService : AccessibilityService() {

    // Packages connus dont la fenêtre correspond à l'écran multitâche
    private val overviewPackages = setOf(
        "com.android.systemui",
        "app.lawnchair",
        "com.android.launcher3"
    )

    // Mots-clés cherchés dans le nom de la fenêtre/activité pour confirmer que c'est bien
    // l'écran "Recents" et pas juste le launcher normal (écran d'accueil)
    private val overviewClassKeywords = listOf(
        "recents",
        "overview",
        "quickstep",
        "taskview"
    )

    private var lastTriggerTime = 0L
    private val handler = Handler(Looper.getMainLooper())

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString()?.lowercase() ?: ""

        if (pkg !in overviewPackages) return
        val looksLikeOverview = overviewClassKeywords.any { cls.contains(it) }
        if (!looksLikeOverview) return

        // Anti-rebond : on évite de re-déclencher plusieurs fois pour le même événement
        val now = System.currentTimeMillis()
        if (now - lastTriggerTime < 800) return
        lastTriggerTime = now

        // On referme l'écran multitâche natif...
        performGlobalAction(GLOBAL_ACTION_BACK)

        // ...et on ouvre notre écran custom juste après, pour laisser le temps
        // à l'action BACK de s'exécuter avant d'empiler notre activité par-dessus.
        handler.postDelayed({
            val intent = Intent(this, RecentsOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }, 120)
    }

    override fun onInterrupt() {
        // Rien de spécial à faire
    }
}
