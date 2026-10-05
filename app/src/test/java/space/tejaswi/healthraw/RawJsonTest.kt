package space.tejaswi.healthraw

import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import org.junit.Test
import org.junit.Assert.*
import java.time.Instant
import java.time.ZoneOffset

class RawJsonTest {
    private val start = Instant.parse("2026-10-01T10:00:00Z")
    @Test fun exportsOriginalValuesAndOffsets() {
        val record = StepsRecord(start, ZoneOffset.ofHoursMinutes(5,30), start.plusSeconds(60), ZoneOffset.ofHoursMinutes(5,30), 123, Metadata.manualEntry())
        val json = RawJson.record(record)
        assertEquals("StepsRecord", json.getString("recordType"))
        val data = json.getJSONObject("data")
        assertEquals(123, data.getLong("count"))
        assertEquals(start.toString(), data.getString("startTime"))
        assertEquals("+05:30", data.getString("startZoneOffset"))
        assertTrue(data.has("metadata"))
        assertTrue(data.getJSONObject("metadata").has("dataOrigin"))
    }
    @Test fun preservesEachHeartRateSample() {
        val record = HeartRateRecord(start, null, start.plusSeconds(60), null,
            listOf(HeartRateRecord.Sample(start.plusSeconds(1), 71), HeartRateRecord.Sample(start.plusSeconds(2), 85)), Metadata.manualEntry())
        val samples = RawJson.record(record).getJSONObject("data").getJSONArray("samples")
        assertEquals(2, samples.length())
        assertEquals(71, samples.getJSONObject(0).getLong("beatsPerMinute"))
        assertEquals(start.plusSeconds(2).toString(), samples.getJSONObject(1).getString("time"))
    }
}
