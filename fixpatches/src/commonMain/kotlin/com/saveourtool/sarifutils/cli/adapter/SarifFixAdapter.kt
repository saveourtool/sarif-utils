package com.saveourtool.sarifutils.cli.adapter

import com.saveourtool.sarifutils.cli.config.FileReplacements
import com.saveourtool.sarifutils.cli.config.RuleReplacements
import com.saveourtool.sarifutils.cli.files.copyFileContent
import com.saveourtool.sarifutils.cli.files.createTempDir
import com.saveourtool.sarifutils.cli.files.fs
import com.saveourtool.sarifutils.cli.files.readFile
import com.saveourtool.sarifutils.cli.files.readLines
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
     * @return list of processed files
     */
    fun process(): List<Path?> {
        val sarifSchema210: SarifSchema210 = Json.decodeFromString(
            fs.readFile(sarifFile)
        )
        // A run object describes a single run of an analysis tool and contains the output of that run.
        val processedFiles = sarifSchema210.runs.flatMapIndexed { index, run ->
            val runReplacements: List<RuleReplacements?> = extractFixObjects(run)
            if (runReplacements.isEmpty()) {
                // TODO: Use logging library
                println("Run #$index have no fix object section!")
                emptyList()
            } else {
                applyReplacementsToFiles(runReplacements, testFiles)
            }
        }
        return processedFiles
    }

    /**
     * @param run describes a single run of an analysis tool, and contains the reported output of that run
     * @return list of replacements for all files from single [run]
     */
    fun extractFixObjects(run: Run): List<RuleReplacements?> {
        if (!run.isFixObjectExist()) {
            return emptyList()
        }
        // A result object describes a single result detected by an analysis tool.
        // Each result is produced by the evaluation of a rule.
        return run.results?.map { result ->
            // A fix object represents a proposed fix for the problem indicated by the result.
            // It specifies a set of artifacts to modify.
            // For each artifact, it specifies regions to remove, and provides new content to insert.
            result.fixes?.flatMap { fix ->
                fix.artifactChanges.map { artifactChange ->
                    // TODO: What if uri is not provided? Could it be?
                    val filePath = artifactChange.artifactLocation.uri!!.toPath()
                    val replacements = artifactChange.replacements
                    FileReplacements(filePath, replacements)
                }
            } ?: emptyList()
        } ?: emptyList()
    }

    private fun Run.isFixObjectExist(): Boolean = this.results?.any { result ->
        result.fixes != null
    } ?: false

    @Suppress("UnusedPrivateMember")
    private fun applyReplacementsToFiles(runReplacements: List<RuleReplacements?>?, testFiles: List<Path>): List<Path?> = runReplacements?.flatMap { ruleReplacements ->
        ruleReplacements?.mapNotNull { fileReplacements ->
            val testFile = testFiles.find {
                it.toString().contains(fileReplacements.filePath.toString())
            }
            if (testFile == null) {
                println("Couldn't find appropriate test file on the path ${fileReplacements.filePath}, which provided in Sarif!")
                null
            } else {
                applyChangesToFile(testFile, fileReplacements.replacements)
            }
        } ?: emptyList()
    } ?: emptyList()

    private fun applyChangesToFile(testFile: Path, replacements: List<Replacement>): Path {
        val testFileCopy = tmpDir.resolve(testFile.name)
        fs.copyFileContent(testFile, testFileCopy)
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
            fileContent.forEach {
                write((it + "\n").encodeToByteArray())
            }
        }
        return testFileCopy
    }
}
