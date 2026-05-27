package com.gte619n.healthfitness.feature.bodycomposition.detail

import com.gte619n.healthfitness.domain.bodycomposition.DexaRegion
import com.gte619n.healthfitness.domain.bodycomposition.DexaScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [withFieldPatched]. Verifies the path-string convention
 * used by the optimistic UI mirrors the backend's `UpdateFieldRequest`
 * shape — bare names for top-level fields, `region.field` for region
 * cells. Unknown paths are no-ops so a stale UI doesn't corrupt local
 * state when the server rejects.
 */
class WithFieldPatchedTest {

    @Test fun `top-level totalMassLb is patched`() {
        val before = emptyScan().copy(totalMassLb = 180.0)
        val after = before.withFieldPatched("totalMassLb", 182.5)
        assertEquals(182.5, after.totalMassLb!!, 0.001)
    }

    @Test fun `nested region field is patched`() {
        val before = emptyScan().copy(
            trunk = DexaRegion(totalMassLb = 90.0, leanTissueLb = 65.0, fatTissueLb = 25.0, regionFatPercent = 27.7),
        )
        val after = before.withFieldPatched("trunk.leanTissueLb", 66.5)
        assertEquals(66.5, after.trunk!!.leanTissueLb!!, 0.001)
        assertEquals(90.0, after.trunk!!.totalMassLb!!, 0.001)
    }

    @Test fun `nested region field on a null region creates an empty region`() {
        val before = emptyScan()
        assertNull(before.armsLeft)
        val after = before.withFieldPatched("armsLeft.fatTissueLb", 3.2)
        assertEquals(3.2, after.armsLeft!!.fatTissueLb!!, 0.001)
        assertNull(after.armsLeft!!.totalMassLb)
    }

    @Test fun `unknown top-level field is a no-op`() {
        val before = emptyScan().copy(totalMassLb = 180.0)
        val after = before.withFieldPatched("nonsense", 999.0)
        assertEquals(180.0, after.totalMassLb!!, 0.001)
    }

    @Test fun `unknown region path is a no-op`() {
        val before = emptyScan().copy(
            trunk = DexaRegion(totalMassLb = 90.0, leanTissueLb = null, fatTissueLb = null, regionFatPercent = null),
        )
        val after = before.withFieldPatched("trunk.unknown", 1.0)
        assertEquals(90.0, after.trunk!!.totalMassLb!!, 0.001)
    }

    @Test fun `restingMetabolicRateKcal accepts integer cast`() {
        val before = emptyScan().copy(restingMetabolicRateKcal = 1800)
        val after = before.withFieldPatched("restingMetabolicRateKcal", 1850.0)
        assertEquals(1850, after.restingMetabolicRateKcal)
    }

    @Test fun `null value clears the field`() {
        val before = emptyScan().copy(totalMassLb = 180.0)
        val after = before.withFieldPatched("totalMassLb", null)
        assertNull(after.totalMassLb)
    }

    private fun emptyScan() = DexaScan(
        scanId = "scan-1",
        measuredOn = null,
        sourceFacility = null,
        totalMassLb = null,
        leanTissueLb = null,
        fatTissueLb = null,
        totalBodyFatPercent = null,
        visceralFatLb = null,
        androidGynoidRatio = null,
        trunk = null, android = null, gynoid = null,
        armsTotal = null, armsRight = null, armsLeft = null,
        legsTotal = null, legsRight = null, legsLeft = null,
        bmdTScore = null, bmdZScore = null,
        restingMetabolicRateKcal = null,
    )
}
