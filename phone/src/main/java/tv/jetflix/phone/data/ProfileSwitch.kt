package tv.jetflix.phone.data

import tv.jetflix.phone.Graph

/** Switching between logins saved on this phone for the current server. No password: the server confirms the saved login first. */
object ProfileSwitch {
    sealed class Outcome {
        object Switched : Outcome()
        /** The server rejected the saved login. It was dropped, so the password is needed again. */
        data class Expired(val name: String) : Outcome()
        /** The server could not be reached or answered with an error. Nothing changed. */
        data class Unreachable(val name: String) : Outcome()
    }

    /** Saved logins for the signed-in server, signed-in one included. */
    fun saved(): List<Session> {
        val s = SessionManager.session.value ?: return emptyList()
        return Graph.tokens.profiles(s.serverUrl)
    }

    /** Blocking network call: run off the main thread. */
    fun select(target: Session): Outcome = when (Graph.repo.validateToken(target)) {
        true -> { Graph.tokens.activate(target); Graph.signIn(target); Outcome.Switched }
        false -> { Graph.tokens.forget(target.serverUrl, target.userId); Outcome.Expired(target.userName) }
        null -> Outcome.Unreachable(target.userName)
    }
}
