package com.xmu.course

import com.xmu.course.ui.import.PageInspectionFormatter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DebugPageInspectionTest {
    @Test fun `页面结构只输出允许的统计值`() {
        val report = PageInspectionFormatter.format("""{"ready":"complete","body":true,"bodyVisible":false,"bodyChildren":3,"elements":35,"frames":1,"viewportWidth":390,"viewportHeight":700,"framePresent":true,"frameVisible":true,"frameWidth":200,"frameHeight":500,"frameReadable":true,"frameReady":"interactive","frameBody":true,"frameElements":12,"extra":"password ticket course secret"}""")
        assertTrue(report.contains("状态=完成"))
        assertTrue(report.contains("视口=390×700"))
        assertTrue(report.contains("文档可读取=是"))
        assertTrue(report.contains("元素总数=12"))
        assertFalse(report.contains("secret"))
        assertFalse(report.contains("password"))
    }

    @Test fun `格式异常和非数值不输出任意网页字符串`() {
        val report = PageInspectionFormatter.format("""{"ready":"secret","body":"secret","elements":"secret","centerTag":"secret","frameReady":"secret","frameWidth":-42}""")
        assertFalse(report.contains("secret"))
        assertTrue(report.contains("状态=未知"))
        assertTrue(report.contains("尺寸=0×未知"))
        assertEquals("页面结构检查不可用", PageInspectionFormatter.format("null"))
    }
}
