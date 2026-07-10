package com.noah.iosnotifications

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Intercepte les notifications systeme. Pour chaque notif recue (hors notre propre appli et
 * hors notifications persistantes des services de premier plan), on la masque (cancel) puis
 * on demande a OverlayBannerService d'afficher une bannière custom style iOS 26 a la place.
 */
class NotificationCaptureService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // On ignore nos propres notifications pour éviter une boucle infinie
        if (sbn.packageName == packageName) return

        // On ignore les notifications "en cours" non annulables (ex: lecteur de musique, appel)
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

        // On masque le popup natif Android...
        cancelNotification(sbn.key)

        // ...et on affiche notre bannière custom à la place
        val overlayIntent = Intent(this, OverlayBannerService::class.java).apply {
            putExtra(OverlayBannerService.EXTRA_APP_NAME, appName)
            putExtra(OverlayBannerService.EXTRA_PACKAGE, sbn.packageName)
            putExtra(OverlayBannerService.EXTRA_TITLE, title)
            putExtra(OverlayBannerService.EXTRA_TEXT, text)
        }
        startService(overlayIntent)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Rien à faire ici pour l'instant
    }
}
