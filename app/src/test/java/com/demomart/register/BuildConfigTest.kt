package com.demomart.register

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildConfigTest {
    @Test
    fun debugDefaults() {
        assertEquals("fake", BuildConfig.AUTH_MODE)
        assertEquals("R1", BuildConfig.REGISTER_ID)
        assertTrue(BuildConfig.SERVER_URL.endsWith("/"))
    }
}
