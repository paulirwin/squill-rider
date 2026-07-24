package io.github.paulirwin.squill.rider.run

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Application-level settings for the Squill plugin.
 *
 * Application- rather than project-level because the CLI is installed per machine, not per
 * solution: a user who overrides the path does so once.
 */
@Service(Service.Level.APP)
@State(name = "SquillSettings", storages = [Storage("squill.xml")])
class SquillSettings : PersistentStateComponent<SquillSettings.State> {
    class State {
        /** Explicit path to the Squill CLI; blank means "discover it". */
        @JvmField
        var cliPath: String = ""
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    var cliPath: String
        get() = state.cliPath
        set(value) {
            state.cliPath = value
        }

    companion object {
        fun getInstance(): SquillSettings =
            ApplicationManager.getApplication().getService(SquillSettings::class.java)
    }
}
