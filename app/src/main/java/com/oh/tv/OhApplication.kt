package com.oh.tv

import android.app.Application

class OhApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

