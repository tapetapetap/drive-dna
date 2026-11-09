package kr.skku.drivedna

import android.content.Context
import android.content.pm.ApplicationInfo

/** 운전 기록(data-model §7): SharedPreferences "records"의 키 drives 하나. 오래된 것이 앞 */
class Records(context: Context) {
    private val prefs = context.getSharedPreferences("records", Context.MODE_PRIVATE)

    fun all(): List<Metrics> = RecordFormat.decode(prefs.getString(KEY, null))

    /** 읽을 수 있는 줄 뒤에 붙이고 최신 100개만 남긴다 */
    fun add(m: Metrics) = prefs.edit().putString(KEY, RecordFormat.encode(RecordFormat.add(all(), m))).apply()

    private companion object { const val KEY = "drives" }
}

/** 시연용(디버그) 빌드는 보류·짧은 운전 기준을 낮춘다(research R4) */
fun Context.limits() = if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) Limits.DEMO else Limits.RELEASE
