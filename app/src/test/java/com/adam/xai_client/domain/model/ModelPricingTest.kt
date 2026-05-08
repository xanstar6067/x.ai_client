package com.adam.xai_client.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelPricingTest {
    @Test
    fun `formats image prices from xai usd ticks`() {
        assertEquals("$0.02", 200_000_000.toUsdPerImage())
        assertEquals("$0.04", 400_000_000.toUsdPerImage())
        assertEquals("$0.07", 700_000_000.toUsdPerImage())
    }
}
