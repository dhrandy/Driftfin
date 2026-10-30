package tv.jetflix.phone.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.jetflix.phone.Graph
import tv.jetflix.phone.data.AuthenticateUserByName
import tv.jetflix.phone.data.AuthenticationResult
import tv.jetflix.phone.data.QuickConnectAuth
import tv.jetflix.phone.data.Session

/** Sign-in state: server address, Quick Connect code, or username and password. */
class LoginViewModel : ViewModel() {
    var server by mutableStateOf(Graph.tokens.lastServer())
    var status by mutableStateOf<String?>(null)
    var code by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    private var quickJob: Job? = null

    private fun base() = Graph.repo.normalizeServerUrl(server)

    private fun finish(base: String, r: AuthenticationResult): Boolean {
        val token = r.AccessToken
        val user = r.User
        if (token == null || user == null) { status = "The server did not return a sign-in."; return false }
        val session = Session(base, user.Id, user.Name ?: "", token, Graph.tokens.deviceId())
        Graph.tokens.saveLastServer(base)
        if (!Graph.tokens.save(base, session.userId, session.userName, token)) {
            status = "Signed in, but this phone could not save the login."
        }
        Graph.signIn(session)
        return true
    }

    fun startQuickConnect() {
        quickJob?.cancel()
        if (server.isBlank()) { status = "Enter your server address first."; return }
        quickJob = viewModelScope.launch {
            busy = true; status = null; code = null
            try {
                val base = base()
                val api = Graph.repo.apiFor(base)
                val init = api.quickConnectInitiate()
                val state = init.body()
                val secret = state?.Secret
                if (!init.isSuccessful || secret == null) {
                    status = "Quick Connect is not available on this server. Use a username and password."
                    return@launch
                }
                code = state.Code
                repeat(100) {
                    delay(3000)
                    val check = api.quickConnectCheck(secret)
                    if (check.body()?.Authenticated == true) {
                        val auth = api.authenticateWithQuickConnect(QuickConnectAuth(secret))
                        val body = auth.body()
                        if (auth.isSuccessful && body != null) finish(base, body) else status = "Quick Connect sign-in failed."
                        return@launch
                    }
                }
                status = "The code timed out. Start again."
            } catch (e: Exception) {
                status = "Could not reach the server."
            } finally {
                busy = false; code = null
            }
        }
    }

    fun signInWithPassword(user: String, password: String) {
        if (server.isBlank() || user.isBlank()) { status = "Enter the server address and a username."; return }
        quickJob?.cancel()
        viewModelScope.launch {
            busy = true; status = null
            try {
                val base = base()
                val r = Graph.repo.apiFor(base).authenticateByName(AuthenticateUserByName(user.trim(), password))
                val body = r.body()
                if (r.isSuccessful && body != null) finish(base, body) else status = "Wrong username or password."
            } catch (e: Exception) {
                status = "Could not reach the server."
            } finally { busy = false }
        }
    }
}
