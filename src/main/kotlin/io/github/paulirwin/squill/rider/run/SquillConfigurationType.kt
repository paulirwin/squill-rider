package io.github.paulirwin.squill.rider.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue
import icons.ReSharperIcons
import io.github.paulirwin.squill.rider.SquillBundle

/**
 * Registers the Squill run configurations so they appear under "Add New Configuration".
 *
 * Two factories rather than one with a verb flag: `script` only reads the target's schema while
 * `deploy` mutates it, and that difference deserves to be visible in the configuration list
 * rather than hidden behind a checkbox.
 */
class SquillConfigurationType : ConfigurationTypeBase(
    ID,
    SquillBundle.message("run.type.name"),
    SquillBundle.message("run.type.description"),
    // The same database-project icon the plugin already uses for .squillproj nodes, so the
    // configurations read as belonging to the same feature.
    NotNullLazyValue.createValue { ReSharperIcons.ProjectModel.DatabaseProject },
) {
    init {
        addFactory(SquillConfigurationFactory(this, SquillVerb.DEPLOY))
        addFactory(SquillConfigurationFactory(this, SquillVerb.SCRIPT))
    }

    companion object {
        const val ID = "SquillRunConfiguration"
    }
}

class SquillConfigurationFactory(
    type: SquillConfigurationType,
    private val squillVerb: SquillVerb,
) : ConfigurationFactory(type) {

    override fun getId(): String = squillVerb.name

    override fun getName(): String = squillVerb.displayName

    override fun getOptionsClass(): Class<out RunConfigurationOptions> =
        SquillRunConfigurationOptions::class.java

    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        SquillRunConfiguration(project, this, squillVerb.displayName, squillVerb)
}
