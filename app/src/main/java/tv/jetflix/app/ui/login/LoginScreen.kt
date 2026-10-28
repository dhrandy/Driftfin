package tv.jetflix.app.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.ensureActive
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
    /** Names of profiles already signed in on this TV for the typed server. Picking one needs no password. */
    val savedProfiles: List<String> = emptyList(),
    /** Non-null while a Quick Connect approval is pending: the code the user approves in another Jellyfin app. */
    val quickCode: String? = null,
)

class LoginViewModel(
    private val repo: JellyfinRepository,
    private val tokenStore: TokenStore,
    /** Quick Connect poll cadence; tests shrink it. */
    private val quickPollMs: Long = 2_000,
) : ViewModel() {

    private var quickJob: kotlinx.coroutines.Job? = null

    private val _state = MutableStateFlow(LoginUiState(serverUrl = tokenStore.lastServer()))
    val state = _state.asStateFlow()

    init {
        if (tokenStore.lastServer().isNotEmpty()) loadProfiles()
        refreshSaved()
    }

    private fun refreshSaved() {
        val normalized = repo.normalizeServerUrl(_state.value.serverUrl)
        val names = if (normalized.isEmpty()) emptyList() else tokenStore.profiles(normalized).map { it.userName }
        _state.value = _state.value.copy(savedProfiles = names)
    }

    /**
     * Picks a profile. A saved one signs straight in with its own stored token after the server confirms the token
     * still works; an expired or revoked token is dropped and the password is asked for. Anything else just fills the name.
     */
    fun selectProfile(name: String, onSuccess: () -> Unit) {
        val normalized = repo.normalizeServerUrl(_state.value.serverUrl)
        val saved = tokenStore.profiles(normalized).firstOrNull { it.userName.equals(name, ignoreCase = true) }
        if (saved == null) { update(username = name); return }
        _state.value = _state.value.copy(username = name, password = "", connecting = true, error = null)
        viewModelScope.launch {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { repo.validateToken(saved) }
            when (ok) {
                true -> {
                    coroutineContext.ensureActive()
                    val persisted = tokenStore.activate(saved)
                    SessionManager.signIn(saved)
                    _state.value = _state.value.copy(connecting = false, sessionNotSaved = !persisted)
                    onSuccess()
                }
                false -> {
                    tokenStore.forget(saved.serverUrl, saved.userId)
                    refreshSaved()
                    _state.value = _state.value.copy(connecting = false, error = "Your saved sign-in for $name expired. Enter the password.")
                }
                null -> _state.value = _state.value.copy(connecting = false, error = "Could not reach $normalized to switch to $name. Check the connection and try again.")
            }
        }
    }

    /** "Who's watching?": the server's public user list. Admins can hide it, so this can come back empty. */
    fun loadProfiles() {
        val normalized = repo.normalizeServerUrl(_state.value.serverUrl)
        if (normalized.isEmpty()) return
        viewModelScope.launch {
            try {
                val users = repo.apiFor(normalized, deviceId = tokenStore.deviceId()).getPublicUsers().body()?.mapNotNull { it.Name } ?: emptyList()
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
        if (serverUrl != null) refreshSaved()
    }

    private fun completeSignIn(normalized: String, userId: String, userName: String, token: String, onSuccess: () -> Unit) {
        val saved = tokenStore.save(normalized, userId, userName, token)
        refreshSaved()
        SessionManager.signIn(
            Session(
                serverUrl = normalized,
                userId = userId,
                userName = userName,
                token = token,
                deviceId = tokenStore.deviceId(),
            ),
        )
        _state.value = _state.value.copy(connecting = false, quickCode = null, sessionNotSaved = !saved)
        onSuccess()
    }

    /** Quick Connect: show the server's code, the user approves it in another Jellyfin app, the secret signs in. */
    fun startQuickConnect(onSuccess: () -> Unit) {
        val s = _state.value
        val normalized = repo.normalizeServerUrl(s.serverUrl)
        if (normalized.isEmpty()) {
            _state.value = s.copy(error = "Enter your Jellyfin server address")
            return
        }
        quickJob?.cancel()
        quickJob = viewModelScope.launch {
            _state.value = _state.value.copy(connecting = true, error = null, quickCode = null)
            try {
                val api = repo.apiFor(normalized, deviceId = tokenStore.deviceId())
                val info = api.getPublicSystemInfo()
                if (!info.isSuccessful || info.body() == null) {
                    _state.value = _state.value.copy(connecting = false, error = "No Jellyfin server answered at $normalized")
                    return@launch
                }
                val init = api.quickConnectInitiate()
                val st = init.body()
                val secret = st?.Secret
                if (!init.isSuccessful || secret.isNullOrEmpty() || st.Code.isNullOrEmpty()) {
                    _state.value = _state.value.copy(connecting = false, error = "Quick Connect isn't enabled on this server. Sign in with your password instead.")
                    return@launch
                }
                _state.value = _state.value.copy(connecting = false, quickCode = st.Code)
                // Five minutes of approval waiting, then the code is abandoned.
                val deadline = System.currentTimeMillis() + 5 * 60_000
                var approved = false
                while (isActive && System.currentTimeMillis() < deadline) {
                    kotlinx.coroutines.delay(quickPollMs)
                    val chk = try { api.quickConnectCheck(secret).body() } catch (_: Exception) { null }
                    if (chk?.Authenticated == true) { approved = true; break }
                }
                if (!approved) {
                    if (isActive) _state.value = _state.value.copy(quickCode = null, error = "Quick Connect wasn't approved in time. Try again or use your password.")
                    return@launch
                }
                _state.value = _state.value.copy(connecting = true)
                val auth = api.authenticateWithQuickConnect(tv.jetflix.app.data.QuickConnectAuth(secret))
                val body = auth.body()
                val token = body?.AccessToken
                val user = body?.User
                if (!auth.isSuccessful || token.isNullOrEmpty() || user == null) {
                    _state.value = _state.value.copy(connecting = false, quickCode = null, error = "Quick Connect sign-in failed. Try again or use your password.")
                    return@launch
                }
                coroutineContext.ensureActive()
                completeSignIn(normalized, user.Id, user.Name ?: "Jellyfin user", token, onSuccess)
            } catch (e: Exception) {
                if (!isActive) return@launch
                _state.value = _state.value.copy(
                    connecting = false, quickCode = null,
                    error = "Could not reach $normalized (${e.javaClass.simpleName})",
                )
            }
        }
    }

    /** Leaving a cancellable login owns every pending request, not just a displayed code. */
    fun cancelPendingLogin() {
        viewModelScope.coroutineContext.cancelChildren()
        quickJob = null
        _state.value = _state.value.copy(connecting = false, quickCode = null, password = "", error = null)
    }

    fun cancelQuickConnect() {
        quickJob?.cancel()
        _state.value = _state.value.copy(quickCode = null, connecting = false)
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
                val api = repo.apiFor(normalized, deviceId = tokenStore.deviceId())
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
                coroutineContext.ensureActive()
                completeSignIn(normalized, user.Id, user.Name ?: s.username.trim(), token, onSuccess)
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
        onSelectProfile = { viewModel.selectProfile(it, onLoggedIn) },
        onQuickConnect = { viewModel.startQuickConnect(onLoggedIn) },
        onCancelQuickConnect = { viewModel.cancelQuickConnect() },
    )
}

@Composable
fun LoginContent(
    state: LoginUiState,
    onUpdate: (String?, String?, String?) -> Unit,
    onConnect: () -> Unit,
    onLoadProfiles: () -> Unit = {},
    onSelectProfile: (String) -> Unit = {},
    onQuickConnect: () -> Unit = {},
    onCancelQuickConnect: () -> Unit = {},
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
            if (state.quickCode == null) {
            Button(
                onClick = { onQuickConnect() },
                enabled = !state.connecting,
                colors = ButtonDefaults.colors(
                    containerColor = JetflixColors.SurfaceLight,
                    contentColor = JetflixColors.TextPrimary,
                    focusedContainerColor = JetflixColors.Red,
                    focusedContentColor = JetflixColors.TextPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Quick Connect", style = JetflixType.body)
            }
            }
            val names = (state.savedProfiles + state.profiles).distinctBy { it.lowercase() }
            if (names.isNotEmpty()) {
                Text("Who's watching?", style = JetflixType.caption, color = JetflixColors.TextSecondary)
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    names.forEach { name ->
                        val isSaved = state.savedProfiles.any { it.equals(name, ignoreCase = true) }
                        Button(
                            onClick = { if (isSaved) onSelectProfile(name) else onUpdate(null, name, null) },
                            enabled = !state.connecting,
                            colors = ButtonDefaults.colors(
                                containerColor = if (state.username == name) JetflixColors.Red else JetflixColors.SurfaceLight,
                                contentColor = JetflixColors.TextPrimary,
                                focusedContainerColor = JetflixColors.Red,
                                focusedContentColor = JetflixColors.TextPrimary,
                            ),
                        ) { Text(if (isSaved) "\u2713 $name" else name, style = JetflixType.caption) }
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
            if (state.quickCode != null) {
                Text("Approve this code in any Jellyfin app (Dashboard or app settings, Quick Connect):", style = JetflixType.caption, color = JetflixColors.TextSecondary)
                Text(state.quickCode, style = JetflixType.sectionTitle, color = JetflixColors.TextPrimary)
                Text("Waiting for approval…", style = JetflixType.caption, color = JetflixColors.TextMuted)
                Button(
                    onClick = { onCancelQuickConnect() },
                    colors = ButtonDefaults.colors(
                        containerColor = JetflixColors.SurfaceLight,
                        contentColor = JetflixColors.TextPrimary,
                        focusedContainerColor = JetflixColors.Red,
                        focusedContentColor = JetflixColors.TextPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cancel", style = JetflixType.body)
                }
            } else {
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
            if (tv.jetflix.app.data.ServerAddress.isCleartext(state.serverUrl)) {
                Text(tv.jetflix.app.data.ServerAddress.HTTP_WARNING, style = JetflixType.caption, color = JetflixColors.TextMuted)
            }
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
