package com.xmu.course.ui.import

import android.net.Uri
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** A narrow workaround for the zero-height EasyUI body observed on the NAU student page. */
internal object NauLayoutRepair {
    val script = """
        (function () {
          try {
            var body = document.body, root = document.documentElement, jq = window.jQuery;
            var width = window.innerWidth, height = window.innerHeight;
            if (window.location.protocol !== 'https:' || window.location.hostname !== 'jwc.nau.edu.cn' ||
                !body || !root || !document.getElementById('FrameContent') || document.readyState !== 'complete' ||
                !body.classList.contains('easyui-layout') || !jq || !jq.fn || typeof jq.fn.layout !== 'function' ||
                typeof jq.data !== 'function' || !jq.data(body, 'layout')) return 'not-matched';
            if (body.getBoundingClientRect().height > 1) return 'body-not-zero';
            if (!Number.isFinite(width) || !Number.isFinite(height) || width < 100 || height < 100 || width > 10000 || height > 10000) return 'invalid-viewport';
            var oldBody = body.style.getPropertyValue('height'), oldBodyPriority = body.style.getPropertyPriority('height');
            var oldRoot = root.style.getPropertyValue('height'), oldRootPriority = root.style.getPropertyPriority('height');
            var managedRoot = root.getBoundingClientRect().height <= 1;
            var busy = false, resizeHandler = null;
            try {
              function applySize(w, h) {
                if (managedRoot) root.style.setProperty('height', h + 'px', 'important');
                body.style.setProperty('height', h + 'px', 'important');
                jq(body).layout('resize', { width: w, height: h });
              }
              applySize(width, height);
              window.dispatchEvent(new Event('resize'));
              resizeHandler = function () {
                if (busy || document.body !== body || window.location.protocol !== 'https:' || window.location.hostname !== 'jwc.nau.edu.cn') return;
                var w = window.innerWidth, h = window.innerHeight;
                if (!Number.isFinite(w) || !Number.isFinite(h) || w < 100 || h < 100 || w > 10000 || h > 10000) return;
                busy = true;
                try { applySize(w, h); } catch (ignored) { } finally { busy = false; }
              };
              window.addEventListener('resize', resizeHandler);
              return 'height-adjusted';
            } catch (ignored) {
              if (resizeHandler) window.removeEventListener('resize', resizeHandler);
              if (oldBody) body.style.setProperty('height', oldBody, oldBodyPriority); else body.style.removeProperty('height');
              if (oldRoot) root.style.setProperty('height', oldRoot, oldRootPriority); else root.style.removeProperty('height');
              return 'failed';
            }
          } catch (ignored) { return 'failed'; }
        })()
    """.trimIndent()

    fun isSchoolHttps(url: String?): Boolean {
        val uri = runCatching { Uri.parse(url ?: "") }.getOrNull()
        return uri?.scheme.equals("https", true) && uri?.host.equals("jwc.nau.edu.cn", true)
    }

    suspend fun evaluate(view: WebView): String? = withTimeoutOrNull(2000) {
        suspendCancellableCoroutine { cont ->
            view.evaluateJavascript(script) { value -> if (cont.isActive) cont.resume(value) }
        }
    }
}

/** Cancelled on navigation and disposal; no network requests or credentials are involved. */
internal class NauLayoutRecovery(private val scope: CoroutineScope) {
    private var job: Job? = null

    fun start(view: WebView) {
        cancel()
        if (!NauLayoutRepair.isSchoolHttps(view.url)) return
        job = scope.launch {
            withTimeoutOrNull(12_000) {
                repeat(20) {
                    if (!isActive || !NauLayoutRepair.isSchoolHttps(view.url)) return@withTimeoutOrNull
                    val result = NauLayoutRepair.evaluate(view)
                    if (result == "\"height-adjusted\"") return@withTimeoutOrNull
                    delay(600)
                }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
