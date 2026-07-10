package com.noah.iosnotifications

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView

/**
 * Affiche une bannière flottante style iOS 26 par-dessus toutes les autres applications,
 * a la place du popup de notification natif d'Android (qui a déjà été masqué en amont
 * par NotificationCaptureService).
 */
class OverlayBannerService : Service() {

    private var windowManager: WindowManager? = null
    private var bannerView: View? = null
    private val autoDismissHandler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null

    companion object {
        const val EXTRA_APP_NAME = "extra_app_name"
        const val EXTRA_PACKAGE = "extra_package"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_TEXT = "extra_text"
        private const val AUTO_DISMISS_MS = 4500L
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        // On retire une éventuelle bannière déjà affichée avant d'afficher la nouvelle
        // (sans arrêter le service, sinon la nouvelle bannière qu'on va afficher juste
        // après disparaîtrait aussitôt)
        clearCurrentBannerView()

        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: ""
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: ""
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val text = intent.getStringExtra(EXTRA_TEXT) ?: ""

        showBanner(appName, pkg, title, text)
        return START_NOT_STICKY
    }

    private fun showBanner(appName: String, pkg: String, title: String, text: String) {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_banner, null)
        bannerView = view

        view.findViewById<TextView>(R.id.banner_app_name).text = appName
        view.findViewById<TextView>(R.id.banner_title).text = title
        view.findViewById<TextView>(R.id.banner_text).text = text

        try {
            val icon = packageManager.getApplicationIcon(pkg)
            view.findViewById<ImageView>(R.id.banner_icon).setImageDrawable(icon)
        } catch (e: Exception) {
            // Icône par défaut déjà posée dans le layout
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP
        params.y = dpToPx(48)
        params.horizontalMargin = 0f

        // Vrai flou du contenu situé derrière la bannière (effet "verre liquide" iOS 26),
        // disponible depuis Android 12 (API 31). Sur les versions plus anciennes, le fond
        // semi-transparent du drawable suffit à donner une impression de profondeur.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            params.blurBehindRadius = dpToPx(28)
        }

        // On ajoute une marge horizontale via padding sur le root plutôt que WindowManager
        view.setPadding(dpToPx(12), 0, dpToPx(12), 0)

        windowManager?.addView(view, params)

        val slideDown = AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left).apply {
            duration = 260
        }
        view.startAnimation(slideDown)

        // Glisser vers le haut ou taper dessus pour fermer plus tôt
        setupDismissGestures(view)

        dismissRunnable = Runnable { removeBanner() }
        autoDismissHandler.postDelayed(dismissRunnable!!, AUTO_DISMISS_MS)
    }

    private fun setupDismissGestures(view: View) {
        var startY = 0f
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - startY
                    if (dy < 0) v.translationY = dy
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dy = event.rawY - startY
                    if (dy < -40) {
                        removeBanner()
                    } else {
                        v.translationY = 0f
                        // Simple tap -> on ferme aussi (comme sur iOS un tap ouvre l'app,
                        // ici on se contente de fermer la bannière)
                        if (kotlin.math.abs(dy) < 10) removeBanner()
                    }
                    true
                }
                else -> false
            }
        }
    }

    /** Retire la vue affichée à l'écran sans arrêter le service. */
    private fun clearCurrentBannerView() {
        dismissRunnable?.let { autoDismissHandler.removeCallbacks(it) }
        bannerView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                // Vue déjà retirée
            }
        }
        bannerView = null
    }

    /** Ferme définitivement la bannière (fin du délai, swipe, tap) et arrête le service. */
    private fun removeBanner() {
        clearCurrentBannerView()
        stopSelf()
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    override fun onDestroy() {
        clearCurrentBannerView()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
