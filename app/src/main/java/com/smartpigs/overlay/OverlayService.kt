package com.smartpigs.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.abs

class OverlayService : Service() {
    companion object {
        const val ACTION_STOP = "com.smartpigs.overlay.STOP"
        @Volatile var instance: OverlayService? = null
        fun isRunning() = instance != null
    }

    val engine = PigEngine()
    private lateinit var store: SettingsStore
    private lateinit var wm: WindowManager
    private lateinit var canvas: OverlayCanvasView
    private lateinit var canvasParams: WindowManager.LayoutParams
    private var bubble: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var popup: View? = null
    private var popupParams: WindowManager.LayoutParams? = null
    private var lastRestore = 0L
    private lateinit var pigBitmap: Bitmap
    private val io = Executors.newSingleThreadExecutor()
    private var frameCallback: android.view.Choreographer.FrameCallback? = null
    private var popupOpen = false
    private var density = 1f

    private val hitboxes = mutableMapOf<Int, View>()
    private val itemHitboxes = mutableMapOf<Int, View>()

    // Prevent duplicate image downloads.
    private val loadingUrls = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    // Don't hammer WindowManager 60 times/sec for every object.
    private var lastHitboxSync = 0L
    private val hitboxSyncInterval = 33L // ~30 FPS

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        store = SettingsStore(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        density = resources.displayMetrics.density
        pigBitmap = BitmapFactory.decodeStream(assets.open("pig.png"))
        if (pigBitmap.width > 0) {
            engine.imageAspect = pigBitmap.height.toFloat() / pigBitmap.width.toFloat()
        }
        startForeground(7, buildNotification())

        val dm = resources.displayMetrics
        engine.setScreen(dm.widthPixels, dm.heightPixels)

        // First declaration of 'saved'
        val saved = store.getJson()

        // Removed duplicate 'val' declarations and combined configuration steps
        engine.pigCountTarget = saved.optInt("pigCount", 2)

        val savedWidth = saved.optDouble("pigWidth", 0.0).toFloat()
        engine.pigWidth = if (savedWidth >= 36f) savedWidth else 78.0f * density

        engine.imageUrl = saved.optString(
            "imageUrl",
            "https://static.wikia.nocookie.net/neutral-characters/images/d/d0/PeppaPig.webp/revision/latest?cb=20250701022738"
        )

        saved.optJSONObject("modifiers")?.let { obj ->
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                engine.modifiers[k] = obj.optBoolean(k)
            }
        }

        addCanvas()
        addBubble()
        engine.start(saved)
        loadImage(engine.imageUrl)
        startLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopLoop()
        engine.stop()
        removeAllWindows()

        // Stop background image downloads and release the worker thread.
        io.shutdownNow()

        // Release cached downloaded bitmaps.
        runCatching {
            canvas.extraBitmaps.values.forEach { bitmap ->
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
            canvas.extraBitmaps.clear()
        }

        if (!pigBitmap.isRecycled) {
            pigBitmap.recycle()
        }

        loadingUrls.clear()
        instance = null
        super.onDestroy()
    }

    fun handleAction(action: String, extra: JSONObject): JSONObject {
        val result = engine.handle(action, extra)
        when (action) {
            "setImage" -> extra.optString("url").takeIf { it.isNotBlank() }?.let { loadImage(it) }
            "start", "setModifiers", "setPigCount", "setPigSize", "setSize", "addPig", "removePig", "remove", "stop" -> {
                persist()
            }
        }
        extra.optJSONObject("preset")?.optString("imageUrl")?.takeIf { it.isNotBlank() }?.let { loadImage(it) }
        extra.optJSONObject("options")?.optString("imageUrl")?.takeIf { it.isNotBlank() }?.let { loadImage(it) }
        loadMissingPigImages()
        if (action == "addPig" || action == "start" || action == "setImage") {
            extra.optJSONObject("preset")?.optString("imageUrl")?.let { url ->
                if (url.isNotBlank()) loadImage(url)
            }
            extra.optJSONObject("options")?.optString("imageUrl")?.let { url ->
                if (url.isNotBlank()) loadImage(url)
            }
            extra.optString("url").takeIf { it.isNotBlank() }?.let { loadImage(it) }
            loadMissingPigImages()
        }
        canvas.invalidate()
        return result
    }

