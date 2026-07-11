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
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.abs

/**
 * Affiche une bannière flottante style iOS 26 par-dessus toutes les autres applications,
 * a la place du popup de notification natif d'Android (qui a déjà été masqué en amont
 * par NotificationCaptureService).
 *
 * L'apparition/disparition est animée depuis le haut-centre de l'écran (zone du notch),
 * façon "Dynamic Island" : la bannière grossit depuis ce point puis rétrécit vers ce
 * même point en disparaissant.
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
        private const val ENTER_DURATION_MS = 340L
        private const val EXIT_DURATION_MS = 220L
        private const val MIN_SCALE = 0.15f
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            params.blurBehindRadius = dpToPx(28)
        }

        view.setPadding(dpToPx(12), 0, dpToPx(12), 0)

        // Etat initial "réduit au point du notch" avant attachement, pour éviter un flash
        // à taille normale au premier affichage.
        view.alpha = 0f
        view.scaleX = MIN_SCALE
        view.scaleY = MIN_SCALE

        windowManager?.addView(view, params)

        view.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                view.pivotX = view.width / 2f
                view.pivotY = 0f
                animateIn(view)
            }
        })

        setupDismissGestures(view)

        dismissRunnable = Runnable { animateOutAndStop() }
        autoDismissHandler.postDelayed(dismissRunnable!!, AUTO_DISMISS_MS)
    }

    /** Anime l'apparition : grossit + fondu depuis le point du notch (haut-centre). */
    private fun animateIn(view: View) {
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(ENTER_DURATION_MS)
            .setInterpolator(OvershootInterpolator(1.0f))
            .setListener(null)
            .start()
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
                    if (dy < -40 || abs(dy) < 10) {
                        animateOutAndStop()
                    } else {
                        v.animate().translationY(0f).setDuration(150).start()
                    }
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Anime la disparition : rétrécit + fondu vers le point du notch (haut-centre), puis
     * retire réellement la vue et arrête le service une fois l'animation terminée.
     */
    private fun animateOutAndStop() {
        dismissRunnable?.let { autoDismissHandler.removeCallbacks(it) }
        val view = bannerView
        if (view == null) {
            stopSelf()
            return
        }

        view.animate()
            .alpha(0f)
            .scaleX(MIN_SCALE)
            .scaleY(MIN_SCALE)
            .translationY(0f)
            .setDuration(EXIT_DURATION_MS)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                try {
                    windowManager?.removeView(view)
                } catch (e: Exception) {
                    // Vue déjà retirée
                }
                if (bannerView === view) bannerView = null
                stopSelf()
            }
            .start()
    }

    /** Retire la vue affichée à l'écran instantanément, sans animation ni arrêt du service. */
    private fun clearCurrentBannerView() {
        dismissRunnable?.let { autoDismissHandler.removeCallbacks(it) }
        bannerView?.let {
            it.animate().cancel()
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                // Vue déjà retirée
            }
        }
        bannerView = null
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
