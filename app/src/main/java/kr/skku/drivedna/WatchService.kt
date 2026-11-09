package kr.skku.drivedna

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.util.Locale

/** 운전을 지켜보다 P가 되면 이번 운전을 판정·저장하고, 분석된 운전이면 음성·알림으로 한 번 알린다 */
class WatchService : Service() {

    companion object {
        private const val TAG = "WatchService"
        private const val CH_WATCH = "watch"
        private const val CH_RESULT = "result"
        private const val ID_WATCH = 1
        private const val ID_RESULT = 2

        // 아래 값은 같은 프로세스의 MainActivity가 읽는다(메인 스레드)

        /** 차 연결 실패(연결 문제 띠, contracts §6) */
        var noSignal = false
            private set

        /** 기어가 P가 아님(주행 중 화면) */
        var driving = false
            private set

        /** 지금 운전을 세는지. 운전 도중에 앱이 켜졌으면 false(그 운전은 기록하지 않는다) */
        var recording = false
            private set

        /** 화면이 열려 있을 때만 설정된다. 주행 상태·연결 문제·기록이 바뀔 때만 부른다 */
        var onUpdate: (() -> Unit)? = null

        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, WatchService::class.java))
    }

    private lateinit var records: Records
    private lateinit var watcher: CarWatcher
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        records = Records(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_WATCH, "운전 지켜보기", NotificationManager.IMPORTANCE_MIN))
        nm.createNotificationChannel(NotificationChannel(CH_RESULT, "이번 운전 DNA", NotificationManager.IMPORTANCE_HIGH))
        ServiceCompat.startForeground(
            this, ID_WATCH,
            notification(CH_WATCH, "운전을 지켜보는 중", "운전이 끝나면 이번 운전 DNA를 보여 드려요.", ongoing = true),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        startTts()
        watcher = CarWatcher(this, ::onChange, ::onDriveEnd, ::onNoSignal)
        watcher.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        if (::watcher.isInitialized) watcher.stop()
        runCatching { tts?.release() }
        driving = false; recording = false
        onUpdate?.invoke()
        super.onDestroy()
    }

    /** 연결되거나 기어가 바뀔 때(CarWatcher). 바뀐 것이 있을 때만 화면을 다시 그린다 */
    private fun onChange() {
        var changed = noSignal // 신호가 온다 = 연결됨
        noSignal = false
        val t = watcher.tracker
        if (driving != t.notParked || recording != t.driving) {
            driving = t.notParked; recording = t.driving; changed = true
            if (driving) getSystemService(NotificationManager::class.java).cancel(ID_RESULT) // "이번 운전" 알림이 지난 운전을 가리키지 않게
        }
        if (changed) onUpdate?.invoke()
    }

    /** 저장·발화만 한다. 화면 갱신은 바로 뒤 onChange(기어)가 한다 */
    private fun onDriveEnd(raw: Metrics) {
        val l = limits()
        val m = raw.copy(endedAt = System.currentTimeMillis(), analyzed = Judge.analyzed(raw, l))
        Log.i(TAG, "운전 끝: $m")
        records.add(m)
        if (!m.analyzed) return
        val code = Judge.code(m, l)
        val text = Types.speech(code)
        speak(text)
        getSystemService(NotificationManager::class.java)
            .notify(ID_RESULT, notification(CH_RESULT, "이번 운전 DNA: $code", text, ongoing = false))
    }

    private fun onNoSignal() {
        if (!noSignal) { noSignal = true; onUpdate?.invoke() } // 10초마다 재시도해도 다시 그리지 않는다
    }

    private fun startTts() {
        val t = TextToSpeech(applicationContext, Mode.ON_DEVICE)
        tts = t
        try {
            t.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    runCatching { t.setLocale(Locale.KOREA) }
                    ttsReady = true
                }
                override fun onFailure(error: ErrorType) {
                    Log.w(TAG, "TTS 초기화 실패: $error")
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "TTS 초기화 실패", e) // 알림과 화면은 그대로 남는다
        }
    }

    private fun speak(text: String) {
        if (!ttsReady) {
            Log.w(TAG, "TTS 준비 안 됨, 음성 생략")
            return
        }
        runCatching { tts?.speak(text, true) }.onFailure { Log.w(TAG, "TTS 발화 실패", it) }
    }

    private fun notification(channel: String, title: String, text: String, ongoing: Boolean): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_dna)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .build()
    }
}