    fun setPopupOpen(open: Boolean) {
        popupOpen = open
        if (open) showPopup() else hidePopup()
    }

    private fun persist() {
        val cur = store.getJson()
        cur.put("pigCount", engine.pigs.size.coerceAtLeast(engine.pigCountTarget))
        cur.put("pigWidth", engine.pigWidth)
        cur.put("imageUrl", engine.imageUrl)
        cur.put("modifiers", engine.getInfo().optJSONObject("modifiers") ?: JSONObject())
        store.putJson(cur)
    }

    private fun overlayType(): Int =
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    private fun baseFlags(touchable: Boolean): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return flags
    }

    private fun addCanvas() {
        canvas = OverlayCanvasView(this).also {
            it.engine = engine
            it.pigBitmap = pigBitmap

            it.onPigDown = { pig, x, y ->
                engine.beginDragPig(pig, x, y)
            }

            it.onPigMove = { pig, x, y ->
                engine.pointer(x, y)
                engine.moveDragPig(pig, x, y)
            }

            it.onPigUp = { pig ->
                engine.endDragPig(pig)
            }

            it.onItemDown = { item, x, y ->
                engine.beginDragItem(item, x, y)
            }

            it.onItemMove = { item, x, y ->
                engine.moveDragItem(item, x, y)
            }

            it.onItemUp = { item ->
                engine.endDragItem(item)
            }
        }

        canvasParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            baseFlags(false),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "SmartPigsCanvas"
        }

        wm.addView(canvas, canvasParams)
        canvasParams.alpha = 1f
    }

    private fun addBubble() {
        val size = (56 * density).toInt()
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_bubble, null)
        view.findViewById<ImageView>(R.id.bubbleImage).setImageBitmap(
            BitmapFactory.decodeStream(assets.open("pig-face.png")),
        )
        val params = WindowManager.LayoutParams(
            size, size, overlayType(),
            baseFlags(true),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = engine.width.toInt() - size - (16 * density).toInt()
            y = engine.height.toInt() - size - (72 * density).toInt()
            title = "SmartPigsBubble"
        }
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        view.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y; moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).toInt(); val dy = (e.rawY - downY).toInt()
                    if (abs(dx) + abs(dy) > 8) moved = true
                    params.x = (startX + dx).coerceIn(0, engine.width.toInt() - size)
                    params.y = (startY + dy).coerceIn(0, engine.height.toInt() - size)
                    wm.updateViewLayout(view, params)
                    engine.bubbleX = params.x + size / 2f
                    engine.bubbleY = params.y + size / 2f
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) setPopupOpen(!popupOpen)
                    true
                }
                else -> false
            }
        }
        wm.addView(view, params)
        bubble = view
        bubbleParams = params
        engine.bubbleX = params.x + size / 2f
        engine.bubbleY = params.y + size / 2f
    }
    private fun showPopup() {
        if (popup != null) return
        try {
            val width = (340 * density).toInt().coerceAtMost((engine.width * 0.92f).toInt().coerceAtLeast(280))
            val height = (520 * density).toInt().coerceAtMost((engine.height * 0.72f).toInt().coerceAtLeast(360))
            val view = LayoutInflater.from(this).inflate(R.layout.overlay_popup, null)

            val web = view.findViewById<WebView>(R.id.popupWeb)
            web.setBackgroundColor(0x00000000)
            web.webViewClient = WebViewClient()
            web.webChromeClient = WebChromeClient()
            web.settings.javaScriptEnabled = true
            web.settings.domStorageEnabled = true
            web.settings.allowFileAccess = true
            web.settings.allowContentAccess = true
            if (android.os.Build.VERSION.SDK_INT >= 17) {
                web.settings.allowFileAccessFromFileURLs = true
            }
            web.addJavascriptInterface(PigsJsBridge(this, store), "AndroidPigs")
            val html = assets.open("popup.html").bufferedReader().use { it.readText() }
            web.loadDataWithBaseURL(
                "file:///android_asset/",
                html,
                "text/html",
                "utf-8",
                null
            )

            web.isNestedScrollingEnabled = true
            web.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            web.settings.useWideViewPort = true
            web.settings.loadWithOverviewMode = true

            val params = WindowManager.LayoutParams(
                width, height, overlayType(),
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                val maxX = (engine.width.toInt() - width).coerceAtLeast(0)
                val maxY = (engine.height.toInt() - height).coerceAtLeast(0)
                val bx = bubbleParams?.x ?: 0
                val by = bubbleParams?.y ?: 0
                x = (bx - width + (56 * density).toInt()).coerceIn(0, maxX)
                y = (by - height - 12).coerceIn(0, maxY)
                title = "SmartPigsPopup"
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }

            view.findViewById<View>(R.id.btnNativeClose)?.setOnClickListener {
                setPopupOpen(false)
            }

            val dragBar = view.findViewById<View>(R.id.dragBar)
            if (dragBar != null) {
                var downX = 0f
                var downY = 0f
                var startX = 0
                var startY = 0
                dragBar.setOnTouchListener { _, e ->
                    when (e.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = e.rawX
                            downY = e.rawY
                            startX = params.x
                            startY = params.y
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val maxX = (engine.width.toInt() - width).coerceAtLeast(0)
                            val maxY = (engine.height.toInt() - height).coerceAtLeast(0)
                            params.x = (startX + (e.rawX - downX).toInt()).coerceIn(0, maxX)
                            params.y = (startY + (e.rawY - downY).toInt()).coerceIn(0, maxY)
                            runCatching { wm.updateViewLayout(view, params) }
                            true
                        }
                        else -> false
                    }
                }
            }

            wm.addView(view, params)
            view.setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_OUTSIDE) {
                    setPopupOpen(false)
                    true
                } else {
                    false
                }
            }
            popup = view
            popupParams = params
        } catch (e: Exception) {
            android.util.Log.e("SmartPigs", "showPopup failed", e)
            popup = null
            popupParams = null
        }
    }

    private fun hidePopup() {
        popup?.let { view ->
            runCatching {
                wm.removeView(view)
            }

            view.findViewById<WebView>(R.id.popupWeb)?.let { web ->
                runCatching {
                    web.stopLoading()
                    web.loadUrl("about:blank")
                    web.clearHistory()
                    web.removeAllViews()
                    web.destroy()
                }
            }
        }

        popup = null
        popupParams = null
    }
    private fun syncHitboxes() {
        if (popupOpen) {
            hitboxes.values.forEach { runCatching { wm.removeView(it) } }
            hitboxes.clear()

            itemHitboxes.values.forEach { runCatching { wm.removeView(it) } }
            itemHitboxes.clear()
            return
        }

        val w = engine.pigW().toInt().coerceAtLeast(48)
        val h = engine.pigH().toInt().coerceAtLeast(48)

        val ids = engine.pigs.map { it.id }.toSet()

        val stale = hitboxes.keys.filter { it !in ids }

        for (id in stale) {
            hitboxes.remove(id)?.let {
                runCatching { wm.removeView(it) }
            }
        }

        for (pig in engine.pigs) {
            val existing = hitboxes[pig.id]

            if (existing == null) {
                val box = View(this)
                box.setBackgroundColor(0x00000000)

                val lp = WindowManager.LayoutParams(
                    w,
                    h,
                    overlayType(),
                    baseFlags(true),
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = pig.x.toInt()
                    y = pig.y.toInt()
                    title = "SmartPig${pig.id}"
                }

                var dragging = false

                box.setOnTouchListener { _, e ->
                    val loc = IntArray(2)
                    canvas.getLocationOnScreen(loc)

                    val x = e.rawX - loc[0]
                    val y = e.rawY - loc[1]

                    engine.pointer(x, y)

                    when (e.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            dragging = true
                            engine.beginDragPig(pig, x, y)
                            true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            if (dragging) {
                                engine.moveDragPig(pig, x, y)
                            }
                            true
                        }

                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_CANCEL -> {
                            if (dragging) {
                                engine.endDragPig(pig)
                            }

                            dragging = false
                            true
                        }

                        else -> false
                    }
                }

                box.tag = lp

                runCatching {
                    wm.addView(box, lp)
                }

                hitboxes[pig.id] = box
            } else {
                val lp = existing.tag as WindowManager.LayoutParams

                val nx = pig.x.toInt()
                val ny = pig.y.toInt()

                if (
                    abs(lp.x - nx) > 1 ||
                    abs(lp.y - ny) > 1 ||
                    lp.width != w ||
                    lp.height != h
                ) {
                    lp.x = nx
                    lp.y = ny
                    lp.width = w
                    lp.height = h

                    runCatching {
                        wm.updateViewLayout(existing, lp)
                    }
                }
            }
        }

        val items = engine.treats + engine.toys
        val itemIds = items.map { it.id }.toSet()

        val staleItems = itemHitboxes.keys.filter {
            it !in itemIds
        }

        for (id in staleItems) {
            itemHitboxes.remove(id)?.let {
                runCatching { wm.removeView(it) }
            }
        }

        for (item in items) {
            val size = 56
            val existing = itemHitboxes[item.id]

            if (existing == null) {
                val box = View(this)
                box.setBackgroundColor(0x00000000)

                val lp = WindowManager.LayoutParams(
                    size,
                    size,
                    overlayType(),
                    baseFlags(true),
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = item.x.toInt()
                    y = item.y.toInt()
                    title = "SmartItem${item.id}"
                }

                var dragging = false

                box.setOnTouchListener { _, e ->
                    val loc = IntArray(2)
                    canvas.getLocationOnScreen(loc)

                    val x = e.rawX - loc[0]
                    val y = e.rawY - loc[1]

                    when (e.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            dragging = true
                            engine.beginDragItem(item, x, y)
                            true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            if (dragging) {
                                engine.moveDragItem(item, x, y)
                            }
                            true
                        }

                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_CANCEL -> {
                            if (dragging) {
                                engine.endDragItem(item)
                            }

                            dragging = false
                            true
                        }

                        else -> false
                    }
                }

                box.tag = lp

                runCatching {
                    wm.addView(box, lp)
                }

                itemHitboxes[item.id] = box
            } else {
                val lp = existing.tag as WindowManager.LayoutParams

                val nx = item.x.toInt()
                val ny = item.y.toInt()

                if (abs(lp.x - nx) > 1 || abs(lp.y - ny) > 1) {
                    lp.x = nx
                    lp.y = ny

                    runCatching {
                        wm.updateViewLayout(existing, lp)
                    }
                }
            }
        }
    }

    private fun restoreOverlayIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastRestore < 800) return
        lastRestore = now
        canvas.alpha = 1f
        canvas.visibility = View.VISIBLE
        canvasParams.alpha = 1f
        runCatching { wm.updateViewLayout(canvas, canvasParams) }
        if (!canvas.isAttachedToWindow || canvas.width <= 0) {
            runCatching { wm.removeView(canvas) }
            runCatching { wm.addView(canvas, canvasParams) }
        }
        bubble?.let { b ->
            b.alpha = 1f
            b.visibility = View.VISIBLE
            bubbleParams?.alpha = 1f
            bubbleParams?.let { runCatching { wm.updateViewLayout(b, it) } }
        }
    }

    private fun startLoop() {
        val choreographer = android.view.Choreographer.getInstance()

        val cb = object : android.view.Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                val now = System.currentTimeMillis()

                engine.step(now)

                restoreOverlayIfNeeded()
                canvas.invalidate()

                // Rendering stays at display refresh rate,
                // but WindowManager hitboxes update at ~30 FPS.
                if (now - lastHitboxSync >= hitboxSyncInterval) {
                    lastHitboxSync = now
                    syncHitboxes()
                }

                choreographer.postFrameCallback(this)
            }
        }

        frameCallback = cb
        choreographer.postFrameCallback(cb)
    }

    private fun stopLoop() {
        frameCallback?.let { android.view.Choreographer.getInstance().removeFrameCallback(it) }
        frameCallback = null
    }

    private fun removeAllWindows() {
        hidePopup()

        hitboxes.values.forEach {
            runCatching { wm.removeView(it) }
        }
        hitboxes.clear()

        itemHitboxes.values.forEach {
            runCatching { wm.removeView(it) }
        }
        itemHitboxes.clear()

        bubble?.let {
            runCatching { wm.removeView(it) }
        }

        bubble = null
        bubbleParams = null

        runCatching {
            if (canvas.isAttachedToWindow) {
                wm.removeView(canvas)
            }
        }
    }

    private fun loadMissingPigImages() {
        val urls = mutableSetOf<String>()
        if (engine.imageUrl.isNotBlank()) urls.add(engine.imageUrl)
        engine.pigs.forEach { pig ->
            if (pig.imageUrl.isNotBlank()) urls.add(pig.imageUrl)
        }
        urls.forEach { loadImage(it) }
    }

