package com.demomart.register.money

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {
    @Test fun formats() {
        assertEquals("€23.00", formatEur(2300))
        assertEquals("€0.05", formatEur(5))
        assertEquals("-€8.40", formatEur(-840))
    }
}
