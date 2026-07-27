package co.uan.epilepsy.dpa_saam.core.permission

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import co.uan.epilepsy.dpa_saam.presentation.main.MainUiEvent
import co.uan.epilepsy.dpa_saam.presentation.main.MainViewModel
import com.uan.designsystem.uikit.components.UanModal
import com.uan.designsystem.uikit.components.UanModalAction
import com.uan.designsystem.uikit.foundation.UanTone

private enum class PermissionStep {
    Notifications,
    Bluetooth,
    Done,
}

@Composable
fun PermissionCoordinator(
    viewModel: MainViewModel,
    onPermissionsReady: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var currentStep by remember { mutableStateOf(PermissionStep.Notifications) }
    val uiState by viewModel.uiState.collectAsState()

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasBluetoothPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_SCAN,
        ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            currentStep = PermissionStep.Bluetooth
        } else {
            viewModel.showNotificationPermissionDialog()
            viewModel.showSnackbar("Se necesitan notificaciones para alertas clínicas")
        }
    }

    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            currentStep = PermissionStep.Done
            onPermissionsReady()
        } else {
            val permanentlyDenied = activity?.let { act ->
                results.entries.any { (permission, granted) ->
                    !granted && !ActivityCompat.shouldShowRequestPermissionRationale(act, permission)
                }
            } ?: false

            viewModel.showSnackbar(
                if (permanentlyDenied) {
                    "Permisos Bluetooth denegados. Abre ajustes para habilitarlos."
                } else {
                    "Se necesitan permisos Bluetooth para conectar la manilla"
                },
            )
        }
    }

    LaunchedEffect(currentStep) {
        when (currentStep) {
            PermissionStep.Notifications -> {
                if (hasNotificationPermission()) {
                    currentStep = PermissionStep.Bluetooth
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    currentStep = PermissionStep.Bluetooth
                }
            }

            PermissionStep.Bluetooth -> {
                if (hasBluetoothPermissions()) {
                    currentStep = PermissionStep.Done
                    onPermissionsReady()
                } else {
                    bluetoothLauncher.launch(
                        arrayOf(
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT,
                        ),
                    )
                }
            }

            PermissionStep.Done -> Unit
        }
    }

    if (uiState.showNotificationPermissionDialog) {
        UanModal(
            visible = true,
            onDismissRequest = { viewModel.onEvent(MainUiEvent.DismissNotificationDialog) },
            title = "Notificaciones necesarias",
            body = "DPA-SAAM necesita enviarte alertas cuando la manilla registre lecturas de SpO2 y BPM.",
            primaryAction = UanModalAction(
                label = "Conceder permiso",
                onClick = {
                    viewModel.onEvent(MainUiEvent.DismissNotificationDialog)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            ),
            secondaryAction = UanModalAction(
                label = "Abrir ajustes",
                onClick = {
                    viewModel.openAppSettings(context)
                    viewModel.onEvent(MainUiEvent.DismissNotificationDialog)
                },
            ),
            tone = UanTone.Info,
        )
    }

    content()
}
