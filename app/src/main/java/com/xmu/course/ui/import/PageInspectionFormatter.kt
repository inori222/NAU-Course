package com.xmu.course.ui.import

import org.json.JSONObject

/** Never interpolate untrusted page values; tolerate missing or tampered inspection results. */
internal object PageInspectionFormatter {
    fun format(result: String?): String {
        val json = runCatching { JSONObject(result ?: "") }.getOrNull() ?: return "页面结构检查不可用"
        fun count(key: String): String = (json.opt(key) as? Number)?.toInt()?.coerceIn(0, 1_000_000)?.toString() ?: "未知"
        fun flag(key: String): String = when (json.opt(key)) { true -> "是"; false -> "否"; else -> "未知" }
        fun state(key: String): String = when (json.opt(key)) { "loading" -> "加载中"; "interactive" -> "交互中"; "complete" -> "完成"; else -> "未知" }
        fun centerTag(): String = when (json.opt("centerTag")) {
            "HTML", "BODY", "DIV", "IFRAME", "CANVAS", "IMG", "OTHER" -> json.optString("centerTag")
            else -> "未知"
        }
        return listOf(
            "主页面：状态=${state("ready")}，body存在=${flag("body")}，body自身可见=${flag("bodyVisible")}，直接子元素数=${count("bodyChildren")}，元素总数=${count("elements")}，iframe数=${count("frames")}，视口=${count("viewportWidth")}×${count("viewportHeight")}",
            "body 原因：display为none=${flag("bodyDisplayNone")}，visibility非visible=${flag("bodyVisibilityHidden")}，opacity为零=${flag("bodyOpacityZero")}，尺寸=${count("bodyWidth")}×${count("bodyHeight")}，视口中央元素类型=${centerTag()}，EasyUI布局已初始化=${flag("easyuiLayoutReady")}",
            "页面根元素：高度=${count("rootHeight")}，clientHeight=${count("rootClientHeight")}，panel-fit=${flag("rootPanelFit")}，body带easyui-layout=${flag("bodyEasyui")}，样式表数=${count("styleSheetCount")}，外链样式表数=${count("stylesheetLinks")}，未取得样式表数=${count("stylesheetWithoutSheet")}",
            "课表 iframe：存在=${flag("framePresent")}，自身可见=${flag("frameVisible")}，尺寸=${count("frameWidth")}×${count("frameHeight")}，文档可读取=${flag("frameReadable")}，状态=${state("frameReady")}，body存在=${flag("frameBody")}，元素总数=${count("frameElements")}",
            "课表 iframe：位于视口内=${flag("frameInViewport")}，祖先样式可见=${flag("frameAncestorsVisible")}",
        ).joinToString("\n")
    }
}
