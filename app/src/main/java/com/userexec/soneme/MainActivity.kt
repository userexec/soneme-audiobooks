package com.userexec.soneme

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.DocumentsContract
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors
import kotlin.math.ceil

class MainActivity : Activity() {
    private lateinit var db: SonemeDatabase
    private lateinit var scanner: LibraryScanner
    private lateinit var listView: ListView
    private lateinit var listAdapter: LibraryAdapter
    private lateinit var tabBar: View
    private lateinit var playerScroll: View
    private lateinit var tabBooks: TextView
    private lateinit var tabRecents: TextView
    private lateinit var tabQueue: TextView
    private lateinit var tabPlayer: TextView

    private lateinit var playerTitle: TextView
    private lateinit var playerArtist: TextView
    private lateinit var elapsedText: TextView
    private lateinit var remainingText: TextView
    private lateinit var progressSummary: TextView
    private lateinit var positionWiper: SeekBar
    private lateinit var playPauseButton: Button
    private lateinit var previousButton: Button
    private lateinit var nextButton: Button
    private lateinit var rewindButton: Button
    private lateinit var forwardButton: Button
    private lateinit var sleepButton: Button
    private lateinit var repeatButton: Button
    private lateinit var speedButton: Button

    private val executor = Executors.newSingleThreadExecutor()
    private val uiHandler = Handler(Looper.getMainLooper())
    private var service: PlaybackService? = null
    private var serviceBound = false
    private var startupDone = false
    private var currentView = AppView.BOOKS
    private val folderStack = ArrayDeque<FolderLocation>()
    private var booksEntries: List<LibraryEntry> = emptyList()
    private var visibleSources: List<SourceFolder> = emptyList()

    private var wiperHoldRunnable: Runnable? = null
    private var wiperHoldActive = false
    private var wiperWasPlaying = false

    private val uiTicker = object : Runnable {
        override fun run() {
            if (currentView == AppView.PLAYER) updatePlayerUi()
            uiHandler.postDelayed(this, 500L)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as PlaybackService.PlaybackBinder).service()
            serviceBound = true
            if (!startupDone) {
                startupDone = true
                chooseStartupView()
            }
            updatePlayerUi()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            service = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        db = SonemeDatabase(this)
        scanner = LibraryScanner(this, db)
        bindViews()
        bindUiActions()
        bindService(Intent(this, PlaybackService::class.java), connection, Context.BIND_AUTO_CREATE)
        uiHandler.post(uiTicker)
    }

    override fun onDestroy() {
        uiHandler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
        if (serviceBound) unbindService(connection)
        db.close()
        super.onDestroy()
    }

    private fun bindViews() {
        tabBar = findViewById(R.id.tabBar)
        listView = findViewById(R.id.mainList)
        playerScroll = findViewById(R.id.playerScroll)
        tabBooks = findViewById(R.id.tabBooks)
        tabRecents = findViewById(R.id.tabRecents)
        tabQueue = findViewById(R.id.tabQueue)
        tabPlayer = findViewById(R.id.tabPlayer)
        playerTitle = findViewById(R.id.playerTitle)
        playerArtist = findViewById(R.id.playerArtist)
        elapsedText = findViewById(R.id.elapsedText)
        remainingText = findViewById(R.id.remainingText)
        progressSummary = findViewById(R.id.progressSummary)
        positionWiper = findViewById(R.id.positionWiper)
        playPauseButton = findViewById(R.id.playPauseButton)
        previousButton = findViewById(R.id.previousButton)
        nextButton = findViewById(R.id.nextButton)
        rewindButton = findViewById(R.id.rewindButton)
        forwardButton = findViewById(R.id.forwardButton)
        sleepButton = findViewById(R.id.sleepButton)
        repeatButton = findViewById(R.id.repeatButton)
        speedButton = findViewById(R.id.speedButton)
        listAdapter = LibraryAdapter(this)
        listView.adapter = listAdapter
    }

