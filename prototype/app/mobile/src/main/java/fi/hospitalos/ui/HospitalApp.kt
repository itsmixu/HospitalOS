package fi.hospitalos.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fi.hospitalos.AppViewModel
import fi.hospitalos.ward.Link

enum class Tab { WARD, CONNECT }

@Composable
fun HospitalApp(
    vm: AppViewModel,
    onAllowBluetooth: () -> Unit,
    onRegister: () -> Unit,
    onUnregister: () -> Unit,
) {
  val link by vm.ward.link.collectAsStateWithLifecycle()
  var tab by rememberSaveable { mutableStateOf(Tab.CONNECT) }
  val snackbar = remember { SnackbarHostState() }

  // Jump to the ward as soon as the server connects the first time.
  var jumped by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(link) {
    if (link == Link.ONLINE && !jumped) {
      jumped = true
      tab = Tab.WARD
    }
  }
  LaunchedEffect(Unit) { vm.notices.collect { snackbar.showSnackbar(it) } }

  HospitalTheme {
    Scaffold(
        containerColor = C.Page,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
          NavigationBar(containerColor = C.Panel) {
            val colors =
                NavigationBarItemDefaults.colors(
                    selectedIconColor = C.Blue,
                    selectedTextColor = C.Blue,
                    indicatorColor = C.Blue.copy(alpha = 0.15f),
                    unselectedIconColor = C.Muted,
                    unselectedTextColor = C.Muted,
                )
            NavigationBarItem(
                selected = tab == Tab.WARD,
                onClick = { tab = Tab.WARD },
                icon = { Icon(Icons.Outlined.LocalHospital, null) },
                label = { Text("Ward") },
                colors = colors,
            )
            NavigationBarItem(
                selected = tab == Tab.CONNECT,
                onClick = { tab = Tab.CONNECT },
                icon = { Icon(Icons.Outlined.SettingsRemote, null) },
                label = { Text("Connect") },
                colors = colors,
            )
          }
        },
    ) { padding ->
      when (tab) {
        Tab.WARD -> WardScreen(vm, Modifier.padding(padding), openConnect = { tab = Tab.CONNECT })
        Tab.CONNECT -> ConnectScreen(vm, Modifier.padding(padding), onAllowBluetooth, onRegister, onUnregister)
      }
    }
  }
}
