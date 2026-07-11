package com.noah.iosnotifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.widget.Toast

/**
 * Intercepte les notifications systeme. Pour chaque notif recue (hors notre propre appli et
 * hors notifications persistantes des services de premier plan), on affiche une bannière custom
 * style iOS 26. Contrairement à une première version, on NE supprime PLUS la notification
 * (cancelNotification) : ça la retirait aussi de la barre d'état et du centre de notifications,
 * ce qui n'était pas voulu. À la place, on baisse l'importance du canal de notification concerné
 * (une seule fois par canal) pour empêcher le popup natif Android de s'afficher, tout en gardant
 * l'icône dans la barre d'état et l'entrée dans le centre de notifs intactes.
 */
class NotificationCaptureService : NotificationListenerService() {

    // Mémorise les canaux déjà "downgradés" pour ne pas refaire l'appel à chaque notif
    private val downgradedChannels = mutableSetOf<String>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // On ignore nos propres notifications pour éviter une boucle infinie
        if (sbn.packageName == packageName) return

        val notif = sbn.notification
        val isOngoing = (notif.flags and Notification.FLAG_ONGOING_EVENT) != 0 ||
            (notif.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
        if (isOngoing) return

        val extras = notif.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        // Rien d'intéressant à afficher -> on laisse Android gérer normalement
        if (title.isBlank() && text.isBlank()) return

        val appName = try {
            val pm = applicationContext.packageManager
            val appInfo = pm.getApplicationInfo(sbn.packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            sbn.packageName
        }

        // On empêche le popup natif de s'afficher pour ce canal (une seule fois par canal)
        suppressHeadsUpForChannel(sbn)

        // ...et on affiche notre bannière custom à la place. On laisse la notification
        // native intacte dans la barre d'état et le centre de notifs.
        val overlayIntent = Intent(this, OverlayBannerService::class.java).apply {
            putExtra(OverlayBannerService.EXTRA_APP_NAME, appName)
            putExtra(OverlayBannerService.EXTRA_PACKAGE, sbn.packageName)
            putExtra(OverlayBannerService.EXTRA_TITLE, title)
            putExtra(OverlayBannerService.EXTRA_TEXT, text)
        }
        startService(overlayIntent)
    }

    private fun suppressHeadsUpForChannel(sbn: StatusBarNotification) {
        val channelId = sbn.notification.channelId
        if (channelId == null) {
            Toast.makeText(this, "DEBUG: pas de channelId pour ${sbn.packageName}", Toast.LENGTH_SHORT).show()
            return
        }
        val key = "${sbn.packageName}/$channelId"
        if (key in downgradedChannels) return

        try {
            val channels = getNotificationChannels(sbn.packageName, sbn.user)
            val channel = channels.firstOrNull { it.id == channelId }
            if (channel == null) {
                Toast.makeText(this, "DEBUG: canal '$channelId' introuvable pour ${sbn.packageName}", Toast.LENGTH_SHORT).show()
                return
            }

            // Seul un canal en importance HIGH (ou plus) déclenche le popup natif (heads-up).
            // On le baisse en DEFAULT : l'icône et l'entrée dans le centre de notifs restent,
            // seul le popup disparaît.
            if (channel.importance >= NotificationManager.IMPORTANCE_HIGH) {
                channel.importance = NotificationManager.IMPORTANCE_DEFAULT
                updateNotificationChannel(sbn.packageName, sbn.user, channel)
                Toast.makeText(this, "DEBUG: canal ${sbn.packageName}/$channelId downgradé", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "DEBUG: canal déjà à ${channel.importance}, pas HIGH", Toast.LENGTH_SHORT).show()
            }
            downgradedChannels.add(key)
        } catch (e: Exception) {
            Toast.makeText(this, "DEBUG: erreur downgrade -> ${e.javaClass.simpleName}: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Rien à faire ici pour l'instant
    }
}
