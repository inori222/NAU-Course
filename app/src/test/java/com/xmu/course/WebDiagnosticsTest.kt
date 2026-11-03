package com.xmu.course

import com.xmu.course.ui.import.WebDiagnostics
import com.xmu.course.ui.import.WEB_DIAGNOSTICS_ENABLED
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebDiagnosticsTest {
    @Test fun `地址与控制台记录只包含固定类别和允许的来源`() {
        val recorder = WebDiagnostics(true)
        recorder.navigation("主页面完成", "https://user:password@jwc.nau.edu.cn/private/student?ticket=secret#token")
        recorder.console("TypeError: student secret in https://secret.example/path", "https://sso.nau.edu.cn/private?token=secret", 14)
        recorder.resource("网络错误", "http://secret.nau.edu.cn/student?ticket=secret", -10, false)
        val report = recorder.snapshot()
        assertTrue(report.contains("https://jwc.nau.edu.cn"))
        assertTrue(report.contains("https://sso.nau.edu.cn"))
        assertTrue(report.contains("[其他南审主机]"))
        assertTrue(report.contains("TypeError"))
        listOf("password", "private", "student", "ticket", "token", "secret", "user@").forEach { assertFalse(report.contains(it)) }
    }

    @Test fun `混合内容警告会记录但普通警告不会记录`() {
        val recorder = WebDiagnostics(true)
        recorder.console("some arbitrary personal text", "https://jwc.nau.edu.cn/private", 2, warning = true)
        assertEquals("暂无诊断事件", recorder.snapshot())
        recorder.console("Mixed Content: blocked http://secret.example", "https://jwc.nau.edu.cn/", 4, warning = true)
        assertTrue(recorder.snapshot().contains("混合内容相关"))
        assertFalse(recorder.snapshot().contains("secret"))
    }

    @Test fun `诊断关闭时不会记录且事件数量受限`() {
        assertTrue(WEB_DIAGNOSTICS_ENABLED)
        val disabled = WebDiagnostics(false)
        disabled.navigation("主页面完成", "https://jwc.nau.edu.cn/")
        assertEquals("暂无诊断事件", disabled.snapshot())
        val recorder = WebDiagnostics(true)
        repeat(120) { recorder.resource("网络错误", "http://jwc.nau.edu.cn/", it, false) }
        assertEquals(100, recorder.snapshot().lines().size)
        assertTrue(recorder.snapshot().lines().first().endsWith("代码=20"))
    }

    @Test fun `资源错误很多时仍保留最近主页面来源`() {
        val recorder = WebDiagnostics(true)
        recorder.navigation("主页面完成", "https://jwc.nau.edu.cn/private?ticket=secret")
        repeat(110) { recorder.resource("HTTP 错误", "https://jwc.nau.edu.cn/", 404, false) }
        assertTrue(recorder.snapshot().startsWith("最近观察到的主页面来源：https://jwc.nau.edu.cn\n"))
        assertFalse(recorder.snapshot().contains("secret"))
    }
}
