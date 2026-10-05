package space.tejaswi.healthraw

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlin.reflect.KClass
import org.json.JSONArray
import org.json.JSONObject

data class RawPage(val records: List<Record>, val next: String?)
class RecordKind(val name: String, val permission: String, val read: suspend (HealthConnectClient, TimeRangeFilter, String?) -> RawPage)
private fun <T: Record> kind(type: KClass<T>) = RecordKind(
    type.simpleName!!.removeSuffix("Record").replace(Regex("([a-z])([A-Z])"), "$1 $2"),
    HealthPermission.getReadPermission(type)
) { client, range, token ->
    val page = client.readRecords(ReadRecordsRequest(type, range, ascendingOrder = false, pageSize = 100, pageToken = token))
    RawPage(page.records, page.pageToken?.takeIf { it.isNotBlank() })
}
val recordKinds = listOf(
    kind(StepsRecord::class),
    kind(HeartRateRecord::class),
    kind(RestingHeartRateRecord::class),
    kind(HeartRateVariabilityRmssdRecord::class),
    kind(SleepSessionRecord::class),
    kind(ExerciseSessionRecord::class),
    kind(DistanceRecord::class),
    kind(SpeedRecord::class),
    kind(PowerRecord::class),
    kind(CyclingPedalingCadenceRecord::class),
    kind(StepsCadenceRecord::class),
    kind(ActiveCaloriesBurnedRecord::class),
    kind(TotalCaloriesBurnedRecord::class),
    kind(BasalMetabolicRateRecord::class),
    kind(ElevationGainedRecord::class),
    kind(FloorsClimbedRecord::class),
    kind(WheelchairPushesRecord::class),
    kind(WeightRecord::class),
    kind(HeightRecord::class),
    kind(BodyFatRecord::class),
    kind(BodyWaterMassRecord::class),
    kind(BoneMassRecord::class),
    kind(LeanBodyMassRecord::class),
    kind(BloodGlucoseRecord::class),
    kind(BloodPressureRecord::class),
    kind(BodyTemperatureRecord::class),
    kind(BasalBodyTemperatureRecord::class),
    kind(OxygenSaturationRecord::class),
    kind(RespiratoryRateRecord::class),
    kind(Vo2MaxRecord::class),
    kind(HydrationRecord::class),
    kind(NutritionRecord::class),
    kind(MenstruationFlowRecord::class),
    kind(MenstruationPeriodRecord::class),
    kind(IntermenstrualBleedingRecord::class),
    kind(CervicalMucusRecord::class),
    kind(OvulationTestRecord::class),
    kind(SexualActivityRecord::class)
)

/** JSON representation of all public SDK properties, including metadata, units and sample arrays.
 * This is the SDK record representation, not an underlying database/protobuf dump. */
object RawJson {
    fun value(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is String, is Boolean, is Number -> value
        is Iterable<*> -> JSONArray().apply { value.forEach { put(value(it)) } }
        is java.time.temporal.TemporalAccessor -> value.toString()
        else -> {
            val getters = value.javaClass.methods.filter {
                it.parameterCount == 0 && it.name.startsWith("get") && it.name != "getClass" &&
                    !it.name.contains('$') && it.returnType != Void.TYPE
            }.sortedBy { it.name }
            if (!value.javaClass.name.startsWith("androidx.health.connect") || getters.isEmpty()) value.toString()
            else JSONObject().apply {
                getters.forEach { method ->
                    val key = method.name.removePrefix("get").replaceFirstChar { it.lowercase() }
                    put(key, value(method.invoke(value)))
                }
            }
        }
    }
    fun record(record: Record) = JSONObject().put("recordType", record.javaClass.simpleName).put("data", value(record))
}
