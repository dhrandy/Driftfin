package tv.jetflix.phone.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

/** Text inputs are 16sp or larger so Android never zooms or crops the page when one is focused. */
val InputTextStyle = TextStyle(fontSize = 16.sp)

@Composable
fun LoginScreen(vm: LoginViewModel = viewModel()) {
    var user by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("DRIFTFIN", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = DriftfinColors.Red)
        Text("Sign in to your Jellyfin server", color = DriftfinColors.TextSecondary, fontSize = 16.sp)
        OutlinedTextField(
            value = vm.server, onValueChange = { vm.server = it },
            label = { Text("Server address") }, singleLine = true, textStyle = InputTextStyle,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { vm.startQuickConnect() }, enabled = !vm.busy,
            colors = ButtonDefaults.buttonColors(containerColor = DriftfinColors.Red),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text("Sign in with Quick Connect", fontSize = 16.sp) }
        vm.code?.let {
            Text("Quick Connect code", color = DriftfinColors.TextSecondary, fontSize = 14.sp)
            Text(it, fontSize = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
            Text("In another Jellyfin app or the web page, open Quick Connect and enter this code.", color = DriftfinColors.TextSecondary, fontSize = 14.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text("Or use a username and password", color = DriftfinColors.TextSecondary, fontSize = 14.sp)
        OutlinedTextField(
            value = user, onValueChange = { user = it }, label = { Text("Username") }, singleLine = true,
            textStyle = InputTextStyle, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it }, label = { Text("Password") }, singleLine = true,
            textStyle = InputTextStyle, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = { vm.signInWithPassword(user, password) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text("Sign in", fontSize = 16.sp)
        }
        vm.status?.let { Text(it, color = MaterialColorError, fontSize = 14.sp) }
        Text("Build ${tv.jetflix.phone.BuildConfig.VERSION_NAME}", color = DriftfinColors.TextMuted, fontSize = 12.sp)
    }
}

private val MaterialColorError = androidx.compose.ui.graphics.Color(0xFFFF6E40)