private fun loadImage(url: String) {
    if (url.isBlank()) return

    val isAsset = url.startsWith("asset:") ||
            url == "/pig.png" ||
            (url.endsWith("pig.png") && !url.startsWith("http"))

    if (isAsset) {
        canvas.pigBitmap = pigBitmap
        canvas.invalidate()
        return
    }

    // Already loaded.
    if (canvas.extraBitmaps.containsKey(url)) return

    // Already being downloaded.
    if (!loadingUrls.add(url)) return

    io.execute {
        try {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            URL(url).openStream().use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return@execute
            }

            // Keep downloaded images small.
            val maxDimension = 512

            var sample = 1
            while (
                bounds.outWidth / sample > maxDimension ||
                bounds.outHeight / sample > maxDimension
            ) {
                sample *= 2
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val bmp = URL(url).openStream().use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: return@execute

            canvas.post {
                loadingUrls.remove(url)

                // Another request may have completed first.
                if (canvas.extraBitmaps.containsKey(url)) {
                    if (!bmp.isRecycled) {
                        bmp.recycle()
                    }
                    return@post
                }

                // Keep the cache bounded.
                if (canvas.extraBitmaps.size >= 12) {
                    val iterator = canvas.extraBitmaps.entries.iterator()

                    while (iterator.hasNext()) {
                        val entry = iterator.next()

                        // Don't evict the currently displayed main image.
                        if (entry.key != engine.imageUrl) {
                            iterator.remove()

                            if (!entry.value.isRecycled) {
                                entry.value.recycle()
                            }
                            break
                        }
                    }
                }

                canvas.extraBitmaps[url] = bmp

                if (url == engine.imageUrl) {
                    canvas.pigBitmap = bmp

                    if (bmp.width > 0) {
                        engine.imageAspect =
                            bmp.height.toFloat() / bmp.width.toFloat()
                    }
                }

                canvas.invalidate()
            }
        } catch (_: Exception) {
            loadingUrls.remove(url)
        }
    }
}

    private fun buildNotification(): Notification {
        val channelId = "pigs_overlay"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val launch = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_pig)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(launch)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .build()
    }
}
