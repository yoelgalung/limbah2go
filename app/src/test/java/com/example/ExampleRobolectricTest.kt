package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Limbah2GO", appName)
  }

  @Test
  fun `bundled index html asset exists and contains Limbah2GO`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val html = context.assets.open("index.html").bufferedReader().use { it.readText() }
    assertTrue(html.contains("<title>Limbah2GO</title>"))
    assertTrue(html.contains("projectId:\"limbah2go\""))
  }

  @Test
  fun `main activity launches without crashing`() {
    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    assertTrue(activity != null)
  }
}
