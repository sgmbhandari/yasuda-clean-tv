package local.cleanhome.tv

import android.app.Activity
import android.app.ActivityOptions
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.EditText
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import java.lang.ref.WeakReference
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import java.util.concurrent.Executors

class HomeActivity : Activity() {
    private val ui = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val refreshIo = Executors.newSingleThreadExecutor()
    private lateinit var store: WallpaperStore
    private lateinit var rotation: Rotation
    private lateinit var background: ImageView
    private lateinit var outgoingBackground: ImageView
    private var wallpaperFade: ValueAnimator? = null
    private var fadingBitmap: Bitmap? = null
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var caption: TextView
    private lateinit var dock: LinearLayout
    private lateinit var wallpaperControl: ImageButton
    private var glassBackdrop: Bitmap? = null
    private val inputBanner by lazy { InputBanner(applicationContext) }
    private val glassSurfaces = mutableListOf<WeakReference<GlassDrawable>>()
    private var bitmap: Bitmap? = null
    private var dialog: AlertDialog? = null
    @Volatile private var visible = false
    @Volatile private var refreshLoading = false
    private var refreshRetryAfter = 0L
    private var loading = false
    private var generation = 0
    private var retryAfter = 0L
    @Volatile private var restoring = false
    private var heldView: View? = null
    private var heldTriggered = false
    private val heldAction = Runnable {
        heldView?.let { view ->
            heldView = null
            heldTriggered = true
            view.performLongClick()
        }
    }
    private var apps: List<ResolveInfo> = emptyList()
    private var dockSignature = ""
    private val focusMemory by lazy { FocusMemory(prefs.getString("focused_app", null)) }
    private val prefs by lazy { getSharedPreferences("home", MODE_PRIVATE) }
    private val defaults = listOf("org.smarttube.stable", "com.amazon.amazonvideo.livingroom", "org.videolan.vlc", "org.mozilla.firefox")
    private val names = mapOf("org.smarttube.stable" to "SmartTube", "com.amazon.amazonvideo.livingroom" to "Prime Video", "org.videolan.vlc" to "VLC", "org.mozilla.firefox" to "Firefox")
    private val ink = TvDesign.foreground
    private val muted = TvDesign.secondary
    private val accent = Color.WHITE

