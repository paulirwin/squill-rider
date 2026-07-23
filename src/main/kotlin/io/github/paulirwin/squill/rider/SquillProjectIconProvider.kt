package io.github.paulirwin.squill.rider

import com.intellij.openapi.project.Project
import com.jetbrains.rider.projectView.ProjectModelIconProvider
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity
import com.jetbrains.rider.projectView.workspace.isProject
import icons.ReSharperIcons
import javax.swing.Icon

/**
 * Gives `.squillproj` project nodes a database icon in Rider's Solution Explorer.
 *
 * Rider's frontend (`ProjectModelIconsKt.calculateIcon`) decides a project node's icon; it does
 * NOT consult any ReSharper backend presenter for this. It first queries this extension point
 * (`com.intellij.rider.projectModelIconProvider`, first non-null wins), then falls back to
 * hardcoded icons by project type/language — which for an unregistered `.squillproj` lands on the
 * generic project icon. Returning a non-null icon here overrides that fallback.
 *
 * We reuse the platform's shipped [ReSharperIcons.ProjectModel.DatabaseProject] icon — the same one
 * Rider uses for `.sqlproj` database projects.
 */
class SquillProjectIconProvider : ProjectModelIconProvider {

    override fun getIcon(project: Project, entity: ProjectModelEntity): Icon? {
        if (!entity.isProject()) return null

        val fileName = entity.url?.fileName ?: return null
        if (!fileName.endsWith(SQUILLPROJ_EXTENSION, ignoreCase = true)) return null

        return ReSharperIcons.ProjectModel.DatabaseProject
    }

    private companion object {
        const val SQUILLPROJ_EXTENSION = ".squillproj"
    }
}
