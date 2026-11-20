package com.aliasadi.tamdid.ui

import androidx.appcompat.app.AppCompatActivity
import com.aliasadi.tamdid.R

/** AppCompat host for Compose tests (the app theme requires an AppCompat activity). */
class TestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        setTheme(R.style.Theme_Tamdid)
        super.onCreate(savedInstanceState)
    }
}
