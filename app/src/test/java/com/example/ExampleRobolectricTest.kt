package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("DEDUN", appName)
  }

  @Test
  fun `verify safe to spend elements exist in asset index html`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val html = context.assets.open("index.html").bufferedReader().use { it.readText() }
    assert(html.contains("safeToSpendCard"))
    assert(html.contains("safeSpendAmountDisplay"))
    assert(html.contains("safeRingFill"))
    assert(html.contains("allowanceDaysStatusBadge"))
    assert(html.contains("safeProjectionLine"))
    assert(html.contains("savingsStreakBadge"))
  }
}
