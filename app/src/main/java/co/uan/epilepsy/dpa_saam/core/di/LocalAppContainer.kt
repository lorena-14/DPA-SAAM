package co.uan.epilepsy.dpa_saam.core.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer no disponible")
}

@Composable
fun ProvideAppContainer(
    container: AppContainer,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAppContainer provides container) {
        content()
    }
}
