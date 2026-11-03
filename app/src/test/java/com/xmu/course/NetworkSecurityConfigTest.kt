package com.xmu.course

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NetworkSecurityConfigTest {
    @Test fun `只允许确认过的学校主机使用明文`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manifest = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml"))
            .first { it.isFile }
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val application = factory.newDocumentBuilder().parse(manifest).getElementsByTagName("application").item(0)
        assertEquals("@xml/network_security_config", application.attributes.getNamedItemNS("http://schemas.android.com/apk/res/android", "networkSecurityConfig").nodeValue)
        val parser = context.resources.getXml(R.xml.network_security_config)
        val domains = mutableSetOf<String>()
        var baseDisabled = false
        var domainEnabled = false
        parser.use {
            var event = it.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) when (it.name) {
                    "base-config" -> baseDisabled = it.getAttributeValue(null, "cleartextTrafficPermitted") == "false"
                    "domain-config" -> domainEnabled = it.getAttributeValue(null, "cleartextTrafficPermitted") == "true"
                    "domain" -> {
                        assertFalse(it.getAttributeBooleanValue(null, "includeSubdomains", true))
                        domains += it.nextText().trim()
                    }
                }
                event = it.next()
            }
        }
        assertTrue(baseDisabled)
        assertTrue(domainEnabled)
        assertEquals(setOf("sso.nau.edu.cn", "jwc.nau.edu.cn"), domains)
    }
}
