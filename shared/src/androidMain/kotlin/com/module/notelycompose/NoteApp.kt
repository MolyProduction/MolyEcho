package com.module.notelycompose

import android.app.Application
import android.util.Log
import com.module.notelycompose.di.initKoinApplication
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class NoteApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Pre-load sherpa-onnx JNI on the main thread so JNI_OnLoad runs with the app
        // class loader. Without this, the library's first load happens on a Dispatchers.IO
        // background thread whose class loader cannot resolve app-bundled classes, causing
        // GetFieldID("decodingMethod") to fail at runtime.
        try {
            System.loadLibrary("sherpa-onnx-jni")
        } catch (e: UnsatisfiedLinkError) {
            // Expected on devices/emulators where the AAR native libs are absent
            Log.w("NoteApp", "sherpa-onnx-jni not available: ${e.message}")
        }
        Napier.base(DebugAntilog())
        initKoinApplication {
            androidContext(this@NoteApp)
            androidLogger()
        }
    }
}
