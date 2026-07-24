package io.github.paulirwin.squill.rider.run

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import io.github.paulirwin.squill.rider.SquillBundle
import javax.swing.JComponent

/**
 * Editor UI for a Squill run configuration.
 *
 * The connection is chosen from the project's existing DataGrip data sources rather than typed as
 * a connection string: the platform already manages those, including credentials, so the run
 * configuration never has to store a secret.
 */
class SquillRunConfigurationEditor(private val project: Project) :
    SettingsEditor<SquillRunConfiguration>() {

    private val projectFileField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFileDescriptor("squillproj")
                .withTitle(SquillBundle.message("run.field.project.browseTitle")),
        )
    }

    /** Data sources are read once per editor; the picker reflects what exists at open time. */
    private val dataSources = SquillConnectionResolver.listDataSources(project)

    private val dataSourceModel = javax.swing.DefaultComboBoxModel(dataSources.toTypedArray())

    private var targetDatabase = ""
    private var buildConfiguration = "Debug"
    private var dryRun = false
    private var disallowTableRebuild = false
    private var dropObjectsNotInSource = false
    private var allowDataLoss = false

    private val panel = panel {
        row(SquillBundle.message("run.field.project")) {
            cell(projectFileField).align(Align.FILL)
        }
        row(SquillBundle.message("run.field.dataSource")) {
            comboBox(
                dataSourceModel,
                SimpleListCellRenderer.create("") { it?.name ?: "" },
            ).align(Align.FILL)
        }
        row(SquillBundle.message("run.field.targetDatabase")) {
            textField().bindText(::targetDatabase)
                .comment(SquillBundle.message("run.field.targetDatabase.comment"))
        }
        row(SquillBundle.message("run.field.buildConfiguration")) {
            textField().bindText(::buildConfiguration)
        }

        group(SquillBundle.message("run.group.options")) {
            row {
                checkBox(SquillBundle.message("run.option.dryRun")).bindSelected(::dryRun)
                    .comment(SquillBundle.message("run.option.dryRun.comment"))
            }
            row {
                checkBox(SquillBundle.message("run.option.disallowTableRebuild"))
                    .bindSelected(::disallowTableRebuild)
            }
            row {
                checkBox(SquillBundle.message("run.option.dropObjectsNotInSource"))
                    .bindSelected(::dropObjectsNotInSource)
                    .comment(SquillBundle.message("run.option.dropObjectsNotInSource.comment"))
            }
            row {
                checkBox(SquillBundle.message("run.option.allowDataLoss"))
                    .bindSelected(::allowDataLoss)
                    .comment(SquillBundle.message("run.option.allowDataLoss.comment"))
            }
        }
    }

    override fun createEditor(): JComponent = panel

    override fun resetEditorFrom(configuration: SquillRunConfiguration) {
        val options = configuration.options

        projectFileField.text = options.projectFilePath.orEmpty()
        dataSourceModel.selectedItem =
            SquillConnectionResolver.findDataSource(project, options.dataSourceId)

        targetDatabase = options.targetDatabase.orEmpty()
        buildConfiguration = options.buildConfiguration ?: "Debug"
        dryRun = options.dryRun
        disallowTableRebuild = options.disallowTableRebuild
        dropObjectsNotInSource = options.dropObjectsNotInSource
        allowDataLoss = options.allowDataLoss

        panel.reset()
    }

    override fun applyEditorTo(configuration: SquillRunConfiguration) {
        panel.apply()

        val options = configuration.options
        options.projectFilePath = projectFileField.text.takeIf { it.isNotBlank() }
        options.dataSourceId =
            (dataSourceModel.selectedItem as? com.intellij.database.dataSource.LocalDataSource)
                ?.uniqueId
        options.targetDatabase = targetDatabase.takeIf { it.isNotBlank() }
        options.buildConfiguration = buildConfiguration.takeIf { it.isNotBlank() } ?: "Debug"
        options.dryRun = dryRun
        options.disallowTableRebuild = disallowTableRebuild
        options.dropObjectsNotInSource = dropObjectsNotInSource
        options.allowDataLoss = allowDataLoss
    }
}
