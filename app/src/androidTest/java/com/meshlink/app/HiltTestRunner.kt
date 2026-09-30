package com.meshlink.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Custom instrumentation runner that swaps the real [MeshLinkApp] for
 * [HiltTestApplication] so Hilt can inject test fakes/mocks in instrumented tests.
 *
 * Referenced from `testInstrumentationRunner` in app/build.gradle.kts.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader,
        className: String,
        context: Context
    ): Application = super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