    private fun bindUiActions() {
        tabBooks.setOnClickListener { showView(AppView.BOOKS) }
        tabRecents.setOnClickListener { showView(AppView.RECENTS) }
        tabQueue.setOnClickListener { showView(AppView.QUEUE) }
        tabPlayer.setOnClickListener { showView(AppView.PLAYER) }

        listView.setOnItemClickListener { _, _, position, _ ->
            when (currentView) {
                AppView.BOOKS -> openBookEntry(listAdapter.item(position))
                AppView.RECENTS -> playSelectedRecord(listAdapter.item(position), replaceQueue = true)
                AppView.QUEUE -> playSelectedRecord(listAdapter.item(position), replaceQueue = false)
                AppView.SOURCES -> Unit
                AppView.PLAYER -> Unit
            }
        }

        playPauseButton.setOnClickListener {
            ensureNotificationPermission()
            if (service?.isPlaying() != true) ensureForegroundPlaybackService()
            service?.togglePlayPause()
            updatePlayerUi()
        }
        previousButton.setOnClickListener { service?.previousQueueTitle() }
        nextButton.setOnClickListener { service?.nextQueueTitle() }
        rewindButton.setOnClickListener { service?.seekRelative(-(service?.rewindIntervalMs() ?: 10_000L)) }
        forwardButton.setOnClickListener { service?.seekRelative(service?.forwardIntervalMs() ?: 10_000L) }
        rewindButton.setOnLongClickListener { showIntervalDialog(rewind = true); true }
        forwardButton.setOnLongClickListener { showIntervalDialog(rewind = false); true }
        sleepButton.setOnClickListener { showSleepDialog() }
        repeatButton.setOnClickListener { showRepeatDialog() }
        speedButton.setOnClickListener { showSpeedDialog() }
    }

    private fun chooseStartupView() {
        val recent = db.recents().firstOrNull()
        val queued = db.queueRecords().firstOrNull()
        when {
            recent != null -> {
                service?.load(recent.uri, resumeSaved = true, autoplay = false)
                showView(AppView.PLAYER)
            }
            queued != null -> {
                service?.load(queued.uri, resumeSaved = true, autoplay = false)
                showView(AppView.PLAYER)
            }
            else -> showView(AppView.BOOKS, refreshBooks = true)
        }
    }

    private fun showView(view: AppView, refreshBooks: Boolean = false) {
        currentView = view
        tabBar.visibility = if (view == AppView.SOURCES) View.GONE else View.VISIBLE
        listView.visibility = if (view == AppView.PLAYER) View.GONE else View.VISIBLE
        playerScroll.visibility = if (view == AppView.PLAYER) View.VISIBLE else View.GONE
        updateTabAppearance()

        when (view) {
            AppView.BOOKS -> {
                if (refreshBooks || booksEntries.isEmpty()) loadBooksAsync()
                else {
                    booksEntries = booksEntries.map(::refreshProgress)
                    showList(booksEntries)
                }
            }
            AppView.RECENTS -> showList(db.recents().map(::recordEntry))
            AppView.QUEUE -> showList(db.queueRecords().map(::recordEntry))
            AppView.PLAYER -> {
                updatePlayerUi()
                playerScroll.requestFocus()
            }
            AppView.SOURCES -> showSources()
        }
        invalidateOptionsMenu()
    }

    private fun updateTabAppearance() {
        val active = Color.rgb(79, 111, 143)
        val inactive = Color.rgb(217, 222, 227)
        val activeText = Color.WHITE
        val inactiveText = Color.rgb(26, 26, 26)
        val pairs = listOf(
            tabBooks to AppView.BOOKS,
            tabRecents to AppView.RECENTS,
            tabQueue to AppView.QUEUE,
            tabPlayer to AppView.PLAYER
        )
        pairs.forEach { (tab, view) ->
            val selected = currentView == view
            tab.setBackgroundColor(if (selected) active else inactive)
            tab.setTextColor(if (selected) activeText else inactiveText)
        }
    }

