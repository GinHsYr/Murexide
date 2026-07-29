package com.juhao.murexide.benchmark

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DefaultEmojiScrollBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollAndTypeHundredsOfDefaultEmoji() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        iterations = 8,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait(
                Intent().apply {
                    component = ComponentName(PACKAGE_NAME, BENCHMARK_ACTIVITY)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
            )
        }
    ) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val centerX = device.displayWidth / 2
        repeat(8) {
            device.swipe(centerX, device.displayHeight * 3 / 4, centerX, device.displayHeight / 4, 16)
            device.swipe(centerX, device.displayHeight / 4, centerX, device.displayHeight * 3 / 4, 16)
        }
        device.click(centerX, device.displayHeight - 96)
        device.waitForIdle()
        val stressInput = "[.OK]".repeat(40)
        device.executeShellCommand("input text '$stressInput'")
        device.waitForIdle()
    }

    private companion object {
        const val PACKAGE_NAME = "com.juhao.murexide"
        const val BENCHMARK_ACTIVITY =
            "com.juhao.murexide.ui.chat.DefaultEmojiBenchmarkActivity"
    }
}
