package kr.skku.drivedna

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
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
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.EvBatteryLevelListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

private const val TAG = "CarWatcher"
private const val RETRY_MILLIS = 10_000L
private const val LOCATION = "pleos.car.permission.FUSED_LOCATION"

/** 차 연결 실패 띠. 넣는 쪽과 지우는 쪽이 같은 값을 쓴다 */
val NO_SIGNAL = "차 신호를 받지 못하고 있어요 · ${RETRY_MILLIS / 1000}초마다 다시 연결해요"

/**
 * Pleos Vehicle SDK 신호 6개를 [tracker]에 넘기고 운전 끝을 알린다(첫 앱 CarWatcher와 같은 방식).
 * - SDK 콜백은 백그라운드 스레드에서 오므로 모든 처리는 메인 스레드로 옮긴다.
 * - `register*`는 구독 직후 현재값을 한 번 보내 준다. 끝까지 값이 한 번도 오지 않은 신호의 지표는 "없음"이다.
 */
class CarWatcher(
    context: Context,
    private val onChange: () -> Unit,
    private val onDriveEnd: (Metrics) -> Unit,
    private val onNoSignal: () -> Unit,
) {
    private val app = context.applicationContext
    private val vehicle = Vehicle(app)
    private var fused: FusedLocation? = null
    private var locating = false // 위치 켜기(재시도 포함)가 이미 돌고 있다
    val tracker = Tracker { SystemClock.elapsedRealtime() }
    private val main = Handler(Looper.getMainLooper())
    private val unregisters = mutableListOf<() -> Unit>() // 메인 스레드에서만 만진다
    private var stopped = false

    fun start() {
        if (!locating) { locating = true; watchLocation() } // Vehicle 재시도와 따로, 한 줄기만
        try {
            vehicle.initialize()
        } catch (e: Exception) {
            Log.w(TAG, "Vehicle 초기화 실패, ${RETRY_MILLIS / 1000}초 뒤 다시 시도", e)
            onNoSignal()
            main.postDelayed({ if (!stopped) start() }, RETRY_MILLIS) // 부팅 직후 차 서비스가 늦게 뜨는 경우
            return
        }
        onChange()
        // 하나가 실패해도 나머지는 지켜본다
        for ((what, watch) in listOf("기어" to ::watchGear, "주행거리" to ::watchOdometer, "브레이크" to ::watchBrake,
            "조향" to ::watchSteering, "방향지시등" to ::watchTurnSignal, "배터리" to ::watchBattery,
            "크루즈" to ::watchCruise, "배터리 SOC" to ::watchSoc, "와이퍼" to ::watchWiper, "조수석" to ::watchSeat)) {
            try { watch() } catch (e: Exception) { Log.w(TAG, "$what 구독 실패", e) }
        }
    }

    fun stop() {
        stopped = true
        main.removeCallbacksAndMessages(null)
        unregisters.forEach { runCatching(it) }
        unregisters.clear()
        runCatching { vehicle.release() }
        runCatching { fused?.release() }
    }

    private fun ui(block: () -> Unit) {
        main.post { if (!stopped) block() }
    }

    private fun failed(what: String): (Exception) -> Unit = { e -> Log.i(TAG, "$what 일시 실패: ${e.message}") }

    private fun watchGear() {
        val gear = vehicle.getGear()
        val l = object : GearSelectionListener {
            override fun onSelected(g: VehicleGear?) = ui {
                if (g == null || g == VehicleGear.GEAR_UNKNOWN) return@ui
                val wasDriving = tracker.driving
                tracker.gear(g == VehicleGear.GEAR_PARK, g == VehicleGear.GEAR_REVERSE)?.let(onDriveEnd)
                if (!wasDriving && tracker.driving) readStart()
                onChange()
            }
            override fun onFailed(e: Exception) = failed("기어")(e)
        }
        gear.registerGearSelection(l)
        unregisters += { gear.unregisterGearSelection(l) }
    }

    private fun watchOdometer() {
        val odometer = vehicle.getOdometer()
        val l = object : DistanceDrivenListener {
            override fun onDistanceDrivenUpdated(v: Float?) { v?.let { ui { tracker.km(it.toDouble()) } } }
            override fun onFailed(e: Exception) = failed("주행거리")(e)
        }
        odometer.registerDistanceDriven(l)
        unregisters += { odometer.unregisterDistanceDriven(l) }
    }

    private fun watchBrake() {
        val brake = vehicle.getBrake()
        val l = object : BrakePedalStatusListener {
            override fun onBrakePedalStatusUpdated(v: Boolean?) { v?.let { ui { tracker.brake(it) } } }
            override fun onFailed(e: Exception) = failed("브레이크")(e)
        }
        brake.registerBrakePedalStatus(l)
        unregisters += { brake.unregisterBrakePedalStatus(l) }
    }

    private fun watchSteering() {
        val wheel = vehicle.getSteeringWheel()
        val l = object : SteeringWheelAngleListener {
            override fun onSteeringWheelAngleUpdated(v: Float?) { v?.let { ui { tracker.steer(it.toDouble()) } } }
            override fun onFailed(e: Exception) = failed("조향")(e)
        }
        wheel.registerSteeringWheelAngle(l)
        unregisters += { wheel.unregisterSteeringWheelAngle(l) }
    }

    private fun watchTurnSignal() {
        val signal = vehicle.getTurnSignal()
        val l = object : TurnSignalStateListener {
            override fun onTurnSignalStateUpdated(v: TurnSignalValue?) {
                v?.let { ui { tracker.turn(when (it) { TurnSignalValue.LEFT -> -1; TurnSignalValue.RIGHT -> 1; else -> 0 }) } }
            }
            override fun onFailed(e: Exception) = failed("방향지시등")(e)
        }
        signal.registerTurnSignalState(l)
        unregisters += { signal.unregisterTurnSignalState(l) }
    }

    /** 운전 시작 때 SOC·착좌·크루즈를 한 번 더 읽는다(구독 첫 값이 엇갈리거나 늦을 때, research R8) */
    private fun readStart() {
        runCatching { vehicle.getEvBattery().getEvBatteryStateOfCharge({ v -> v?.let { ui { tracker.soc(it, start = true) } } }, failed("SOC")) }
        runCatching { vehicle.getSeat().getCurrentSeatOccupancy(SeatArea(SeatValue.ROW_1_RIGHT), { v -> v?.let { ui { tracker.seat(it, start = true) } } }, failed("조수석")) }
        runCatching { vehicle.getDrivingMode().getCruiseControlState({ s -> s?.let { ui { tracker.cruise(it == CruiseControl.ACTIVATED) } } }, failed("크루즈")) }
    }

    private fun watchCruise() {
        val mode = vehicle.getDrivingMode()
        val l = object : CruiseControlStateListener {
            // null = 크루즈 준비 안 됨(값 없음), ACTIVATED = 작동, 그 밖 = 대기
            override fun onCruiseControlStateUpdated(state: CruiseControl?) = ui { tracker.cruise(state?.let { it == CruiseControl.ACTIVATED }) }
            override fun onFailed(e: Exception) = failed("크루즈")(e)
        }
        mode.registerCruiseControlState(l)
        unregisters += { mode.unregisterCruiseControlState(l) }
    }

    private fun watchSoc() {
        val battery = vehicle.getEvBattery()
        val l = object : EvBatteryStateOfChargeListener {
            override fun onEvBatteryStateOfChargeUpdated(value: Int?) { value?.let { ui { tracker.soc(it) } } }
            override fun onFailed(e: Exception) = failed("배터리 SOC")(e)
        }
        battery.registerEvBatteryStateOfCharge(l)
        unregisters += { battery.unregisterEvBatteryStateOfCharge(l) }
    }

    private fun watchWiper() {
        val wiper = vehicle.getWiper()
        val area = WindowArea(WindowValue.FRONT_WINDSHIELD)
        val l = object : WiperStateListener {
            override fun onWiperStateUpdated(state: WiperState?) { state?.let { ui { tracker.wiper(it == WiperState.ON) } } }
            override fun onFailed(e: Exception) = failed("와이퍼")(e)
        }
        wiper.registerWiperState(area, l)
        unregisters += { wiper.unregisterWiperState(area, l) }
    }

    private fun watchSeat() {
        val seat = vehicle.getSeat()
        val area = SeatArea(SeatValue.ROW_1_RIGHT)
        val l = object : SeatOccupancyListener {
            override fun onSeatOccupancyUpdated(occupied: Boolean?) { occupied?.let { ui { tracker.seat(it) } } }
            override fun onFailed(e: Exception) = failed("조수석")(e)
        }
        seat.registerSeatOccupancy(area, l)
        unregisters += { seat.unregisterSeatOccupancy(area, l) }
    }

    /** 끝난 곳 한 점만 쓴다(헌법 VI). 같은편 PickupService.watchLocation과 같은 모양 */
    private fun watchLocation() {
        val retry = { main.postDelayed({ if (!stopped) watchLocation() }, RETRY_MILLIS) }
        // 첫 실행에는 권한 창이 떠 있는 동안 서비스가 먼저 뜬다. 허용되면 다음 확인에서 켠다(거부면 위치 없는 운전)
        if (app.checkSelfPermission(LOCATION) != PackageManager.PERMISSION_GRANTED) return Unit.also { retry() }
        val f = FusedLocation(app)
        if (!f.isSupported()) return Unit.also { Log.w(TAG, "FusedLocation을 지원하지 않는 환경, 위치 없이 동작") }
        fused = f
        try {
            f.initialize()
            f.registerFusedLocationCallback(object : FusedLocationListener {
                override fun onChanged(data: LocationData?) { data?.let { ui { tracker.place(it.latitude, it.longitude) } } }
                override fun onError(e: Exception) { Log.i(TAG, "위치 일시 실패: ${e.message}") }
            })
        } catch (e: Exception) {
            Log.w(TAG, "위치 초기화 실패, ${RETRY_MILLIS / 1000}초 뒤 다시 시도", e)
            runCatching { f.release() }
            fused = null
            retry()
        }
    }

    private fun watchBattery() {
        val battery = vehicle.getEvBattery()
        val l = object : EvBatteryLevelListener {
            override fun onEvBatteryLevelUpdated(v: Float?) { v?.let { ui { tracker.batteryWh(it.toDouble()) } } }
            override fun onFailed(e: Exception) = failed("배터리")(e)
        }
        battery.registerEvBatteryLevel(l)
        unregisters += { battery.unregisterEvBatteryLevel(l) }
    }
}