    private fun loadBooksAsync() {
        listAdapter.replace(emptyList())
        executor.execute {
            try {
                val entries = if (folderStack.isEmpty()) scanner.scanTopLevel() else scanner.scanFolder(folderStack.last())
                runOnUiThread {
                    booksEntries = entries
                    if (currentView == AppView.BOOKS) showList(entries)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Unable to scan sources: ${e.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
                    booksEntries = emptyList()
                    if (currentView == AppView.BOOKS) showList(emptyList())
                }
            }
        }
    }

    private fun showList(entries: List<LibraryEntry>) {
        listAdapter.replace(entries)
        listView.requestFocus()
        if (entries.isNotEmpty()) listView.setSelection(0)
    }

    private fun openBookEntry(entry: LibraryEntry?) {
        entry ?: return
        if (entry.isFolder) {
            val source = entry.source ?: return
            val documentId = entry.documentId ?: return
            folderStack.addLast(FolderLocation(source, documentId, entry.title))
            loadBooksAsync()
        } else {
            playSelectedRecord(entry, replaceQueue = true)
        }
    }

    private fun playSelectedRecord(entry: LibraryEntry?, replaceQueue: Boolean) {
        val uri = entry?.uri ?: return
        if (replaceQueue) db.replaceQueue(uri) else db.addToQueue(uri)
        ensureNotificationPermission()
        ensureForegroundPlaybackService()
        service?.load(uri, resumeSaved = true, autoplay = true)
        showView(AppView.PLAYER)
    }

    private fun refreshProgress(entry: LibraryEntry): LibraryEntry {
        val uri = entry.uri ?: return entry
        val state = db.getAudio(uri) ?: return entry
        val percent = if (state.durationMs > 0 && state.positionMs > 0) {
            ((state.positionMs * 100) / state.durationMs).toInt().coerceIn(0, 100)
        } else null
        return entry.copy(progressPercent = percent, durationMs = state.durationMs)
    }

    private fun recordEntry(record: AudioRecord): LibraryEntry {
        val percent = if (record.durationMs > 0 && record.positionMs > 0) {
            ((record.positionMs * 100) / record.durationMs).toInt().coerceIn(0, 100)
        } else null
        return LibraryEntry(
            isFolder = false,
            title = record.title,
            subtitle = record.artist,
            durationMs = record.durationMs,
            progressPercent = percent,
            uri = record.uri
        )
    }

    private fun showSources() {
        visibleSources = db.sources()
        showList(visibleSources.map {
            LibraryEntry(isFolder = true, title = it.name, subtitle = it.uri)
        })
    }

    private fun addSource() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_SOURCE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_SOURCE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return

        for (existing in db.sources()) {
            when (SourceUtils.relationship(uri, Uri.parse(existing.uri))) {
                SourceUtils.Relationship.SAME -> {
                    Toast.makeText(this, "Already a source", Toast.LENGTH_SHORT).show()
                    return
                }
                SourceUtils.Relationship.INSIDE_EXISTING -> {
                    Toast.makeText(this, "Already inside a source", Toast.LENGTH_SHORT).show()
                    return
                }
                SourceUtils.Relationship.DISTINCT -> Unit
            }
        }

        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            Toast.makeText(this, "Unable to retain folder access", Toast.LENGTH_LONG).show()
            return
        }

