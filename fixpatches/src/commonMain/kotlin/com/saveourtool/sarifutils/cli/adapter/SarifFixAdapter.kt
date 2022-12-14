package com.saveourtool.sarifutils.cli.adapter

import com.saveourtool.sarifutils.cli.config.FileReplacements
import com.saveourtool.sarifutils.cli.config.RuleReplacements
import com.saveourtool.sarifutils.cli.files.createTempDir
import com.saveourtool.sarifutils.cli.files.fs
import com.saveourtool.sarifutils.cli.files.readFile
import com.saveourtool.sarifutils.cli.files.readLines
import com.saveourtool.sarifutils.cli.utils.dropFileProtocol
import io.github.detekt.sarif4k.ArtifactLocation
import io.github.detekt.sarif4k.Replacement

import io.github.detekt.sarif4k.Run
import io.github.detekt.sarif4k.SarifSchema210
import okio.Path
import okio.Path.Companion.toPath

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Adapter for applying sarif fix object replacements to the corresponding test files
 *
 * @param sarifFile path to the sarif file with fix object replacements
 * @param testFiles list of the test file, to which above fixes need to be applied
 */
class SarifFixAdapter(
    private val sarifFile: Path,
    private val testFiles: List<Path>
) {
    private val tmpDir = fs.createTempDir(SarifFixAdapter::class.simpleName!!)

    /**
     * Main entry for processing and applying fixes from sarif file into the test files
     *
     * @return list of files with applied fixes
     */
    fun process(): List<Path> {
        val sarifSchema210: SarifSchema210 = Json.decodeFromString(
            fs.readFile(sarifFile)
        )
        // A run object describes a single run of an analysis tool and contains the output of that run.
        val processedFiles = sarifSchema210.runs.asSequence().flatMapIndexed { index, run ->
            val runReplacements: List<RuleReplacements> = extractFixObjects(run)
            if (runReplacements.isEmpty()) {
                println("Run #$index have no `fix object` section!")
                emptyList()
            } else {
                applyReplacementsToFiles(runReplacements, testFiles)
            }
        }
        return processedFiles.toList()
    }

    /**
     * Collect all fix objects into the list from sarif file
     *
     * @param run describes a single run of an analysis tool, and contains the reported output of that run
     * @return list of replacements for all files from single [run]
     */
    fun extractFixObjects(run: Run): List<RuleReplacements> {
        if (!run.isFixObjectExist()) {
            return emptyList()
        }
        // A result object describes a single result detected by an analysis tool.
        // Each result is produced by the evaluation of a rule.
        return run.results?.asSequence()
            ?.map { result ->
                // A fix object represents a proposed fix for the problem indicated by the result.
                // It specifies a set of artifacts to modify.
                // For each artifact, it specifies regions to remove, and provides new content to insert.
                result.fixes?.flatMap { fix ->
                    fix.artifactChanges.mapNotNull { artifactChange ->
                        val currentArtifactLocation = artifactChange.artifactLocation
                        if (currentArtifactLocation.uri == null) {
                            println("Error: Field `uri` is absent in `artifactLocation`! Ignore this artifact change")
                            null
                        } else {

                            val originalUri = resolveBaseUri(
                                getUriBaseIdForCurrentArtifactLocation(currentArtifactLocation, result),
                                run
                            )
                            println("ORIGINAL URI: $originalUri")
                            val filePath = currentArtifactLocation.uri!!.toPath()
                            val replacements = artifactChange.replacements
                            FileReplacements(filePath, replacements)
                    }
                    }
                } ?: emptyList()
            }
            ?.toList() ?: emptyList()
    }

    private fun Run.isFixObjectExist(): Boolean = this.results?.any { result ->
        result.fixes != null
    } ?: false

    // uriBaseID could be provided directly in `artifactLocation` or in corresponding field from `locations` scope in `results` scope
    private fun getUriBaseIdForCurrentArtifactLocation(
        currentArtifactLocation: ArtifactLocation,
        result: io.github.detekt.sarif4k.Result
    ): String? {
        val uriBaseIDFromLocations = result.locations?.find {
            it.physicalLocation?.artifactLocation?.uri == currentArtifactLocation.uri
        }?.physicalLocation?.artifactLocation?.uriBaseID
        return currentArtifactLocation.uriBaseID ?: uriBaseIDFromLocations
    }

    // TODO: Move to utils
    // Recursively resolve base uri: https://docs.oasis-open.org/sarif/sarif/v2.1.0/os/sarif-v2.1.0-os.html#_Toc34317498
    private fun resolveBaseUri(uriBaseID: String?, run: Run): Path {
        // Find corresponding value in `run.originalURIBaseIDS`, otherwise
        // the tool can set the uriBaseId property to "%srcroot%", which have been agreed that this indicates the root of the source tree in which the file appears.
        val originalUri = run.originalURIBaseIDS?.get(uriBaseID) ?: return ".".toPath()
        return if (originalUri.uri == null) {
            if (originalUri.uriBaseID == null) {
                ".".toPath()
            } else {
                resolveBaseUri(originalUri.uriBaseID!!, run)
            }
        } else {
            val uri = originalUri.uri!!.dropFileProtocol().toPath()
            if (uri.isAbsolute) {
                uri
            } else {
                resolveBaseUri(originalUri.uriBaseID, run) / uri
            }
        }
    }

    /**
     * Apply fixes from single run to the test files
     *
     * @param runReplacements list of replacements from all rules
     * @param testFiles list of test files
     */
    private fun applyReplacementsToFiles(runReplacements: List<RuleReplacements>, testFiles: List<Path>): List<Path> = runReplacements.flatMap { ruleReplacements ->
        val filteredRuleReplacements = filterRuleReplacements(ruleReplacements)
        filteredRuleReplacements?.mapNotNull { fileReplacements ->
            val testFile = testFiles.find {
                val fullPathOfFileFromSarif = if (!fileReplacements.filePath.isAbsolute) {
                    fs.canonicalize(sarifFile.parent!! / fileReplacements.filePath)
                } else {
                    fileReplacements.filePath
                }
                fs.canonicalize(it) == fullPathOfFileFromSarif
            }
            if (testFile == null) {
                println("Couldn't find appropriate test file on the path ${fileReplacements.filePath}, which provided in Sarif!")
                null
            } else {
                applyReplacementsToSingleFile(testFile, fileReplacements.replacements)
            }
        } ?: emptyList()
    }

    /**
     * If there are several fixes in one file on the same line by different rules, take only the first one
     *
     * @param ruleReplacements list of replacements by all rules
     * @return filtered list of replacements by all rules
     */
    private fun filterRuleReplacements(ruleReplacements: RuleReplacements?): RuleReplacements? {
        // group replacements for each file by all rules
        val listOfAllReplacementsForEachFile = ruleReplacements?.groupBy { fileReplacement ->
            fileReplacement.filePath.toString()
        }?.values

        return listOfAllReplacementsForEachFile?.flatMap { fileReplacements ->
            // distinct replacements from all rules for each file by `startLine`,
            // i.e., take only first of possible fixes for each line
            fileReplacements.distinctBy { fileReplacement ->
                fileReplacement.replacements.map { replacement ->
                    replacement.deletedRegion.startLine
                }
            }
        }
    }

    /**
     * Create copy of the test file and apply fixes from sarif
     *
     * @param testFile test file which need to be fixed
     * @param replacements corresponding replacements for [testFile]
     * @return file with applied fixes
     */
    private fun applyReplacementsToSingleFile(testFile: Path, replacements: List<Replacement>): Path {
        val testFileCopy = tmpDir.resolve(testFile.name)
        // If file doesn't exist, fill it with original data
        // Otherwise, that's mean, that we already made some changes to it (by other rules),
        // so continue to work with modified file
        if (!fs.exists(testFileCopy)) {
            fs.copy(testFile, testFileCopy)
        }
        val fileContent = fs.readLines(testFileCopy).toMutableList()

        replacements.forEach { replacement ->
            val startLine = replacement.deletedRegion.startLine!!.toInt() - 1
            val startColumn = replacement.deletedRegion.startColumn!!.toInt() - 1
            val endColumn = replacement.deletedRegion.endColumn!!.toInt() - 1
            val insertedContent = replacement.insertedContent?.text

            insertedContent?.let {
                fileContent[startLine] = fileContent[startLine].replaceRange(startColumn, endColumn, it)
            } ?: run {
                fileContent[startLine] = fileContent[startLine].removeRange(startColumn, endColumn)
            }
        }
        fs.write(testFileCopy) {
            fileContent.forEach { line ->
                writeUtf8(line + '\n')
            }
        }
        return testFileCopy
    }
}
