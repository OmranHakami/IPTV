package com.oh.tv.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.oh.tv.BuildConfig

enum class DeviceProfile { Tv, Tablet;
    val isTv: Boolean get() = this == Tv

    companion object {
        fun fromBuild(): DeviceProfile = if (BuildConfig.DEVICE_PROFILE == "tv") Tv else Tablet
    }
}

val LocalDeviceProfile = staticCompositionLocalOf { DeviceProfile.Tv }

