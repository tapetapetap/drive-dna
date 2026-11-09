package kr.skku.drivedna

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 차가 켜지면(부팅) 사용자가 앱을 열지 않아도 운전을 지켜본다 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) WatchService.start(context)
    }
}
