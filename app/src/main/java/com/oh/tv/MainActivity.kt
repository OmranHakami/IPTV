package com.oh.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import com.oh.tv.ui.DeviceProfile
import com.oh.tv.ui.LocalDeviceProfile
import com.oh.tv.ui.OhApp
import com.oh.tv.ui.OhViewModel
import com.oh.tv.ui.theme.OhTheme

class MainActivity : ComponentActivity() {
    private val viewModel: OhViewModel by viewModels {
        val container = (application as OhApplication).container
        OhViewModel.Factory(container.repository, container.localPairingServer)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalDeviceProfile provides DeviceProfile.fromBuild()) {
                OhTheme { OhApp(viewModel) }
            }
        }
    }
}
