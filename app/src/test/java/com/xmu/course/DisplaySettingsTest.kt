package com.xmu.course

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.DisplaySettings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DisplaySettingsTest {
    @Test fun sectionCountPersistsAndStaysWithinRange() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val old = DisplaySettings.sectionCount.value
        try {
            DisplaySettings.setSectionCount(context, 8)
            assertEquals(8, DisplaySettings.sectionCount.value)
            DisplaySettings.load(context)
            assertEquals(8, DisplaySettings.sectionCount.value)
            DisplaySettings.setSectionCount(context, 50)
            assertEquals(13, DisplaySettings.sectionCount.value)
        } finally {
            DisplaySettings.setSectionCount(context, old)
        }
    }
}
