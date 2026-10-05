package space.tejaswi.healthraw

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.*

data class Entry(val id: String, val source: String, val json: String)
data class ExplorerState(
    val status: Int = HealthConnectClient.SDK_UNAVAILABLE,
    val granted: Set<String> = emptySet(), val historyAvailable: Boolean = false,
    val kind: Int = 0, val from: String = LocalDate.now().minusDays(7).toString(),
    val through: String = LocalDate.now().toString(), val entries: List<Entry> = emptyList(),
    val next: String? = null, val busy: Boolean = false, val message: String? = null,
    val queried: Boolean = false, val queryFrom: String = "", val queryThrough: String = ""
)
class ExplorerModel(app: Application): AndroidViewModel(app) {
    private val mutable = MutableStateFlow(ExplorerState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private fun client() = HealthConnectClient.getOrCreate(getApplication<Application>())
    fun refreshPermissions() {
        viewModelScope.launch {
            try {
                val status = HealthConnectClient.getSdkStatus(getApplication<Application>())
                val granted = if (status == HealthConnectClient.SDK_AVAILABLE) client().permissionController.getGrantedPermissions() else emptySet()
                val history = status == HealthConnectClient.SDK_AVAILABLE && client().features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
                val old = mutable.value
                mutable.value = old.copy(status = status, granted = granted, historyAvailable = history)
                if (!granted.contains(recordKinds[old.kind].permission) || (historyPermission in old.granted && historyPermission !in granted)) clear()
            } catch (e: Exception) { mutable.value = mutable.value.copy(message = "Cannot check Health Connect: ${e.message}") }
        }
    }
    fun choose(index: Int) { clear(); mutable.value = mutable.value.copy(kind = index) }
    fun dates(from: String, through: String) { clear(); mutable.value = mutable.value.copy(from = from, through = through) }
    private fun clear() { job?.cancel(); mutable.value = mutable.value.copy(entries = emptyList(), next = null, queried = false, busy = false, message = null) }
    fun load(more: Boolean = false) {
        if (mutable.value.busy) return
        val snapshot = mutable.value
        job = viewModelScope.launch {
            mutable.value = snapshot.copy(busy = true, message = null, entries = if (more) snapshot.entries else emptyList(), next = if (more) snapshot.next else null)
            try {
                val from = LocalDate.parse(snapshot.from)
                val through = LocalDate.parse(snapshot.through)
                require(!through.isBefore(from)) { "End date must be on or after start date." }
                require(!through.isAfter(LocalDate.now())) { "End date cannot be in the future." }
                val permissions = client().permissionController.getGrantedPermissions()
                if (recordKinds[snapshot.kind].permission !in permissions) throw SecurityException("Grant read access for this record type first.")
                val range = TimeRangeFilter.between(from.atStartOfDay(ZoneId.systemDefault()).toInstant(), through.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant())
                val page = withContext(Dispatchers.IO) {
                    val raw = recordKinds[snapshot.kind].read(client(), range, if (more) snapshot.next else null)
                    raw to raw.records.map { Entry(it.metadata.id, it.metadata.dataOrigin.packageName, RawJson.record(it).toString(2)) }
                }
                val combined = ((if (more) snapshot.entries else emptyList()) + page.second).distinctBy { it.id }
                mutable.value = mutable.value.copy(granted = permissions, entries = combined, next = page.first.next, busy = false, queried = true, queryFrom = snapshot.from, queryThrough = snapshot.through)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                mutable.value = mutable.value.copy(busy = false, message = when(e) {
                    is java.time.format.DateTimeParseException -> "Use dates in YYYY-MM-DD format."
                    is SecurityException -> "Read access is missing or this date range is outside allowed history. Grant access or choose a recent range."
                    else -> e.message ?: "Unable to read records. Please try again."
                }, entries = if (e is SecurityException) emptyList() else mutable.value.entries)
            }
        }
    }
    fun export(s: ExplorerState = mutable.value): String {
        return JSONObject().put("format", "health-raw-sdk-v1").put("exportedAt", Instant.now().toString())
            .put("recordType", recordKinds[s.kind].name).put("fromDate", s.queryFrom).put("throughDate", s.queryThrough)
            .put("zoneId", ZoneId.systemDefault().id).put("hasMorePages", s.next != null)
            .put("records", JSONArray().apply { s.entries.forEach { put(JSONObject(it.json)) } }).toString(2)
    }
    fun message(text: String) { mutable.value = mutable.value.copy(message = text) }
    val historyPermission get() = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
}
