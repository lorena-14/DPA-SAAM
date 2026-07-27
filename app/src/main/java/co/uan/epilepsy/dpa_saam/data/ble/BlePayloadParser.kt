package co.uan.epilepsy.dpa_saam.data.ble

import co.uan.epilepsy.dpa_saam.core.result.AppResult
import co.uan.epilepsy.dpa_saam.domain.model.BraceletReading
import java.time.Instant

class BlePayloadParser {

    private val pattern = Regex(
        """SpO2:(?<spo2>\d+(?:\.\d+)?),BPM:(?<bpm>\d+)(?:,BAT:(?<bat>\d+))?""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(payload: String, deviceAddress: String): AppResult<BraceletReading> {
        return try {
            val trimmed = payload.trim()
            val match = pattern.matchEntire(trimmed)
                ?: return AppResult.Error("Formato de lectura no reconocido: $trimmed")

            val spo2 = match.groups["spo2"]?.value?.toFloatOrNull()
                ?: return AppResult.Error("SpO2 inválido en la lectura")
            val bpm = match.groups["bpm"]?.value?.toIntOrNull()
                ?: return AppResult.Error("BPM inválido en la lectura")
            val battery = match.groups["bat"]?.value?.toIntOrNull()

            AppResult.Success(
                BraceletReading(
                    spo2 = spo2,
                    bpm = bpm,
                    batteryPercent = battery,
                    receivedAt = Instant.now(),
                    deviceAddress = deviceAddress,
                ),
            )
        } catch (e: Exception) {
            AppResult.Error("Error al interpretar la lectura BLE", e)
        }
    }
}
