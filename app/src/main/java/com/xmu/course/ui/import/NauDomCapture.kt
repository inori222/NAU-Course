package com.xmu.course.ui.import

/** 根据实际顶层样本读取同源课表 iframe；不注入脚本到第三方，也不触及凭据。 */
internal object NauDomCapture {
    val script = """
        (function() {
          try {
            var frame = document.getElementById('FrameContent');
            if (!frame) return document.documentElement.outerHTML;
            var doc = frame.contentDocument;
            return doc && doc.documentElement ? doc.documentElement.outerHTML : null;
          } catch (error) { return null; }
        })()
    """.trimIndent()
}
