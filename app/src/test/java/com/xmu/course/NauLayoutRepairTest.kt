package com.xmu.course

import com.xmu.course.ui.import.NauLayoutRepair
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NauLayoutRepairTest {
    @Test fun `只对学校教务 HTTPS 页面启用布局适配`() {
        assertTrue(NauLayoutRepair.isSchoolHttps("https://jwc.nau.edu.cn/path?ticket=redacted"))
        assertFalse(NauLayoutRepair.isSchoolHttps("http://jwc.nau.edu.cn/"))
        assertFalse(NauLayoutRepair.isSchoolHttps("https://sso.nau.edu.cn/"))
        assertFalse(NauLayoutRepair.isSchoolHttps("https://jwc.nau.edu.cn.example.org/"))
        assertFalse(NauLayoutRepair.isSchoolHttps(null))
    }
}
