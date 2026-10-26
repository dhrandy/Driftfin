package tv.jetflix.app.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.jetflix.app.data.AuthenticateUserByName
import tv.jetflix.app.data.JellyfinRepository
import tv.jetflix.app.data.Session
import tv.jetflix.app.data.SessionManager
import tv.jetflix.app.data.TokenStore
import tv.jetflix.app.ui.components.Wordmark
import tv.jetflix.app.ui.theme.JetflixColors
import tv.jetflix.app.ui.theme.JetflixType

data class LoginUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val connecting: Boolean = false,
    val error: String? = null,
    val profiles: List<String> = emptyList(),
    val profilesNote: String? = null,
    val sessionNotSaved: Boolean = false,
)

class LoginViewModel(
    private val repo: JellyfinRepository,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState(serverUrl = tokenStore.lastServer()))
    val state = _state.asStateFlow()

    init { if (tokenStore.lastServer().isNotEmpty()) loadProfiles() }

    /** "Who's watching?": the server's public user list. Admins can hide it, so this can come back empty. */
    fun loadProfiles() {
        val normalized = repo.normalizeServerUrl(_state.value.serverUrl)
        if (normalized.isEmpty()) return
        viewModelScope.launch {
            try {
                val users = repo.apiFor(normalized).getPublicUsers().body()?.mapNotNull { it.Name } ?: emptyList()
                _state.value = _state.value.copy(
                    profiles = users,
                    profilesNote = if (users.isEmpty()) "This server doesn't list its users. Type your username." else null,
                )
            } catch (_: Exception) {
                _state.value = _state.value.copy(profiles = emptyList(), profilesNote = null)
            }
        }
    }

    fun update(serverUrl: String? = null, username: String? = null, password: String? = null) {
        _state.value = _state.value.copy(
            serverUrl = serverUrl ?: _state.value.serverUrl,
            username = username ?: _state.value.username,
            password = password ?: _state.value.password,
            error = null,
        )
    }

    fun connect(onSuccess: () -> Unit) {
        val s = _state.value
        val normalized = repo.normalizeServerUrl(s.serverUrl)
        if (normalized.isEmpty()) {
            _state.value = s.copy(error = "Enter your Jellyfin server address")
            return
        }
        if (s.username.isBlank()) {
            _state.value = s.copy(error = "Enter your Jellyfin username")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(connecting = true, error = null)
            try {
                val api = repo.apiFor(normalized)
                val info = api.getPublicSystemInfo()
                if (!info.isSuccessful || info.body() == null) {
                    _state.value = _state.value.copy(connecting = false, error = "No Jellyfin server answered at $normalized")
                    return@launch
                }
                val auth = api.authenticateByName(AuthenticateUserByName(s.username.trim(), s.password))
                val body = auth.body()
                val token = body?.AccessToken
                val user = body?.User
                if (!auth.isSuccessful || token.isNullOrEmpty() || user == null) {
                    _state.value = _state.value.copy(connecting = false, error = "Sign-in failed. Check the username and password.")
                    return@launch
                }
                val saved = tokenStore.save(normalized, user.Id, user.Name ?: s.username.trim(), token)
                SessionManager.signIn(
                    Session(
                        serverUrl = normalized,
                        userId = user.Id,
                        userName = user.Name ?: s.username.trim(),
                        token = token,
                        deviceId = tokenStore.deviceId(),
                    ),
                )
                _state.value = _state.value.copy(connecting = false, sessionNotSaved = !saved)
                onSuccess()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    connecting = false,
                    error = "Could not reach $normalized (${e.javaClass.simpleName})",
                )
            }
        }
    }
}

@Composable
fun LoginScreen(viewModel: LoginViewModel, onLoggedIn: () -> Unit) {
    val state by viewModel.state.collectAsState()
    LoginContent(
        state = state,
        onUpdate = { server, user, pass ->
            viewModel.update(serverUrl = server ?: state.serverUrl, username = user ?: state.username, password = pass ?: state.password)
        },
        onConnect = { viewModel.connect(onLoggedIn) },
        onLoadProfiles = { viewModel.loadProfiles() },
    )
}

@Composable
fun LoginContent(
    state: LoginUiState,
    onUpdate: (String?, String?, String?) -> Unit,
    onConnect: () -> Unit,
    onLoadProfiles: () -> Unit = {},
) {
    val serverFocus = remember { FocusRequester() }

    Box(
        Modifier
            .fillMaxSize()
            .background(JetflixColors.Background),
    ) {
        Wordmark(Modifier.padding(start = 48.dp, top = 28.dp))
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .width(420.dp)
                .background(JetflixColors.Surface, RoundedCornerShape(8.dp))
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Sign in with Jellyfin", style = JetflixType.sectionTitle, color = JetflixColors.TextPrimary)
            LoginField(
                value = state.serverUrl,
                onValueChange = { onUpdate(it, null, null) },
                label = "Server address (e.g. 192.168.1.10:8096)",
                keyboardType = KeyboardType.Uri,
                modifier = Modifier.focusRequester(serverFocus),
            )
            if (state.profiles.isNotEmpty()) {
                Text("Who's watching?", style = JetflixType.caption, color = JetflixColors.TextSecondary)
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.profiles.take(4).forEach { name ->
                        Button(
                            onClick = { onUpdate(null, name, null) },
                            colors = ButtonDefaults.colors(
                                containerColor = if (state.username == name) JetflixColors.Red else JetflixColors.SurfaceLight,
                                contentColor = JetflixColors.TextPrimary,
                                focusedContainerColor = JetflixColors.Red,
                                focusedContentColor = JetflixColors.TextPrimary,
                            ),
                        ) { Text(name, style = JetflixType.caption) }
                    }
                }
            } else {
                Button(
                    onClick = { onLoadProfiles() },
                    colors = ButtonDefaults.colors(
                        containerColor = JetflixColors.SurfaceLight,
                        contentColor = JetflixColors.TextPrimary,
                        focusedContainerColor = JetflixColors.Red,
                        focusedContentColor = JetflixColors.TextPrimary,
                    ),
                ) { Text("Show profiles on this server", style = JetflixType.caption) }
                state.profilesNote?.let { Text(it, style = JetflixType.caption, color = JetflixColors.TextMuted) }
            }
            LoginField(
                value = state.username,
                onValueChange = { onUpdate(null, it, null) },
                label = "Username",
            )
            LoginField(
                value = state.password,
                onValueChange = { onUpdate(null, null, it) },
                label = "Password",
                isPassword = true,
                imeAction = ImeAction.Done,
                onDone = { onConnect() },
            )
            state.error?.let {
                Text(it, style = JetflixType.caption, color = androidx.tv.material3.MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = { onConnect() },
                enabled = !state.connecting,
                colors = ButtonDefaults.colors(
                    containerColor = JetflixColors.Red,
                    contentColor = JetflixColors.TextPrimary,
                    focusedContainerColor = JetflixColors.Red,
                    focusedContentColor = JetflixColors.TextPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.connecting) "Connecting…" else "Sign In", style = JetflixType.body)
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { serverFocus.requestFocus() }
}

@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    Column {
        Text(label, style = JetflixType.caption, color = JetflixColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = JetflixType.body.copy(color = JetflixColors.TextPrimary),
            cursorBrush = SolidColor(JetflixColors.Red),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .background(JetflixColors.SurfaceLight, RoundedCornerShape(4.dp))
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) JetflixColors.FocusBorder else JetflixColors.TextMuted,
                    shape = RoundedCornerShape(4.dp),
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}
