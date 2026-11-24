package com.xmu.course

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.domain.BackgroundType
import com.xmu.course.domain.TimetableConfig
import com.xmu.course.ui.XmuCourseApp
import com.xmu.course.ui.background.BackgroundContent
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimetableBackdropTest {
    @get:Rule val rule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun timetableHeaderAccountsForStatusBarOnce() {
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("onboarding_done", true).commit()
        lateinit var host: View
        rule.setContent {
            host = LocalView.current
            XmuCourseApp()
        }
        fun headerTop(statusBarHeight: Int): Float {
            rule.runOnIdle {
                val type = WindowInsetsCompat.Type.statusBars()
                val insets = Insets.of(0, statusBarHeight, 0, 0)
                val bars = WindowInsetsCompat.Builder()
                    .setInsets(type, insets)
                    .setInsetsIgnoringVisibility(type, insets)
                    .setVisible(type, statusBarHeight > 0)
                    .build()
                ViewCompat.dispatchApplyWindowInsets(host, bars)
            }
            return rule.onNodeWithContentDescription("添加课程").fetchSemanticsNode().boundsInRoot.top
        }
        val baseline = headerTop(0)
        assertEquals(48f, headerTop(48) - baseline, 1f)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun pannedImageAndOverlayEndAtTheSameEdges() {
        val file = File(context.cacheDir, "white-background.png")
        val image = Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888)
        image.eraseColor(android.graphics.Color.WHITE)
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val config = TimetableConfig(
            timetableId = 1, backgroundType = BackgroundType.CUSTOM,
            backgroundValue = Uri.fromFile(file).toString(), blurRadius = 0,
            overlayColor = Color.Black.toArgb(), overlayAlpha = 0.5f,
            cropScale = 2f, cropOffsetX = -999f, cropOffsetY = 999f,
        )
        lateinit var host: View
        rule.setContent {
            host = LocalView.current
            Box(Modifier.size(120.dp).background(Color.Magenta).testTag("frame"), contentAlignment = Alignment.Center) {
                BackgroundContent(config, Modifier.size(80.dp))
            }
        }
        val frame = rule.onNodeWithTag("frame").fetchSemanticsNode().boundsInRoot
        val pixels = Bitmap.createBitmap(frame.width.toInt(), frame.height.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(pixels).apply { translate(-frame.left, -frame.top) }
        val centerX = pixels.width / 2
        val centerY = pixels.height / 2
        // 位图异步解码完成后，纯白图片加半透明黑罩应呈中灰色。
        rule.waitUntil(10_000) {
            rule.runOnIdle { host.draw(canvas) }
            pixels.getPixel(centerX, centerY) in listOf(0xff7f7f7f.toInt(), 0xff808080.toInt())
        }
        val inset = pixels.height / 6
        val masked = pixels.getPixel(centerX, centerY)
        for (edge in listOf(inset + 1, pixels.height - inset - 2)) {
            assertEquals(masked, pixels.getPixel(centerX, edge))
            assertEquals(masked, pixels.getPixel(edge, centerY))
        }
        for (outside in listOf(inset - 2, pixels.height - inset + 1)) {
            assertEquals(Color.Magenta.toArgb(), pixels.getPixel(centerX, outside))
            assertEquals(Color.Magenta.toArgb(), pixels.getPixel(outside, centerY))
        }
    }
}
