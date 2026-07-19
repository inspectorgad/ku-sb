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
class AppResourcesTest {

    @Test
    fun `app name is KU Softball`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("KU Softball", context.getString(R.string.app_name))
    }
}
