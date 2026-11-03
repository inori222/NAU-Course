package com.xmu.course.ui.import

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xmu.course.parser.NauCourseParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.coroutines.resume

/** 学校公开登录首页，不包含用户会话参数。 */
const val NAU_JW_URL = "https://jwc.nau.edu.cn/"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(onBack: () -> Unit, onImported: () -> Unit = onBack, viewModel: ImportViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsState()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var isCleartextPage by remember { mutableStateOf(false) }
    var rendererGone by remember { mutableStateOf(false) }
    val diagnostics = remember { WebDiagnostics(WEB_DIAGNOSTICS_ENABLED) }
    val layoutRecovery = remember(scope) { NauLayoutRecovery(scope) }
    var diagnosticReport by remember { mutableStateOf<String?>(null) }
    var layoutAttempt by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current

    diagnosticReport?.let { report ->
        AlertDialog(
            onDismissRequest = { diagnosticReport = null },
            title = { Text("WebView 调试诊断") },
            text = { Text("仅显示协议、已知主机和错误类别；路径、参数及控制台原文均已隐藏。\n\n$report", modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { clipboard.setText(AnnotatedString(report)); diagnosticReport = null }) { Text("复制诊断") } },
            dismissButton = { TextButton(onClick = { diagnosticReport = null }) { Text("关闭") } },
        )
    }

    BackHandler { if (canGoBack) webView?.goBack() else onBack() }
    LaunchedEffect(state.message, state.importCompleted) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.messageShown() }
        if (state.importCompleted) { viewModel.importConsumed(); onImported() }
    }
    state.conflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = viewModel::cancelOverwrite,
            title = { Text("该学期已存在课程，是否覆盖？") },
            text = { Text("${conflict.semester.name}：已有 ${conflict.existingCount} 门，新导入 ${conflict.newCount} 门。" + if (conflict.warnings.isEmpty()) "" else "\n" + conflict.warnings.joinToString("\n")) },
            confirmButton = { Button(onClick = viewModel::confirmOverwrite) { Text("覆盖") } },
            dismissButton = { TextButton(onClick = viewModel::cancelOverwrite) { Text("取消") } },
        )
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth()) {
                TextButton(onClick = { if (canGoBack) webView?.goBack() else onBack() }) { Text("返回") }
                TextButton(onClick = { pageError = null; if (rendererGone) rendererGone = false else webView?.reload() }) { Text("重新加载") }
                TextButton(enabled = !saving && !state.isImporting, onClick = {
                    val current = webView ?: return@TextButton
                    if (!viewModel.isParserConfigured) {
                        scope.launch { snackbar.showSnackbar(NauCourseParser.ADAPTATION_MESSAGE) }
                        return@TextButton
                    }
                    saving = true
                    scope.launch {
                        try {
                            val html = withTimeoutOrNull(12_000) {
                                var ready: String? = null
                                while (ready == null && webView === current) {
                                    val candidate = evaluateDom(current)
                                    if (candidate != null && viewModel.isTimetableReady(candidate)) ready = candidate
                                    else delay(1200)
                                }
                                ready
                            }
                            if (html == null) snackbar.showSnackbar("课表尚未加载或无法读取课表 iframe，请进入我的课表后重试")
                            else viewModel.saveCourseHtml(html)
                        } finally { saving = false }
                    }
                }) { if (saving || state.isImporting) CircularProgressIndicator(Modifier.padding(4.dp)) else Text("导入课表") }
            }
            if (WEB_DIAGNOSTICS_ENABLED) {
              Row {
                TextButton(onClick = {
                    val current = webView
                    scope.launch {
                        val inspection = if (current == null) "WebView 不可用" else try {
                            DebugPageInspection.inspect(current)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            "页面结构检查不可用"
                        }
                        diagnosticReport = diagnostics.snapshot() + "\n\n" + inspection + (layoutAttempt?.let { "\n$it" } ?: "")
                    }
                }) { Text("查看调试诊断") }
                TextButton(enabled = webView != null, onClick = {
                    val current = webView ?: return@TextButton
                    scope.launch {
                        layoutAttempt = try {
                            DebugPageInspection.tryResize(current)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            "布局试验：执行失败"
                        }
                        snackbar.showSnackbar("已完成布局试验，请观察页面并查看调试诊断")
                    }
                }) { Text("尝试恢复布局") }
              }
            }
            if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            if (isCleartextPage) {
                Text(
                    "当前网页使用 HTTP 明文连接，账号、密码和登录信息可能被窃取或篡改。请仅在可信网络使用。",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            pageError?.let { Text(it, modifier = Modifier.padding(8.dp)) }
            if (!rendererGone) AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    @Suppress("DEPRECATION")
                    settings.savePassword = false
                    settings.saveFormData = false
                    WebSessionManager.attach(this)
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }
                        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                            val level = message.messageLevel()
                            if (level == ConsoleMessage.MessageLevel.ERROR || level == ConsoleMessage.MessageLevel.WARNING) {
                                diagnostics.console(message.message(), message.sourceId(), message.lineNumber(), level == ConsoleMessage.MessageLevel.WARNING)
                            }
                            // Do not forward raw page console text to application logs.
                            return true
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            if (request?.isForMainFrame == true) diagnostics.navigation("主页面导航", request.url.toString())
                            return false
                        }
                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                            if (request?.url?.scheme.equals("http", ignoreCase = true)) diagnostics.cleartextResource(request?.url.toString())
                            return null
                        }
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            layoutRecovery.cancel()
                            layoutAttempt = null
                            diagnostics.navigation("主页面开始", url)
                            isCleartextPage = url?.startsWith("http://", ignoreCase = true) == true
                            pageError = null
                        }
                        override fun onPageFinished(view: WebView?, url: String?) {
                            diagnostics.navigation("主页面完成", url)
                            if (view != null && view.url == url) layoutRecovery.start(view)
                            canGoBack = view?.canGoBack() == true
                            WebSessionManager.flushCookies()
                        }
                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                            diagnostics.navigation("主页面历史更新", url)
                            canGoBack = view?.canGoBack() == true
                            WebSessionManager.flushCookies()
                        }
                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                            diagnostics.resource("网络错误", request?.url?.toString(), error?.errorCode ?: 0, request?.isForMainFrame == true)
                            if (request?.isForMainFrame == true) pageError = "页面加载失败，请检查网络后重新加载"
                        }
                        override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, response: WebResourceResponse?) {
                            diagnostics.resource("HTTP 错误", request?.url?.toString(), response?.statusCode ?: 0, request?.isForMainFrame == true)
                            if (request?.isForMainFrame == true) pageError = "页面返回 HTTP 错误，请稍后重试"
                        }
                        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                            diagnostics.resource("TLS 证书错误", error?.url, error?.primaryError ?: 0, false)
                            pageError = "网页资源的安全证书验证失败，已停止加载该资源"
                            handler?.cancel()
                        }
                        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                            layoutRecovery.cancel()
                            diagnostics.renderer(detail?.didCrash() == true)
                            if (webView === view) webView = null
                            canGoBack = false
                            rendererGone = true
                            pageError = "网页渲染进程已退出，请点击重新加载"
                            return true
                        }
                    }
                    loadUrl(NAU_JW_URL)
                    webView = this
                }
            }, onRelease = {
                layoutRecovery.cancel()
                if (webView === it) webView = null
                WebSessionManager.flushCookies()
                if (!rendererGone) it.stopLoading()
                it.destroy()
            })
        }
    }
}

private suspend fun evaluateDom(webView: WebView): String? = suspendCancellableCoroutine { cont ->
    webView.evaluateJavascript(NauDomCapture.script) { result ->
        if (cont.isActive) cont.resume(runCatching {
            JSONObject("""{"h":$result}""").opt("h").takeIf { it is String } as? String
        }.getOrNull())
    }
}
