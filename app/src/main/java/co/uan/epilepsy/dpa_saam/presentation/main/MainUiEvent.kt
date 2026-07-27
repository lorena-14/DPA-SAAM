package co.uan.epilepsy.dpa_saam.presentation.main

sealed interface MainUiEvent {
    data object RetryConnection : MainUiEvent
    data object DismissSnackbar : MainUiEvent
    data object ConfirmBatteryOpt : MainUiEvent
    data object DismissBatteryOptDialog : MainUiEvent
    data object DismissNotificationDialog : MainUiEvent
    data object SimulateAlert : MainUiEvent
}
