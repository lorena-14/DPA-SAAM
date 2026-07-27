package co.uan.epilepsy.dpa_saam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import co.uan.epilepsy.dpa_saam.core.di.ProvideAppContainer
import co.uan.epilepsy.dpa_saam.core.permission.PermissionCoordinator
import co.uan.epilepsy.dpa_saam.presentation.main.MainViewModel
import co.uan.epilepsy.dpa_saam.presentation.main.MainViewModelFactory
import co.uan.epilepsy.dpa_saam.ui.screen.MainScreen
import com.uan.designsystem.uikit.theme.UanTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as DpaSaamApplication
        val container = app.container

        setContent {
            UanTheme {
                ProvideAppContainer(container) {
                    val viewModel: MainViewModel = viewModel(
                        factory = MainViewModelFactory(container),
                    )

                    PermissionCoordinator(
                        viewModel = viewModel,
                        onPermissionsReady = { viewModel.onPermissionsReady() },
                    ) {
                        MainScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