    private val tick = object : Runnable {
        override fun run() {
            if (!visible) return
            if (restoring) return
            val now = System.currentTimeMillis()
            val labels = ClockLabels.at(now)
            clock.text = SpannableString(labels.time).apply {
                val period = labels.time.lastIndexOf(' ')
                if (period > 0) setSpan(RelativeSizeSpan(.38f), period, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            date.text = labels.date
            if (Rotation.isDue(store.state(), now) && SystemClock.elapsedRealtime() >= retryAfter) changeWallpaper(false)
            refreshWallpapers(now)
            ui.postDelayed(this, 15_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        store = WallpaperStore(applicationContext)
        rotation = Rotation(store.rotationCatalogue)
        buildHome()
        loadCurrent()
    }

    override fun onResume() {
        super.onResume()
        inputBanner.close()
        visible = true
        refreshApps()
        for (index in 0 until dock.childCount) (dock.getChildAt(index) as? AppCard)?.let { it.focusAppearance(it.hasFocus(), true) }
        ui.removeCallbacks(tick)
        ui.post(tick)
    }

    override fun onPause() {
        visible = false
        generation++
        loading = false
        ui.removeCallbacks(tick)
        ui.removeCallbacks(heldAction)
        heldView = null
        dialog?.dismiss()
        for (index in 0 until dock.childCount) (dock.getChildAt(index) as? AppCard)?.cancelMotion()
        super.onPause()
    }

    override fun onDestroy() {
        inputBanner.close()
        generation++
        io.shutdownNow()
        refreshIo.shutdownNow()
        ui.removeCallbacksAndMessages(null)
        wallpaperFade?.removeAllListeners()
        wallpaperFade?.cancel()
        outgoingBackground.setImageDrawable(null)
        fadingBitmap?.recycle()
        background.setImageDrawable(null)
        bitmap?.recycle()
        bitmap = null
        glassBackdrop?.recycle()
        glassBackdrop = null
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        ui.removeCallbacks(heldAction)
        heldView = null
        dialog?.dismiss()
        restoreDockFocus()
    }

    @Deprecated("Android 9 remote Back")
    override fun onBackPressed() {
        if (dialog?.isShowing == true) dialog?.dismiss() else dock.getChildAt(0)?.requestFocus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_UP) {
            wallpaperMenu()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float, color: Int = ink) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        fontFeatureSettings = "tnum"
        includeFontPadding = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    private fun glass(owner: View, panel: Boolean = false): GlassDrawable = GlassDrawable(owner, { glassBackdrop }, panel).also {
        glassSurfaces += WeakReference(it)
    }

    private fun focus(view: View, quiet: Boolean = false) {
        view.id = View.generateViewId()
        if (view is TextView) view.minimumHeight = dp(48)
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.requestFocus()
                    v.isPressed = true
                    (v as? AppCard)?.press(true)
                    heldTriggered = false
                    ui.removeCallbacks(heldAction)
                    heldView = if (v.isLongClickable) v else null
                    if (v.isLongClickable) ui.postDelayed(heldAction, android.view.ViewConfiguration.getLongPressTimeout().toLong())
                }
                android.view.MotionEvent.ACTION_UP -> {
                    ui.removeCallbacks(heldAction)
                    if (v.isPressed && !heldTriggered) v.performClick()
                    v.isPressed = false
                    (v as? AppCard)?.press(false)
                    heldView = null
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    ui.removeCallbacks(heldAction)
                    v.isPressed = false
                    (v as? AppCard)?.press(false)
                    heldView = null
                }
            }
            true
        }
        if (view !is AppCard && view !is NavigationLabel) view.background = glass(view)
        view.setOnFocusChangeListener { v, hasFocus ->
            if (v is AppCard) {
                v.focusAppearance(hasFocus)
                if (hasFocus) (v.tag as? String)?.let { id ->
                    focusMemory.appId = id
                    prefs.edit().putString("focused_app", id).apply()
                }
            } else if (v !is NavigationLabel) {
                v.background?.state = v.drawableState
                if (v is TextView) v.setTextColor(ink)
                v.animate().cancel()
                v.animate().scaleX(if (hasFocus) 1.04f else 1f).scaleY(if (hasFocus) 1.04f else 1f)
                    .setDuration(TvDesign.duration(this, TvDesign.FOCUS_EXIT)).setInterpolator(TvDesign.ease).start()
            }
        }
        view.setOnKeyListener { v, code, event ->
            if (code !in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER) || !v.isLongClickable) false
            else {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (event.repeatCount == 0) {
                        (v as? AppCard)?.press(true)
                        heldView = v
                        heldTriggered = false
                        ui.removeCallbacks(heldAction)
                        ui.postDelayed(heldAction, android.view.ViewConfiguration.getLongPressTimeout().toLong())
                    } else if (event.isLongPress && !heldTriggered) {
                        ui.removeCallbacks(heldAction)
                        heldAction.run()
                    }
                } else if (event.action == KeyEvent.ACTION_UP) {
                    ui.removeCallbacks(heldAction)
                    (v as? AppCard)?.press(false)
                    if (!heldTriggered && !event.isCanceled) v.performClick()
                    heldView = null
                }
                true
            }
        }
    }

    private fun buildHome() {
        val root = FrameLayout(this).apply { setBackgroundColor(TvDesign.midnight); clipChildren = false }
        background = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        root.addView(background, FrameLayout.LayoutParams(-1, -1))
        outgoingBackground = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        root.addView(outgoingBackground, FrameLayout.LayoutParams(-1, -1))
        val timeBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        clock = text("", 68f).apply { typeface = Typeface.create("sans-serif", Typeface.NORMAL); letterSpacing = -.035f }
        date = text("", 24f, ink).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
        timeBox.addView(clock)
        timeBox.addView(date, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(10) })
        root.addView(timeBox, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { leftMargin = dp(54); topMargin = dp(46) })

        wallpaperControl = ImageButton(this).apply {
            setImageDrawable(PhotoIconDrawable())
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(16), dp(16), dp(16), dp(16))
            contentDescription = "Wallpaper settings"
            tooltipText = "Wallpapers"
            stateListAnimator = null
            focus(this, true)
            setOnClickListener { wallpaperMenu() }
        }
        root.addView(wallpaperControl, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.TOP or Gravity.END).apply { rightMargin = dp(54); topMargin = dp(50) })
        val lower = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        dock = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_HORIZONTAL; clipChildren = false; clipToPadding = false }
        lower.addView(dock, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; clipChildren = false; clipToPadding = false }
        val shortcuts = listOf("All Apps" to { allApps() }, "Inputs" to { inputs() }, "Settings" to { settings() })
        shortcuts.forEachIndexed { index, (label, action) ->
            val button = NavigationLabel(this).apply {
                text = label; textSize = 14f; setTextColor(muted)
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                gravity = Gravity.CENTER; includeFontPadding = false; letterSpacing = 0f
                contentDescription = label; stateListAnimator = null
                focus(this, true); setOnClickListener { action() }
            }
            controls.addView(button, LinearLayout.LayoutParams(dp(120), dp(48)).apply { if (index < shortcuts.lastIndex) rightMargin = dp(24) })
        }
        lower.addView(controls, LinearLayout.LayoutParams(-2, dp(48)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(26) })
        root.addView(lower, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { leftMargin = dp(54); rightMargin = dp(54); bottomMargin = dp(44) })
        caption = text("", 11f, muted).apply { gravity = Gravity.END; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        root.addView(caption, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { rightMargin = dp(54); bottomMargin = dp(12) })
        setContentView(root)
    }

    private fun label(info: ResolveInfo): String = names[info.activityInfo.packageName] ?: info.loadLabel(packageManager).toString()

    private fun refreshApps() {
        val byPackage = linkedMapOf<String, ResolveInfo>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0).forEach {
                if (it.activityInfo.packageName != packageName) byPackage.putIfAbsent(it.activityInfo.packageName, it)
            }
        }
        apps = byPackage.values.sortedBy { label(it).lowercase(Locale.ROOT) }
        val ids = Favourites.visible(favourites(), AppVisibility.visible(byPackage.keys.toList(), hiddenApps()).toSet()).take(6)
        val signature = ids.joinToString() + apps.joinToString { it.activityInfo.packageName + it.activityInfo.name }
        if (signature == dockSignature) { restoreDockFocus(); return }
        dockSignature = signature
        val remembered = focusMemory.resolve(ids)
        dock.removeAllViews()
        val availableWidth = resources.displayMetrics.widthPixels - dp(108)
        val tileWidth = ((availableWidth - dp(24) * (ids.size - 1).coerceAtLeast(0)) / ids.size.coerceAtLeast(1)).coerceAtMost(dp(230))
        ids.forEachIndexed { index, id ->
            val tile = appTile(byPackage.getValue(id), tileWidth)
            tile.tag = id
            tile.setOnClickListener { launch(byPackage.getValue(id)) }
            tile.setOnLongClickListener { favouriteMenu(id); true }
            dock.addView(tile, LinearLayout.LayoutParams(tileWidth, -2).apply { if (index < ids.lastIndex) rightMargin = dp(24) })
        }
        if (dock.childCount == 0) {
            val add = text("＋  Add favourites", 17f).apply { gravity = Gravity.CENTER; focus(this); setOnClickListener { allApps() } }
            dock.addView(add, LinearLayout.LayoutParams(dp(220), dp(112)))
        }
        focusMemory.appId = remembered
        restoreDockFocus()
    }

    private fun restoreDockFocus() {
        val id = focusMemory.resolve((0 until dock.childCount).mapNotNull { dock.getChildAt(it).tag as? String })
        ((0 until dock.childCount).map { dock.getChildAt(it) }.firstOrNull { it.tag == id } ?: dock.getChildAt(0))?.requestFocus()
    }

    private fun favourites() = prefs.getString("favourites", null)?.split(',')?.filter { it.isNotEmpty() } ?: defaults
    private fun saveFavourites(values: List<String>) { prefs.edit().putString("favourites", values.distinct().joinToString(",")).apply(); refreshApps() }
    private fun hiddenApps(): Set<String> = prefs.getStringSet("hidden_apps", emptySet())?.toSet() ?: emptySet()
    private fun setHidden(id: String, hidden: Boolean) {
        prefs.edit().putStringSet("hidden_apps", if (hidden) hiddenApps() + id else hiddenApps() - id).apply()
        refreshApps()
    }

    private fun appTile(info: ResolveInfo, width: Int = dp(155)): AppCard {
        val banner = info.activityInfo.loadBanner(packageManager)
        return AppCard(this, label(info), banner ?: info.loadIcon(packageManager), banner != null, info.activityInfo.packageName, width).apply { focus(this) }
    }

    private fun launch(info: ResolveInfo) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).setComponent(ComponentName(info.activityInfo.packageName, info.activityInfo.name))
                .addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            val animations = if (TvDesign.duration(this, 220) == 0L) ActivityOptions.makeCustomAnimation(this, 0, 0)
                else ActivityOptions.makeCustomAnimation(this, R.anim.app_enter, R.anim.home_exit)
            startActivity(intent, animations.toBundle())
        } catch (_: RuntimeException) { toast("This app could not be opened"); refreshApps() }
    }

    private fun showDialog(builder: AlertDialog.Builder) {
        dialog?.dismiss()
        dialog = builder.create().also { active ->
            active.setOnDismissListener { if (dialog === active) dialog = null }
            active.show()
            active.window?.let { window -> window.setBackgroundDrawable(glass(window.decorView, true)) }
            active.window?.setWindowAnimations(if (TvDesign.duration(this, 220) == 0L) 0 else R.style.CleanPanelAnimation)
            active.window?.setLayout(dp(900).coerceAtMost(resources.displayMetrics.widthPixels - dp(120)), -2)
        }
    }

    private fun allApps(focusedId: String? = null) {
        val panelWidth = dp(900).coerceAtMost(resources.displayMetrics.widthPixels - dp(120))
        val cardWidth = (panelWidth - dp(48) - dp(64)) / 4
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(12), dp(24), dp(20))
            clipChildren = false; clipToPadding = false
        }
        var first: View? = null
        var remembered: View? = null
        apps.filterNot { it.activityInfo.packageName in hiddenApps() }.chunked(4).forEach { group ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false
                setPadding(0, dp(20), 0, dp(20)); gravity = Gravity.CENTER_VERTICAL
            }
            group.forEach { info ->
                val tile = appTile(info, cardWidth)
                tile.tag = info.activityInfo.packageName
                tile.setFavourite(tile.tag in favourites())
                if (first == null) first = tile
                if (tile.tag == focusedId) remembered = tile
                tile.setOnClickListener { dialog?.dismiss(); launch(info) }
                tile.setOnLongClickListener {
                    appActions(info)
                    true
                }
                row.addView(tile, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8); rightMargin = dp(8) })
            }
            repeat(4 - group.size) { row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
            column.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        val scroll = ScrollView(this).apply {
            addView(column); isFillViewport = false; clipToPadding = false
            setPadding(0, dp(8), 0, dp(8)); isVerticalFadingEdgeEnabled = false
        }
        val wrapper = FrameLayout(this).apply { addView(scroll, FrameLayout.LayoutParams(-1, dp(370))) }
        showDialog(AlertDialog.Builder(this, R.style.CleanGlassDialogTheme).setTitle("All Apps").setMessage(if (first == null) "All apps are hidden. Use Hidden apps to show them again." else "Hold OK for favourites and hiding")
            .setView(wrapper).setNeutralButton("Hidden apps") { _, _ -> hiddenAppsMenu() }.setNegativeButton("Back", null))
        val target = remembered ?: first
        target?.post { target.requestFocus() }
    }

    private fun appActions(info: ResolveInfo) {
        val id = info.activityInfo.packageName
        showDialog(AlertDialog.Builder(this, R.style.CleanGlassDialogTheme).setTitle(label(info))
            .setItems(arrayOf(if (id in favourites()) "Remove from favourites" else "Add to favourites", "Hide app")) { _, which ->
                if (which == 1) {
                    setHidden(id, true)
                    toast("App hidden. Find it in All Apps → Hidden apps.")
                } else if (id !in favourites() && favourites().size >= 6) toast("Keep up to six favourites")
                else saveFavourites(if (id in favourites()) favourites() - id else favourites() + id)
                allApps(id)
            }.setNegativeButton("Back") { _, _ -> allApps(id) })
    }

    private fun hiddenAppsMenu() {
        val hidden = apps.filter { it.activityInfo.packageName in hiddenApps() }
        val builder = AlertDialog.Builder(this, R.style.CleanGlassDialogTheme).setTitle("Hidden apps")
        if (hidden.isEmpty()) builder.setMessage("No installed apps are hidden.")
        else builder.setItems(hidden.map { "Show ${label(it)}" }.toTypedArray()) { _, index ->
            val id = hidden[index].activityInfo.packageName
            setHidden(id, false)
            allApps(id)
        }
        showDialog(builder.setNegativeButton("Back") { _, _ -> allApps() })
    }

    private fun favouriteMenu(id: String) {
        val title = names[id] ?: apps.find { it.activityInfo.packageName == id }?.let { label(it) } ?: id
        showDialog(AlertDialog.Builder(this, R.style.CleanGlassDialogTheme).setTitle(title).setItems(arrayOf("Move left", "Move right", "Remove from favourites")) { _, which ->
            val installed = apps.map { it.activityInfo.packageName }.toSet()
            saveFavourites(when (which) { 0 -> Favourites.move(favourites(), id, -1, installed); 1 -> Favourites.move(favourites(), id, 1, installed); else -> favourites() - id })
        }.setNegativeButton("Back", null))
    }

    private fun inputs() {
        val manager = getSystemService(TvInputManager::class.java)
        val available = manager.tvInputList.filter { it.isPassthroughInput && it.type in listOf(TvInputInfo.TYPE_HDMI, TvInputInfo.TYPE_COMPOSITE, TvInputInfo.TYPE_COMPONENT) }
            .sortedBy { it.id }
        showDialog(AlertDialog.Builder(this, R.style.CleanGlassDialogTheme).setTitle("Inputs").setItems(available.map { it.loadLabel(this).toString() }.toTypedArray()) { _, index -> openInput(available[index]) }
            .setNegativeButton("Back", null))
    }

    private fun openInput(input: TvInputInfo) {
        val standard = Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input.id))
        if (standard.resolveActivity(packageManager) != null) {
            try { startActivity(standard); inputBanner.show(input.loadLabel(this).toString()); return } catch (_: RuntimeException) { toast("Input could not be opened"); return }
        }
        // This CVTE firmware identifies sources by the HW suffix, not HDMI order.
        if (android.os.Build.BOARD != "SK708D") { toast("Please use the TV's source menu"); return }
        val sourceId = InputRouting.sourceId(input.id) ?: run { toast("This input needs the TV's source menu"); return }
        val intent = Intent("com.cvte.intent.ACTION_TUNE_SOURCE_OR_CHANNEL")
            .setComponent(ComponentName("com.cvte.tv.api.impl", "com.cvte.tv.api.tif.TifReceiver"))
            .addCategory("com.cvte.intent.CATEGORY_TUNE_SOURCE_OR_CHANNEL").putExtra("sourceId", sourceId)
        try { sendBroadcast(intent); ui.postDelayed({ sendBroadcast(intent); inputBanner.show(input.loadLabel(this).toString()) }, 350) }
        catch (_: RuntimeException) { toast("Please use the remote's Input key") }
    }

    private fun settings() {
        try { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        catch (_: RuntimeException) { toast("TV settings could not be opened") }
    }

    private fun wallpaperMenu() {
        val paused = store.state().paused
        showDialog(AlertDialog.Builder(this, R.style.CleanGlassDialogTheme).setTitle("Wallpapers").setItems(arrayOf("Next wallpaper", if (paused) "Resume 30-minute rotation" else "Pause 30-minute rotation", "About this image", "About Clean Home")) { _, which ->
            when (which) {
                0 -> changeWallpaper(true)
                1 -> { val state = store.state(); store.save(state.copy(paused = !paused)); updateCaption(); toast(if (paused) "30-minute rotation resumed" else "Wallpaper paused") }
                2 -> imageInfo()
                3 -> aboutMenu()
            }
        }.setNegativeButton("Back", null))
    }

    private fun aboutMenu() {
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        showDialog(AlertDialog.Builder(this, R.style.CleanGlassDialogTheme).setTitle("Clean Home $version")
            .setMessage("Wallpaper changes every 30 minutes. Fresh Wikimedia Commons landscapes every two days while Home is visible. Images are saved locally, with 24 bundled fallback photos.\nNo accounts, advertising or analytics.\n\nHold OK on a favourite to rearrange it. Hold OK in All Apps for favourites and hiding.")
            .setNeutralButton("Reset TV") { _, _ -> resetPinMenu() }.setPositiveButton("Done", null))
    }

    private fun resetPinMenu() {
        if (restoring) return
        val spec = try { RecoveryConfig.read(this) } catch (error: Exception) {
            showDialog(AlertDialog.Builder(this, R.style.CleanGlassDialogTheme).setTitle("Restore unavailable")
                .setMessage(error.message).setPositiveButton("Done", null))
            return
        }
        val now = System.currentTimeMillis()
        val blocked = prefs.getLong("reset_blocked_until", 0L)
        if (blocked > now) {
            if (blocked - now > 60_000) prefs.edit().putLong("reset_blocked_until", now + 60_000).apply()
            toast("Too many attempts. Try again in a minute.")
            return
        }
        val pin = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(android.text.InputFilter.LengthFilter(6))
            contentDescription = "Reset PIN"; hint = "6-digit PIN"; isSingleLine = true
            setPadding(dp(24), dp(16), dp(24), dp(16))
        }
        showDialog(AlertDialog.Builder(this, R.style.CleanGlassDialogTheme).setTitle("Enter reset PIN").setView(pin)
            .setPositiveButton("Continue", null).setNegativeButton("Cancel", null))
        val active = dialog ?: return
        active.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val supplied = pin.text.toString().toCharArray()
            pin.text.clear()
            active.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            io.execute {
                val valid = try { ResetPin.matches(supplied, spec.getString("pinSalt"), spec.getString("pinHash")) }
                    finally { supplied.fill('\u0000') }
                ui.post {
                    if (dialog !== active || isDestroyed) return@post
                    active.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                    if (valid) {
                        prefs.edit().remove("reset_attempts").remove("reset_blocked_until").apply()
                        restoreMenu()
                    } else {
                        val attempts = prefs.getInt("reset_attempts", 0) + 1
                        prefs.edit().putInt("reset_attempts", if (attempts >= 5) 0 else attempts)
                            .putLong("reset_blocked_until", if (attempts >= 5) System.currentTimeMillis() + 60_000 else 0).apply()
                        if (attempts >= 5) { active.dismiss(); toast("Too many attempts. Try again in a minute.") }
                        else { pin.error = "Incorrect PIN"; pin.requestFocus() }
                    }
                }
            }
        }
        pin.requestFocus()
    }

    private fun restoreMenu() {
        if (restoring) return
        val scope = try { RecoveryConfig.read(this).optString("scope", "Restores the saved original Home, startup behaviour, remote-key routes and input-label permission.") }
            catch (error: Exception) { toast(error.message ?: "Restore is unavailable"); return }
        showDialog(AlertDialog.Builder(this, R.style.CleanGlassDialogTheme).setTitle("Restore original TV setup?")
            .setMessage("$scope\n\nYour installed apps and personal data stay on the TV. Clean Home is disabled. ADB must be enabled. The TV will restart.\n\nThis restores our recorded changes; it does not erase modification history or guarantee warranty eligibility.")
            .setPositiveButton("Restore and restart") { _, _ ->
                restoring = true
                inputBanner.close()
                toast("Restoring saved TV setup…")
                refreshIo.execute {
                    try {
                        val spec = RecoveryConfig.read(this)
                        LocalAdb.requireRoot()
                        LocalAdb.recovery(spec.getString("scriptSha256"), false)
                        LocalAdb.launchRecovery(spec.getString("scriptSha256"))
                    } catch (error: Exception) {
                        ui.post {
                            restoring = false
                            showDialog(AlertDialog.Builder(this, R.style.CleanGlassDialogTheme).setTitle("Restore did not complete")
                                .setMessage("${error.message}\n\nKeep the saved Mac project for recovery: tools/rollback.sh. Do not uninstall Clean Home until startup routing is restored.")
                                .setPositiveButton("Done", null))
                            ui.post(tick)
                        }
                    }
                }
            }.setNegativeButton("Cancel", null))
        dialog?.let { active ->
            val cancel = active.getButton(AlertDialog.BUTTON_NEGATIVE)
            val restore = active.getButton(AlertDialog.BUTTON_POSITIVE)
            cancel.nextFocusRightId = restore.id
            restore.nextFocusLeftId = cancel.id
            cancel.requestFocus()
        }
    }

    private fun imageInfo() {
        val image = store.catalogue.find { it.id == store.state().currentId } ?: return
        showDialog(AlertDialog.Builder(this, R.style.CleanGlassDialogTheme).setTitle(image.title).setMessage("${image.artist}\n\n${image.license}\n\n${image.attributionUrl}").setPositiveButton("Done", null))
    }

    private fun updateCaption() {
        val state = store.state()
        val image = store.catalogue.find { it.id == state.currentId }
        val title = image?.title ?: getString(R.string.empty_caption)
        caption.text = if (state.paused) getString(R.string.paused_caption, title) else title
    }

    private fun loadCurrent() {
        val selected = store.catalogue.find { it.id == store.state().currentId }
        val fallback = store.catalogue.first { it.group == "landscape" && store.local(it) }
        val initial = selected?.takeIf { store.local(it) } ?: fallback
        try {
            displayWallpaper(WallpaperRenderer.prepare(store.load(initial)))
            if (selected?.id != initial.id) store.save(rotation.commit(store.state(), initial, System.currentTimeMillis()))
        } catch (_: Exception) {
            val alternate = store.catalogue.first { it.id != initial.id }
            displayWallpaper(WallpaperRenderer.prepare(store.load(alternate)))
            store.save(rotation.commit(store.state(), alternate, System.currentTimeMillis()))
        }
        updateCaption()
    }

    private fun displayWallpaper(next: Bitmap) {
        wallpaperFade?.removeAllListeners()
        wallpaperFade?.cancel()
        outgoingBackground.setImageDrawable(null)
        fadingBitmap?.recycle()
        fadingBitmap = null
        val previousGlass = glassBackdrop
        glassBackdrop = GlassBackdrop.create(next)
        glassSurfaces.removeAll { reference -> reference.get()?.let { it.invalidateSelf(); false } ?: true }
        ui.postDelayed({ previousGlass?.recycle() }, 600)
        val previous = bitmap
        bitmap = next
        background.setImageBitmap(next)
        background.alpha = 1f
        if (previous == null || previous === next) return
        val duration = TvDesign.duration(this, TvDesign.WALLPAPER_FADE)
        if (duration == 0L || !visible) { previous.recycle(); return }
        fadingBitmap = previous
        outgoingBackground.setImageBitmap(previous)
        outgoingBackground.alpha = 1f
        wallpaperFade = ValueAnimator.ofFloat(1f, 0f).apply {
            this.duration = duration
            interpolator = TvDesign.ease
            addUpdateListener { outgoingBackground.alpha = it.animatedValue as Float }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    outgoingBackground.setImageDrawable(null)
                    fadingBitmap?.recycle()
                    fadingBitmap = null
                    wallpaperFade = null
                }
            })
            start()
        }
    }

    private fun changeWallpaper(manual: Boolean) {
        if (loading || !visible) return
        loading = true
        val token = generation
        val state = store.state()
        io.execute {
            var result: Pair<Wallpaper, Bitmap>? = null
            val excluded = mutableSetOf<String>()
            for (attempt in store.catalogue.indices) {
                val candidate = try { rotation.choose(state, excluded) } catch (_: IllegalStateException) { break }
                excluded += candidate.id
                try { result = candidate to WallpaperRenderer.prepare(store.load(candidate)); break }
                catch (error: Exception) { android.util.Log.w("CleanHome", "Wallpaper ${candidate.id}: ${error.javaClass.simpleName}") }
            }
            val loaded = result
            ui.post {
                if (token != generation || !visible || isDestroyed) { loaded?.second?.recycle(); return@post }
                loading = false
                if (loaded != null) {
                    store.save(rotation.commit(store.state(), loaded.first, System.currentTimeMillis()))
                    displayWallpaper(loaded.second)
                    updateCaption()
                    retryAfter = 0
                    if (manual) toast(loaded.first.title)
                } else {
                    retryAfter = SystemClock.elapsedRealtime() + 5 * 60 * 1000
                    if (manual) toast("Keeping the current wallpaper; no new image is available")
                }
            }
        }
    }

    private fun refreshWallpapers(now: Long) {
        if (refreshLoading || !RefreshPolicy.isDue(store.refreshedAt, now, visible, SystemClock.elapsedRealtime(), refreshRetryAfter)) return
        refreshLoading = true
        refreshIo.execute {
            val success = try {
                store.refresh(now) { visible && !restoring && !Thread.currentThread().isInterrupted }
                true
            } catch (error: Exception) {
                android.util.Log.w("CleanHome", "Keeping local wallpapers: ${error.javaClass.simpleName}: ${error.message}")
                false
            }
            ui.post {
                refreshLoading = false
                if (isDestroyed) return@post
                if (success) {
                    rotation = Rotation(store.rotationCatalogue)
                    refreshRetryAfter = 0
                } else refreshRetryAfter = SystemClock.elapsedRealtime() + RefreshPolicy.RETRY_MS
            }
        }
    }

    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
}
