package co.uan.epilepsy.dpa_saam.ui.screen

import co.uan.epilepsy.dpa_saam.BuildConfig
import co.uan.epilepsy.dpa_saam.domain.model.ConnectionStatus
import co.uan.epilepsy.dpa_saam.presentation.main.MainUiEvent
import co.uan.epilepsy.dpa_saam.presentation.main.MainUiState
import co.uan.epilepsy.dpa_saam.presentation.main.MainViewModel
import com.uan.designsystem.uikit.components.UanAppBar
import com.uan.designsystem.uikit.components.UanBadge
import com.uan.designsystem.uikit.components.UanBadgeEmphasis
import com.uan.designsystem.uikit.components.UanButton
import com.uan.designsystem.uikit.components.UanButtonSize
import com.uan.designsystem.uikit.components.UanButtonStyle
import com.uan.designsystem.uikit.components.UanCard
import com.uan.designsystem.uikit.components.UanLists
import com.uan.designsystem.uikit.components.UanModal
import com.uan.designsystem.uikit.components.UanModalAction
import com.uan.designsystem.uikit.components.UanProgressIndicator
import com.uan.designsystem.uikit.components.UanProgressIndicatorVariant
import com.uan.designsystem.uikit.components.UanProgressStep
import com.uan.designsystem.uikit.components.UanProgressStepState
import com.uan.designsystem.uikit.foundation.UanTone
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
    .withZone(ZoneId.systemDefault())

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = if (message.contains("ajustes", ignoreCase = true)) "Abrir ajustes" else null,
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.openAppSettings(context)
        }
        viewModel.onEvent(MainUiEvent.DismissSnackbar)
    }

    if (uiState.showBatteryOptDialog) {
        UanModal(
            visible = true,
            onDismissRequest = { viewModel.onEvent(MainUiEvent.DismissBatteryOptDialog) },
            title = "Optimización de batería",
            body = "Para mantener la conexión clínica con la manilla, se recomienda excluir DPA-SAAM de la optimización de batería del sistema.",
            primaryAction = UanModalAction(
                label = "Configurar",
                onClick = { viewModel.onEvent(MainUiEvent.ConfirmBatteryOpt) },
            ),
            secondaryAction = UanModalAction(
                label = "Ahora no",
                onClick = { viewModel.onEvent(MainUiEvent.DismissBatteryOptDialog) },
            ),
            tone = UanTone.Warning,
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            UanAppBar(
                title = "DPA-SAAM",
                subtitle = connectionSubtitle(uiState.connectionStatus),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ConnectionStatusCard(uiState.connectionStatus)
            }

            item {
                LatestReadingCard(uiState)
            }

            item {
                BatteryCard(uiState)
            }

            if (uiState.connectionStatus == ConnectionStatus.Failed) {
                item {
                    UanButton(
                        onClick = { viewModel.onEvent(MainUiEvent.RetryConnection) },
                        modifier = Modifier.fillMaxWidth(),
                        style = UanButtonStyle.Primary,
                        size = UanButtonSize.Regular,
                    ) {
                        Text("Reintentar conexión")
                    }
                }
            }

            if (BuildConfig.DEBUG) {
                item {
                    UanButton(
                        onClick = { viewModel.onEvent(MainUiEvent.SimulateAlert) },
                        modifier = Modifier.fillMaxWidth(),
                        style = UanButtonStyle.Secondary,
                        size = UanButtonSize.Regular,
                    ) {
                        Text("Simular alerta")
                    }
                }
            }

            item {
                Text(
                    text = "Historial de alertas",
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (uiState.alertHistory.isEmpty()) {
                item {
                    UanCard(
                        title = "Sin lecturas",
                        body = "Las alertas recibidas aparecerán aquí",
                    )
                }
            } else {
                items(uiState.alertHistory, key = { it.id }) { reading ->
                    UanLists(
                        title = "SpO2 ${reading.spo2}% · BPM ${reading.bpm}",
                        supportingText = dateTimeFormatter.format(reading.receivedAt),
                        itemDescription = "Alerta del ${dateTimeFormatter.format(reading.receivedAt)}",
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(status: ConnectionStatus) {
    val (label, tone) = when (status) {
        ConnectionStatus.Connected -> "Conectado" to UanTone.Success
        ConnectionStatus.Scanning -> "Escaneando" to UanTone.Info
        ConnectionStatus.Connecting -> "Conectando" to UanTone.Info
        ConnectionStatus.Failed -> "Desconectado" to UanTone.Danger
        ConnectionStatus.Idle -> "Inactivo" to UanTone.Neutral
    }

    UanCard(
        title = "Estado BLE",
        body = "Conexión con la manilla ESP32",
        tone = tone,
        supportingContent = {
            UanBadge(
                text = label,
                tone = tone,
                emphasis = UanBadgeEmphasis.Tonal,
                contentDescription = "Estado de conexión: $label",
            )
        },
    )
}

@Composable
private fun LatestReadingCard(uiState: MainUiState) {
    val reading = uiState.latestReading
    UanCard(
        title = "Última alerta",
        body = if (reading != null) {
            dateTimeFormatter.format(reading.receivedAt)
        } else {
            "Esperando lectura de la manilla"
        },
        supportingContent = if (reading != null) {
            {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "SpO2: ${reading.spo2}%")
                    Text(text = "BPM: ${reading.bpm}")
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun BatteryCard(uiState: MainUiState) {
    val percent = uiState.batteryInfo.percent
    UanCard(
        title = "Batería manilla",
        body = if (percent != null) "$percent%" else "No disponible",
        tone = if (uiState.batteryInfo.isLow) UanTone.Warning else UanTone.Neutral,
        supportingContent = {
            if (percent != null) {
                val stepState = when {
                    uiState.batteryInfo.isLow -> UanProgressStepState.Warning
                    percent >= 50 -> UanProgressStepState.Completed
                    else -> UanProgressStepState.Current
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    UanProgressIndicator(
                        steps = listOf(
                            UanProgressStep(
                                label = "$percent%",
                                state = stepState,
                                contentDescription = "Batería al $percent por ciento",
                            ),
                        ),
                        variant = UanProgressIndicatorVariant.Status,
                        modifier = Modifier.fillMaxWidth(),
                        currentTone = if (uiState.batteryInfo.isLow) UanTone.Warning else UanTone.Primary,
                    )
                    if (uiState.batteryInfo.isLow) {
                        UanBadge(
                            text = "Batería baja",
                            tone = UanTone.Warning,
                            emphasis = UanBadgeEmphasis.Tonal,
                            contentDescription = "Batería baja",
                        )
                    }
                }
            } else {
                UanBadge(
                    text = "No disponible",
                    tone = UanTone.Neutral,
                    emphasis = UanBadgeEmphasis.Tonal,
                    contentDescription = "Batería no disponible",
                )
            }
        },
    )
}

private fun connectionSubtitle(status: ConnectionStatus): String {
    return when (status) {
        ConnectionStatus.Connected -> "Manilla conectada"
        ConnectionStatus.Scanning -> "Buscando manilla..."
        ConnectionStatus.Connecting -> "Estableciendo conexión..."
        ConnectionStatus.Failed -> "Sin conexión — reintenta manualmente"
        ConnectionStatus.Idle -> "Preparando conexión..."
    }
}
