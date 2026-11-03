package com.xmu.course.ui.import

import android.net.Uri
import java.util.Locale

/** Only fixed categories and origin information are retained; raw URLs and console text never are. */
internal class WebDiagnostics(private val enabled: Boolean) {
    private val events = ArrayDeque<String>()
    private var lastMainOrigin: String? = null

    @Synchronized
    private fun add(event: String) {
        if (!enabled) return
        if (events.size == 100) events.removeFirst()
        events.addLast(event)
    }

    @Synchronized
    fun navigation(stage: String, url: String?) {
        if (!enabled) return
        val origin = safeOrigin(url)
        if (stage != "主页面导航") lastMainOrigin = origin
        add("$stage $origin")
    }
    fun resource(kind: String, url: String?, code: Int, main: Boolean) =
        add("$kind ${if (main) "主页面" else "子资源或 iframe"} ${safeOrigin(url)} 代码=$code")
    fun cleartextResource(url: String?) = add("HTTP 资源请求 ${safeOrigin(url)}（不代表已成功加载）")
    fun renderer(crashed: Boolean) = add(if (crashed) "渲染进程崩溃" else "渲染进程被系统终止")
    fun console(message: String, source: String?, line: Int, warning: Boolean = false) {
        if (!enabled) return
        val lower = message.lowercase(Locale.ROOT)
        val category = when {
            "mixed content" in lower -> "混合内容相关"
            "err_cleartext_not_permitted" in lower -> "明文连接被禁止"
            "content security policy" in lower -> "内容安全策略相关"
            "referenceerror" in lower -> "ReferenceError"
            "typeerror" in lower -> "TypeError"
            "syntaxerror" in lower -> "SyntaxError"
            "failed to load resource" in lower -> "资源加载失败"
            else -> "其他控制台错误"
        }
        if (warning && category != "混合内容相关" && category != "明文连接被禁止" && category != "内容安全策略相关") return
        add("控制台 $category ${safeOrigin(source)} 行=${line.coerceAtLeast(0)}（原文已隐藏）")
    }

    @Synchronized
    fun snapshot(): String = if (events.isEmpty()) "暂无诊断事件" else buildString {
        lastMainOrigin?.let { append("最近观察到的主页面来源：").append(it).append('\n') }
        append(events.joinToString("\n"))
    }

    companion object {
        /** Paths, ports, query, fragment, userinfo and unknown hostnames can contain secrets. */
        fun safeOrigin(url: String?): String {
            val uri = runCatching { Uri.parse(url ?: "") }.getOrNull()
            val scheme = uri?.scheme?.lowercase(Locale.ROOT)
            if (scheme != "http" && scheme != "https") return "[非 HTTP(S) 或未知地址]"
            val host = uri.host?.lowercase(Locale.ROOT)
            val safeHost = when (host) {
                "jwc.nau.edu.cn", "sso.nau.edu.cn" -> host
                null -> "[未知主机]"
                else -> if (host.endsWith(".nau.edu.cn")) "[其他南审主机]" else "[其他主机]"
            }
            return "$scheme://$safeHost"
        }
    }
}
