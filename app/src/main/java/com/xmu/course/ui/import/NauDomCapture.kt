package com.xmu.course.ui.import

/** 根据实际顶层样本读取同源课表 iframe；不注入脚本到第三方，也不触及凭据。 */
internal object NauDomCapture {
    val examScript = """
        (function() {
          if (location.hostname !== 'jwc.nau.edu.cn') return null;
          try {
            var frame = document.getElementById('FrameContent');
            var doc = frame ? frame.contentDocument : document;
            var table = doc && doc.querySelector('table#content');
            var title = table && table.querySelector('.tdTitle');
            return title && title.textContent.indexOf('考试日程列表') !== -1 ? table.outerHTML : null;
          } catch (error) { return null; }
        })()
    """.trimIndent()

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
