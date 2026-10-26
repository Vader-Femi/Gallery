package com.femi.gallery.domain.label

import org.junit.Assert.assertTrue
import org.junit.Test

class SceneTaggerTest {

    @Test
    fun testTagScreenshot() {
        val tags = SceneTagger.tag("Screenshot_2026.png", "Pictures/Screenshots/", "", emptySet(), 2400, 1080)
        assertTrue("Expected screenshot in $tags", tags.contains("screenshot"))
    }

    @Test
    fun testTagFood() {
        val tags = SceneTagger.tag("IMG_food_lunch.jpg", "DCIM/Camera/", "", setOf("orange"), 1000, 1000)
        assertTrue("Expected food in $tags", tags.contains("food"))
    }
}
