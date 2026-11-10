package com.demomart.register.sale

import org.junit.Assert.assertEquals
import org.junit.Test

class RedeemPresetsTest {
    private fun points(max: Long) = redeemPresets(max).map { it.second }

    @Test fun allPresetsBelowMax() = assertEquals(listOf(0L, 100L, 500L, 1300L), points(1300))

    @Test fun dropsPresetsAtOrAboveMax() {
        assertEquals(listOf(0L, 100L, 500L), points(500))
        assertEquals(listOf(0L, 100L), points(100))
    }

    @Test fun labels() = assertEquals(listOf("None", "€1.00", "€5.00", "Max"), redeemPresets(1300).map { it.first })
}
