package io.github.paulirwin.squill.rider.run

import java.io.File

/**
 * Locates the `.dacpac` a `.squillproj` builds.
 *
 * The authoritative source is the MSBuild property `SquillDacpacPath`, which `Squill.Sdk`'s
 * `Sdk.props` defines as `<ProjectDir>/bin/<Configuration>/<ProjectName>.dacpac` — note there is
 * **no target-framework subfolder**, unlike a normal .NET project.
 *
 * Reading the evaluated property would mean invoking MSBuild, which costs a process launch on
 * every run. Since the SDK's layout is fixed and public, the path is reconstructed here and the
 * result is verified to exist after the build — a mismatch surfaces as "build produced no
 * dacpac" rather than a silent wrong-file deploy.
 */
object SquillDacpacLocator {
    /**
     * The conventional output path for [projectFile] under [configuration].
     *
     * Mirrors `Sdk.props`: `SquillDacpacFileName` defaults to `$(MSBuildProjectName).dacpac` and
     * `OutputPath` to `bin/$(Configuration)/`.
     */
    fun expectedDacpacPath(projectFile: File, configuration: String): File {
        val projectName = projectFile.nameWithoutExtension
        return File(projectFile.parentFile, "bin/$configuration/$projectName.dacpac")
    }
}
