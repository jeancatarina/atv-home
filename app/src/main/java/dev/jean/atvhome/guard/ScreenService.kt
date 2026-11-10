package dev.jean.atvhome.guard

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.google.mlkit.common.MlKit
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

/**
 * Reader 3: reads the top and bottom edges of the screen with OCR, for apps that show nothing to
 * the other two readers. Holds the screen-capture token in a foreground service (Android 10 wants
 * one of type mediaProjection), in the guard process.
 *
 * Smoothness: the mirror of the screen has no surface between looks, so the compositor does not
 * draw every frame of the film twice; each look attaches it, takes one frame and detaches it. The
 * OCR runs on one background-priority thread. Everything else runs on the main thread.
 */
class ScreenService : Service() {

    /** One OCR result. [seq] grows with each new read, so the caller can tell a new look from a repeat. */
    data class Shot(val seq: Long, val reading: Reading)

    private val handler = Handler(Looper.getMainLooper())
    private val ocrThread = Executors.newSingleThreadExecutor { task ->
        Thread({ Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND); task.run() }, "ad-guard-ocr")
    }
    private var recognizer: TextRecognizer? = null
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var images: ImageReader? = null
    private var frame: Bitmap? = null // the whole captured frame, reused between reads
    private var bands: Bitmap? = null // top and bottom bands stacked: what the OCR gets, reused
    private var busy = false          // from a look's start until its OCR is done
    private var waiting = false       // a look waits for its frame
    private var seq = 0L
    private var last: Shot? = null
    private var lastAt = 0L

    /**
     * No look for [IDLE_MS] (no video in front, or an app the screen is not needed for): the OCR
     * model, the frame buffers and the image queue go, so the guard process leaves the other apps
     * their memory. The next look brings them back in a few hundred ms.
     */
    private val trim = object : Runnable {
        override fun run() {
            if (busy) { handler.postDelayed(this, IDLE_MS); return }
            display?.surface = null
            images?.close(); images = null
            frame = null; bands = null
            recognizer?.close(); recognizer = null
        }
    }

    /** No frame came: nothing on screen moved, so the last read still holds. */
    private val noFrame = Runnable {
        if (!waiting) return@Runnable
        waiting = false
        detach()
        if (last != null) lastAt = SystemClock.elapsedRealtime()
        busy = false
    }

    val ready: Boolean get() = projection != null

    /** A look or its OCR is on its way: the next [latest] may bring news. */
    val pending: Boolean get() = busy

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // The ML Kit provider is removed from the manifest (it slowed the launcher's start): init here
        runCatching { MlKit.initialize(this) }.onFailure { Log.w(TAG, "mlkit", it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        foreground() // has to come before getMediaProjection on Android 10+
        @Suppress("DEPRECATION") val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
        if (data != null && projection == null) runCatching {
            val manager = getSystemService(MediaProjectionManager::class.java)
            projection = manager.getMediaProjection(intent.getIntExtra(EXTRA_CODE, 0), data)?.also {
                it.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() { stopSelf() }
                }, handler)
            }
        }.onFailure { Log.w(TAG, "projection", it) }
        if (projection == null) { stopSelf(); return START_NOT_STICKY }
        instance = this
        return START_NOT_STICKY
    }

    private fun foreground() {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Sem anúncios", NotificationManager.IMPORTANCE_MIN))
        val note = Notification.Builder(this, CHANNEL).setSmallIcon(applicationInfo.icon)
            .setContentTitle("Sem anúncios ligado").build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(1, note)
    }

    /** Reads the screen once; the result shows up in [latest] a few hundred ms later. */
    fun look() {
        val p = projection
        if (p == null || busy) return
        val reader = images ?: runCatching { mirror(p) }.onFailure { Log.w(TAG, "mirror", it) }.getOrNull() ?: return
        handler.removeCallbacks(trim)
        handler.postDelayed(trim, IDLE_MS)
        busy = true
        waiting = true
        reader.acquireLatestImage()?.close() // a frame left from before is old news
        display?.surface = reader.surface
        handler.postDelayed(noFrame, FRAME_WAIT_MS)
    }

    /**
     * The image queue comes and goes ([trim]); the mirror is made once and kept, because Android 14
     * allows one virtual display per capture token.
     */
    private fun mirror(p: MediaProjection): ImageReader {
        val reader = ImageReader.newInstance(W, H, PixelFormat.RGBA_8888, 2)
        reader.setOnImageAvailableListener({ r -> onImage(r) }, handler)
        val mirror = display
        if (mirror == null) {
            display = p.createVirtualDisplay(
                "ad-guard", W, H, 160, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                if (KEEP_MIRROR) reader.surface else null, null, handler,
            )
        } else if (KEEP_MIRROR) mirror.surface = reader.surface
        images = reader
        return reader
    }

    private fun recognizer(): TextRecognizer = recognizer
        ?: TextRecognition.getClient(TextRecognizerOptions.Builder().setExecutor(ocrThread).build()).also { recognizer = it }

    private fun onImage(reader: ImageReader) {
        val image = reader.acquireLatestImage() ?: return
        if (!waiting) { image.close(); return } // only with KEEP_MIRROR: frames nobody asked for
        waiting = false
        handler.removeCallbacks(noFrame)
        detach()
        val input = runCatching { stack(image) }.onFailure { Log.w(TAG, "frame", it) }.getOrNull()
        image.close()
        if (input == null) { busy = false; return }
        recognizer().process(InputImage.fromBitmap(input, 0))
            .addOnSuccessListener { text ->
                last = Shot(++seq, Reading(Source.SCREEN, labels = labels(text)))
                lastAt = SystemClock.elapsedRealtime()
            }
            .addOnFailureListener { Log.w(TAG, "ocr", it) }
            .addOnCompleteListener { busy = false }
    }

    /** Without a surface the mirror costs nothing: Android treats it like a screen that is off. */
    private fun detach() { if (!KEEP_MIRROR) display?.surface = null }

    /** The last read, or null when it is older than [FRESH_MS]. */
    fun latest(now: Long): Shot? = last?.takeIf { now - lastAt < FRESH_MS }

    /** No video in front: stop looking, keep the capture token and the mirror. */
    fun rest() {
        if (waiting) {
            waiting = false
            handler.removeCallbacks(noFrame)
            busy = false
        }
        detach()
        last = null
    }

    private fun stack(image: Image): Bitmap {
        val plane = image.planes[0]
        val rowPixels = plane.rowStride / plane.pixelStride // rows may be padded past W
        val full = frame?.takeIf { it.width == rowPixels }
            ?: Bitmap.createBitmap(rowPixels, H, Bitmap.Config.ARGB_8888).also { frame = it }
        full.copyPixelsFromBuffer(plane.buffer)
        val out = bands ?: Bitmap.createBitmap(W, TOP + BOTTOM, Bitmap.Config.ARGB_8888).also { bands = it }
        Canvas(out).apply {
            drawBitmap(full, Rect(0, 0, W, TOP), Rect(0, 0, W, TOP), null)
            drawBitmap(full, Rect(0, H - BOTTOM, W, H), Rect(0, TOP, W, TOP + BOTTOM), null)
        }
        return out
    }

    private fun labels(text: Text): List<Label> = text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
        val box = line.boundingBox ?: return@mapNotNull null
        // Undo the stacking: rows below TOP came from the bottom band of the screen
        val y = if (box.exactCenterY() < TOP) box.exactCenterY() else box.exactCenterY() - TOP + (H - BOTTOM)
        Label(line.text.trim(), box.exactCenterX() / W, y / H, box.width().toFloat() / W, box.height().toFloat() / H)
    }

    override fun onDestroy() {
        instance = null
        rest()
        display?.release(); display = null
        images?.close(); images = null
        projection?.stop(); projection = null
        handler.removeCallbacks(trim)
        recognizer?.close(); recognizer = null
        ocrThread.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AdGuard"
        private const val CHANNEL = "guard"
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"
        // 960×540 makes the badge too small for the OCR
        private const val W = 1280
        private const val H = 720
        private const val TOP = 180     // 25% of H
        private const val BOTTOM = 216  // 30% of H
        private const val FRAME_WAIT_MS = 400L
        private const val IDLE_MS = 30_000L
        private const val FRESH_MS = 3_000L
        /** Lever (docs/anuncios/SINAIS.md): true only if attaching the mirror per look stutters on the TV. */
        private const val KEEP_MIRROR = false

        /** Set in the guard process while the capture token is held. */
        var instance: ScreenService? = null
            private set

        /** Called from the launcher with the result of createScreenCaptureIntent. */
        fun start(context: Context, code: Int, data: Intent) {
            runCatching {
                context.startForegroundService(
                    Intent(context, ScreenService::class.java).putExtra(EXTRA_CODE, code).putExtra(EXTRA_DATA, data),
                )
            }.onFailure { Log.w(TAG, "start", it) }
        }

        /** Asked from the launcher process, where [instance] is always null. */
        @Suppress("DEPRECATION")
        fun running(context: Context): Boolean = runCatching {
            context.getSystemService(ActivityManager::class.java).getRunningServices(Int.MAX_VALUE)
                .any { it.service.className == ScreenService::class.java.name && it.started }
        }.getOrDefault(false)
    }
}