        val name = sourceDisplayName(uri)
        if (!db.addSource(uri.toString(), name)) {
            Toast.makeText(this, "Already a source", Toast.LENGTH_SHORT).show()
            return
        }
        folderStack.clear()
        booksEntries = emptyList()
        showSources()
    }

    private fun sourceDisplayName(treeUri: Uri): String {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return "Source"
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return runCatching {
            contentResolver.query(docUri, projection, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: docId.substringAfterLast('/').substringAfter(':').ifBlank { "Source" }
    }

    private fun removeSelectedSource() {
        val position = listView.selectedItemPosition
        val source = visibleSources.getOrNull(position) ?: return
        db.removeSource(source.id)
        folderStack.clear()
        booksEntries = emptyList()
        showSources()
    }

    private fun queueSelectedBook() {
        val entry = listAdapter.item(listView.selectedItemPosition) ?: return
        val uri = entry.uri ?: return
        db.addToQueue(uri)
        Toast.makeText(this, "Added to queue", Toast.LENGTH_SHORT).show()
    }

    private fun updatePlayerUi() {
        val playback = service
        val record = playback?.currentRecord()
        playerTitle.text = record?.title ?: "No title loaded"
        playerArtist.text = record?.artist ?: ""

        val duration = playback?.durationMs() ?: record?.durationMs ?: 0L
        val position = playback?.positionMs() ?: record?.positionMs ?: 0L
        elapsedText.text = Formatters.clock(position)
        remainingText.text = "-${Formatters.clock((duration - position).coerceAtLeast(0L))}"
        positionWiper.progress = if (duration > 0) ((position * 1000) / duration).toInt().coerceIn(0, 1000) else 0
        val percent = if (duration > 0) ((position * 100) / duration).toInt().coerceIn(0, 100) else 0
        progressSummary.text = "${Formatters.words(duration)} - $percent%"
        playPauseButton.text = if (playback?.isPlaying() == true) "Pause" else "Play"

        val queueEnabled = (playback?.queueSize() ?: 0) > 1
        previousButton.isEnabled = queueEnabled
        nextButton.isEnabled = queueEnabled

        val rewindMs = playback?.rewindIntervalMs() ?: 10_000L
        val forwardMs = playback?.forwardIntervalMs() ?: 10_000L
        rewindButton.text = "◀ ${Formatters.interval(rewindMs)}"
        forwardButton.text = "${Formatters.interval(forwardMs)} ▶"

        repeatButton.text = when (playback?.repeatMode() ?: RepeatMode.OFF) {
            RepeatMode.OFF -> "Repeat Off"
            RepeatMode.ONE -> "Repeat 1"
            RepeatMode.ALL -> "Repeat All"
        }
        speedButton.text = formatSpeed(playback?.playbackSpeed() ?: 1f)
        val sleepRemaining = playback?.sleepRemainingMs() ?: 0L
        sleepButton.text = if (sleepRemaining <= 0) "Sleep Off" else "Sleep ${formatSleep(sleepRemaining)}"
    }

    private fun formatSleep(ms: Long): String {
        val minutes = ceil(ms / 60_000.0).toLong().coerceAtLeast(1L)
        val hours = minutes / 60
        val remainder = minutes % 60
        return "$hours:${remainder.toString().padStart(2, '0')}"
    }

    private fun formatSpeed(speed: Float): String = if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"

    private fun showSleepDialog() {
        val labels = arrayOf("Off", "10 minutes", "30 minutes", "1 hour", "2 hours", "3 hours", "4 hours", "8 hours", "12 hours")
        val minutes = intArrayOf(0, 10, 30, 60, 120, 180, 240, 480, 720)
        AlertDialog.Builder(this)
            .setTitle("Sleep")
            .setItems(labels) { dialog, which ->
                service?.setSleepMinutes(minutes[which])
                dialog.dismiss()
                updatePlayerUi()
            }
            .show()
    }

    private fun showRepeatDialog() {
        val labels = arrayOf("Off", "Repeat 1", "Repeat All")
        AlertDialog.Builder(this)
            .setTitle("Repeat")
            .setItems(labels) { dialog, which ->
                service?.setRepeatMode(RepeatMode.entries[which])
                dialog.dismiss()
                updatePlayerUi()
            }
            .show()
    }

    private fun showSpeedDialog() {
        val labels = arrayOf("0.5x", "0.75x", "1x", "1.25x", "1.5x", "1.75x", "2x", "3x", "4x")
        val speeds = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f, 4f)
        AlertDialog.Builder(this)
            .setTitle("Playback speed")
            .setItems(labels) { dialog, which ->
                service?.setPlaybackSpeed(speeds[which])
                dialog.dismiss()
                updatePlayerUi()
            }
            .show()
    }

    private fun showIntervalDialog(rewind: Boolean) {
        val labels = arrayOf("10 seconds", "1 minute", "10 minutes", "1 hour")
        val values = longArrayOf(10_000L, 60_000L, 600_000L, 3_600_000L)
        AlertDialog.Builder(this)
            .setTitle(if (rewind) "Rewind interval" else "Fast-forward interval")
            .setItems(labels) { dialog, which ->
                if (rewind) service?.setRewindIntervalMs(values[which]) else service?.setForwardIntervalMs(values[which])
                dialog.dismiss()
                updatePlayerUi()
            }
            .show()
    }

    private fun showControlsDialog() {
        val controls = arrayOf(
            "1 — Rewind 10 seconds",
            "2 — Previous queue title",
            "3 — Fast-forward 10 seconds",
            "4 — Rewind 60 seconds",
            "5 — Next queue title",
            "6 — Fast-forward 60 seconds",
            "7 — Rewind 10 minutes",
            "8 — Cycle repeat",
            "9 — Fast-forward 10 minutes",
            "* — Rewind 1 hour",
            "0 — Add 10 minutes to sleep timer",
            "# — Fast-forward 1 hour"
        )
        AlertDialog.Builder(this).setTitle("Controls").setItems(controls, null).show()
    }

    private fun showOptionsMenuForHardwareKey() {
        invalidateOptionsMenu()
        openOptionsMenu()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean = true

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.clear()
        when (currentView) {
            AppView.BOOKS -> {
                menu.add(0, MENU_SOURCES, 0, "Sources")
                menu.add(0, MENU_REFRESH, 1, "Refresh")
                val entry = listAdapter.item(listView.selectedItemPosition)
                if (entry != null && !entry.isFolder) menu.add(0, MENU_QUEUE, 2, "Queue")
            }
            AppView.RECENTS -> menu.add(0, MENU_CLEAR_RECENTS, 0, "Clear")
            AppView.QUEUE -> menu.add(0, MENU_CLEAR_QUEUE, 0, "Clear")
            AppView.PLAYER -> {
                menu.add(0, MENU_CONTROLS, 0, "Controls")
                menu.add(0, MENU_PLAY_PAUSE, 1, if (service?.isPlaying() == true) "Pause" else "Play")
                menu.add(0, MENU_SLEEP, 2, "Sleep")
            }
            AppView.SOURCES -> {
                menu.add(0, MENU_ADD_SOURCE, 0, "Add")
                if (visibleSources.isNotEmpty()) menu.add(0, MENU_REMOVE_SOURCE, 1, "Remove")
            }
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            MENU_SOURCES -> showView(AppView.SOURCES)
            MENU_REFRESH -> { booksEntries = emptyList(); loadBooksAsync() }
            MENU_QUEUE -> queueSelectedBook()
            MENU_CLEAR_RECENTS -> { db.clearRecents(); showView(AppView.RECENTS) }
            MENU_CLEAR_QUEUE -> { db.clearQueue(); showView(AppView.QUEUE) }
            MENU_CONTROLS -> showControlsDialog()
            MENU_PLAY_PAUSE -> service?.togglePlayPause()
            MENU_SLEEP -> showSleepDialog()
            MENU_ADD_SOURCE -> addSource()
            MENU_REMOVE_SOURCE -> removeSelectedSource()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_MENU || event.keyCode == KeyEvent.KEYCODE_SOFT_LEFT) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) showOptionsMenuForHardwareKey()
            return true
        }

        if (currentView == AppView.PLAYER && handlePlayerKey(event)) return true

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && currentView != AppView.PLAYER && currentView != AppView.SOURCES) {
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                switchTab(-1)
                return true
            }
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                switchTab(1)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handlePlayerKey(event: KeyEvent): Boolean {
        if (positionWiper.hasFocus() && (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            return handleWiperKey(event)
        }

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && isTabFocused()) {
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) { switchTab(-1); return true }
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) { switchTab(1); return true }
        }

        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return false
        when (event.keyCode) {
            KeyEvent.KEYCODE_1 -> service?.seekRelative(-10_000L)
            KeyEvent.KEYCODE_2 -> service?.previousQueueTitle()
            KeyEvent.KEYCODE_3 -> service?.seekRelative(10_000L)
            KeyEvent.KEYCODE_4 -> service?.seekRelative(-60_000L)
            KeyEvent.KEYCODE_5 -> service?.nextQueueTitle()
            KeyEvent.KEYCODE_6 -> service?.seekRelative(60_000L)
            KeyEvent.KEYCODE_7 -> service?.seekRelative(-600_000L)
            KeyEvent.KEYCODE_8 -> service?.cycleRepeat()
            KeyEvent.KEYCODE_9 -> service?.seekRelative(600_000L)
            KeyEvent.KEYCODE_STAR -> service?.seekRelative(-3_600_000L)
            KeyEvent.KEYCODE_0 -> service?.addSleepMinutes(10)
            KeyEvent.KEYCODE_POUND -> service?.seekRelative(3_600_000L)
            else -> return false
        }
        updatePlayerUi()
        return true
    }

    private fun handleWiperKey(event: KeyEvent): Boolean {
        val direction = if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount > 0) return true
                val playback = service ?: return true
                val interval = if (direction < 0) playback.rewindIntervalMs() else playback.forwardIntervalMs()
                playback.seekRelative(direction * interval)
                wiperWasPlaying = playback.isPlaying()
                wiperHoldActive = false
                val runnable = object : Runnable {
                    override fun run() {
                        val current = service ?: return
                        if (!wiperHoldActive) {
                            wiperHoldActive = true
                            if (wiperWasPlaying) current.pause()
                        }
                        val step = if (direction < 0) current.rewindIntervalMs() else current.forwardIntervalMs()
                        current.seekRelative(direction * step)
                        uiHandler.postDelayed(this, 1000L)
                    }
                }
                wiperHoldRunnable = runnable
                uiHandler.postDelayed(runnable, 700L)
                return true
            }
            KeyEvent.ACTION_UP -> {
                wiperHoldRunnable?.let(uiHandler::removeCallbacks)
                wiperHoldRunnable = null
                if (wiperHoldActive && wiperWasPlaying) service?.play()
                wiperHoldActive = false
                wiperWasPlaying = false
                return true
            }
        }
        return true
    }

    private fun isTabFocused(): Boolean = tabBooks.hasFocus() || tabRecents.hasFocus() || tabQueue.hasFocus() || tabPlayer.hasFocus()

    private fun switchTab(direction: Int) {
        val tabs = listOf(AppView.BOOKS, AppView.RECENTS, AppView.QUEUE, AppView.PLAYER)
        val normalized = if (currentView == AppView.SOURCES) AppView.BOOKS else currentView
        val index = tabs.indexOf(normalized)
        val target = (index + direction).coerceIn(0, tabs.lastIndex)
        if (target != index) showView(tabs[target])
    }

    @Deprecated("Deprecated in Android; retained for target flip-phone navigation semantics")
    override fun onBackPressed() {
        when (currentView) {
            AppView.BOOKS -> {
                if (folderStack.isNotEmpty()) {
                    folderStack.removeLast()
                    loadBooksAsync()
                }
            }
            AppView.RECENTS -> showView(AppView.BOOKS)
            AppView.QUEUE -> showView(AppView.RECENTS)
            AppView.PLAYER -> showView(AppView.QUEUE)
            AppView.SOURCES -> showView(AppView.BOOKS)
        }
    }

    private fun ensureForegroundPlaybackService() {
        startForegroundService(Intent(this, PlaybackService::class.java).setAction(PlaybackService.ACTION_KEEP_ALIVE))
    }

    private fun ensureNotificationPermission() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    companion object {
        private const val REQUEST_SOURCE = 40
        private const val REQUEST_NOTIFICATIONS = 41
        private const val MENU_SOURCES = 100
        private const val MENU_REFRESH = 101
        private const val MENU_QUEUE = 102
        private const val MENU_CLEAR_RECENTS = 103
        private const val MENU_CLEAR_QUEUE = 104
        private const val MENU_CONTROLS = 105
        private const val MENU_PLAY_PAUSE = 106
        private const val MENU_SLEEP = 107
        private const val MENU_ADD_SOURCE = 108
        private const val MENU_REMOVE_SOURCE = 109
    }
}
