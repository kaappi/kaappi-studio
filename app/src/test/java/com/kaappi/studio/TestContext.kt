package com.kaappi.studio

import android.content.Context
import java.io.File
import org.mockito.Mockito

fun contextWithCacheDir(cacheDir: File): Context =
    Mockito.mock(Context::class.java).also {
        Mockito.`when`(it.cacheDir).thenReturn(cacheDir)
    }

fun contextWithFilesDir(filesDir: File): Context =
    Mockito.mock(Context::class.java).also {
        Mockito.`when`(it.filesDir).thenReturn(filesDir)
    }
