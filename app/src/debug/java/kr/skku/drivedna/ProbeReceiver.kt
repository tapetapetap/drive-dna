package kr.skku.drivedna

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.CruiseControl
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.CruiseControlStateListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log

/** 디버그 전용 측정: am broadcast -n kr.skku.drivedna/.ProbeReceiver --ei sec 40 → 그동안 신호를 로그로 남긴다 */
class ProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val done = goAsync()
        val v = Vehicle(context.applicationContext)
        fun log(s: String) { Log.i("Probe", s) }
        runCatching {
            v.initialize()
            val dm = v.getDrivingMode()
            dm.getCruiseControlState({ log("크루즈 $it") }, { log("크루즈 실패 ${it.message}") })
            v.getEvBattery().getEvBatteryStateOfCharge({ log("SOC $it") }, { log("SOC 실패 ${it.message}") })
            v.getWiper().getCurrentWiperState(WindowArea(WindowValue.FRONT_WINDSHIELD), { log("와이퍼 $it") }, { log("와이퍼 실패 ${it.message}") })
            v.getSeat().getCurrentSeatOccupancy(SeatArea(SeatValue.ROW_1_RIGHT), { log("조수석 $it") }, { log("조수석 실패 ${it.message}") })
        }.onFailure { log("초기화 실패 $it") }
        Handler(Looper.getMainLooper()).postDelayed({ runCatching { v.release() }; log("끝"); done.finish() }, intent.getIntExtra("sec", 30).coerceIn(1, 50) * 1000L) // 백그라운드 브로드캐스트 60초 제한 안
    }
}
