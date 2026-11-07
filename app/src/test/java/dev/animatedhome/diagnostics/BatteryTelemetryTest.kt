package dev.animatedhome.diagnostics

import org.junit.Assert.*
import org.junit.Test

class BatteryTelemetryTest {
    @Test fun computesHourlyDrainAndCpuSplitAccurately() {
        val r = BatteryTelemetry.computeRates(
            durationMs = 1_800_000L, // 30 minutes
            startLevelPct = 80.0,
            endLevelPct = 77.5,
            startChargeUah = 4_000_000L,
            endChargeUah = 3_875_000L, // 125 mAh drained in 0.5h -> 250 mAh/hr
            visibleMs = 600_000L,      // 10 min visible
            hiddenMs = 1_200_000L,     // 20 min hidden
            visibleCpuMs = 30_000L,    // 5% of 1 core while visible
            hiddenCpuMs = 600L         // 0.05% of 1 core while hidden
        )
        assertEquals(30.0, r.durationMinutes, 0.01)
        assertEquals(10.0, r.visibleMinutes, 0.01)
        assertEquals(20.0, r.hiddenMinutes, 0.01)
        assertEquals(2.5, r.batteryPctDelta, 0.01)
        assertEquals(125.0, r.batteryMahDelta, 0.01)
        assertEquals(5.0, r.estPctPerHour, 0.01)
        assertEquals(250.0, r.estMahPerHour, 0.01)
        assertEquals(5.0, r.visibleCpuPctOfOneCore, 0.01)
        assertEquals(0.05, r.hiddenCpuPctOfOneCore, 0.01)
    }

    @Test fun normalizesXiaomiMahChargeCounterToUah() {
        val r = BatteryTelemetry.computeRates(
            durationMs = 360_000L, // 6 minutes (0.1 hr)
            startLevelPct = 37.0,
            endLevelPct = 35.0,
            startChargeUah = 1831L, // Xiaomi reports 1831 mAh instead of 1,831,000 uAh
            endChargeUah = 1732L,   // 99 mAh drained
            visibleMs = 65_000L,
            hiddenMs = 295_000L,
            visibleCpuMs = 15_000L,
            hiddenCpuMs = 500L
        )
        assertEquals(99.0, r.batteryMahDelta, 0.01)
        assertEquals(990.0, r.estMahPerHour, 0.01)
    }

    @Test fun videoOptimizerScales4kAnd2kTo16Aligned720p() {
        assertEquals(1280 to 720, VideoOptimizer.targetDimensions(3840, 2160))
        assertEquals(1280 to 720, VideoOptimizer.targetDimensions(2560, 1440))
        assertEquals(720 to 1280, VideoOptimizer.targetDimensions(1440, 2560))
        assertEquals(640 to 368, VideoOptimizer.targetDimensions(640, 360))
    }
}
