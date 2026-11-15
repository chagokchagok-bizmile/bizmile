package com.chagokchagok.bizmile

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.chagokchagok.bizmile.ui.BizmileRoot
import com.chagokchagok.bizmile.ui.BizmileViewModel
import com.chagokchagok.bizmile.ui.theme.BizmileTheme
import com.chagokchagok.bizmile.vehicle.PleosVehicleDataSource

class MainActivity : ComponentActivity() {

    private val viewModel: BizmileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestLocationPermissionIfNeeded()
        setContent {
            BizmileTheme {
                BizmileRoot(viewModel)
            }
        }
    }

    /** Pleos FUSED_LOCATION is a runtime ("dangerous") permission. Asked once, while parked at launch. */
    private fun requestLocationPermissionIfNeeded() {
        val app = application as BizmileApp
        if (!app.pleosAvailable) return
        val permission = PleosVehicleDataSource.FUSED_LOCATION_PERMISSION
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(permission), REQUEST_LOCATION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION) (application as BizmileApp).source.value.onPermissionsChanged()
    }

    private companion object {
        const val REQUEST_LOCATION = 1
    }
}
