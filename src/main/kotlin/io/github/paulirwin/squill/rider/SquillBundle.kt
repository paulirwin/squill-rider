package io.github.paulirwin.squill.rider

import com.intellij.DynamicBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE = "messages.SquillBundle"

/**
 * Message bundle for user-facing strings in the Squill plugin. Follow-up PRs that add
 * actions, notifications, and settings UI should route their strings through here.
 */
object SquillBundle : DynamicBundle(BUNDLE) {

    @Suppress("SpreadOperator")
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any) =
        getMessage(key, *params)
}
