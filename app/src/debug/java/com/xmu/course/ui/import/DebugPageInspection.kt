package com.xmu.course.ui.import

import android.webkit.WebView
import android.net.Uri
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Only fixed structural measurements; no page text, attribute values, URLs or HTML are returned. */
internal object DebugPageInspection {
    internal val script = """
        (function () {
          function visible(el) {
            if (!el) return false;
            var s = getComputedStyle(el), r = el.getBoundingClientRect();
            return s.display !== 'none' && s.visibility === 'visible' && Number(s.opacity) > 0 && r.width > 0 && r.height > 0;
          }
          function state(doc) {
            var s = doc && doc.readyState;
            return s === 'loading' || s === 'interactive' || s === 'complete' ? s : 'unknown';
          }
          try {
            var body = document.body, frame = document.getElementById('FrameContent');
            var root = document.documentElement;
            var rootRect = root ? root.getBoundingClientRect() : null;
            var links = document.getElementsByTagName('link'), stylesheetLinks = 0, stylesheetWithoutSheet = 0;
            for (var i = 0; i < links.length; i++) {
              if (links[i].relList && links[i].relList.contains('stylesheet')) {
                stylesheetLinks++;
                if (!links[i].sheet) stylesheetWithoutSheet++;
              }
            }
            var bodyStyle = body ? getComputedStyle(body) : null;
            var bodyRect = body ? body.getBoundingClientRect() : null;
            var center = document.elementFromPoint(window.innerWidth / 2, window.innerHeight / 2);
            var tag = center && center.tagName ? center.tagName.toUpperCase() : '';
            if (['HTML', 'BODY', 'DIV', 'IFRAME', 'CANVAS', 'IMG'].indexOf(tag) < 0) tag = 'OTHER';
            var out = {
              ready: state(document), body: !!body, bodyVisible: visible(body),
              bodyDisplayNone: !!bodyStyle && bodyStyle.display === 'none',
              bodyVisibilityHidden: !!bodyStyle && bodyStyle.visibility !== 'visible',
              bodyOpacityZero: !!bodyStyle && Number(bodyStyle.opacity) <= 0,
              bodyWidth: bodyRect ? Math.round(bodyRect.width) : 0,
              bodyHeight: bodyRect ? Math.round(bodyRect.height) : 0,
              rootHeight: rootRect ? Math.round(rootRect.height) : 0,
              rootClientHeight: root ? root.clientHeight : 0,
              rootPanelFit: !!root && root.classList.contains('panel-fit'),
              bodyEasyui: !!body && body.classList.contains('easyui-layout'),
              styleSheetCount: document.styleSheets.length,
              stylesheetLinks: stylesheetLinks, stylesheetWithoutSheet: stylesheetWithoutSheet,
              centerTag: tag,
              bodyChildren: body ? body.children.length : 0,
              elements: document.getElementsByTagName('*').length,
              frames: document.getElementsByTagName('iframe').length,
              viewportWidth: window.innerWidth, viewportHeight: window.innerHeight,
              framePresent: !!frame, frameVisible: visible(frame), frameReadable: false,
              frameWidth: 0, frameHeight: 0, frameReady: 'unknown', frameBody: false,
              frameElements: 0, frameInViewport: false, frameAncestorsVisible: false,
              easyuiLayoutReady: !!(body && window.jQuery && window.jQuery.fn && typeof window.jQuery.fn.layout === 'function' && typeof window.jQuery.data === 'function' && window.jQuery.data(body, 'layout'))
            };
            if (frame) {
              var rect = frame.getBoundingClientRect();
              out.frameWidth = Math.round(rect.width); out.frameHeight = Math.round(rect.height);
              out.frameInViewport = rect.right > 0 && rect.bottom > 0 && rect.left < window.innerWidth && rect.top < window.innerHeight;
              out.frameAncestorsVisible = true;
              for (var parent = frame.parentElement; parent; parent = parent.parentElement) {
                var style = getComputedStyle(parent);
                if (style.display === 'none' || style.visibility !== 'visible' || Number(style.opacity) <= 0) { out.frameAncestorsVisible = false; break; }
              }
              try {
                var doc = frame.contentDocument;
                if (doc) {
                  out.frameReadable = true; out.frameReady = state(doc);
                  out.frameBody = !!doc.body;
                  out.frameElements = doc.getElementsByTagName('*').length;
                }
              } catch (ignored) { }
            }
            return out;
          } catch (ignored) { return null; }
        })()
    """.trimIndent()

    suspend fun inspect(view: WebView): String {
        val native = "WebView 尺寸=${view.width}×${view.height}，显示=${view.isShown}，硬件加速=${view.isHardwareAccelerated}"
        val result = withTimeoutOrNull(4000) {
            suspendCancellableCoroutine { cont ->
                view.evaluateJavascript(script) { value -> if (cont.isActive) cont.resume(value) }
            }
        }
        return native + "\n" + PageInspectionFormatter.format(result)
    }

    suspend fun tryResize(view: WebView): String {
        val uri = runCatching { Uri.parse(view.url ?: "") }.getOrNull()
        if (uri?.scheme != "https" || uri.host != "jwc.nau.edu.cn") return "布局试验：仅支持学校 HTTPS 教务页面"
        view.requestLayout()
        view.invalidate()
        val result = NauLayoutRepair.evaluate(view)
        return when (result) {
            "\"height-adjusted\"" -> "布局试验：已按视口设置零高度 body 并重算 EasyUI 布局"
            "\"body-not-zero\"" -> "布局试验：body 高度并非零，未修改"
            "\"not-matched\"" -> "布局试验：页面不符合已观察的学校布局条件，未修改"
            "\"invalid-viewport\"" -> "布局试验：视口尺寸异常，未修改"
            else -> "布局试验：执行失败或超时"
        }
    }
}
