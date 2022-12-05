package com.saveourtool.sarifutils.cli.adapter

import com.saveourtool.sarifutils.cli.config.FileReplacements
import com.saveourtool.sarifutils.cli.config.RuleReplacements
import com.saveourtool.sarifutils.cli.files.fs
import com.saveourtool.sarifutils.cli.files.readFile

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
    /**
     * Main entry for processing and applying fixes from sarif file into the test files
     */
    fun process() {
        val sarifSchema210: SarifSchema210 = Json.decodeFromString(
            fs.readFile(sarifFile)
        )
        // A run object describes a single run of an analysis tool and contains the output of that run.
        sarifSchema210.runs.forEachIndexed { index, run ->
            val runReplacements: List<RuleReplacements?>? = extractFixObject(run)
            if (runReplacements.isNullOrEmpty()) {
                // TODO: Use logging library
                println("Run #$index have no fix object section!")
            } else {
                applyReplacementsToFile(runReplacements, testFiles)
            }
        }
    }

    /**
     * @param run describes a single run of an analysis tool, and contains the reported output of that run
     * @return list of replacements for all files from single [run]
     */
    fun extractFixObject(run: Run): List<RuleReplacements?>? {
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
            }
        }
    }

    private fun Run.isFixObjectExist(): Boolean = this.results?.any { result ->
        result.fixes != null
    } ?: false

    // TODO if insertedContent?.text is null -- only delete region
    @Suppress("UnusedPrivateMember")
    private fun applyReplacementsToFile(runReplacements: List<RuleReplacements?>?, testFiles: List<Path>) {
        runReplacements?.forEach { ruleReplacements ->
            ruleReplacements?.forEach Loop@ { fileReplacements ->
                println("\n-------------------Replacements for file ${fileReplacements.filePath}-------------------------\n")
                val testFile = testFiles.find {
                    it.toString().contains(fileReplacements.filePath.toString())
                }
                if (testFile == null) {
                    println("Couldn't find appropriate test file for ${fileReplacements.filePath} in Sarif!")
                    return@Loop
                }
                fileReplacements.replacements.forEach { replacement ->
                    println("Start line: ${replacement.deletedRegion.startLine}," +
                            "Start column: ${replacement.deletedRegion.startColumn}," +
                            "Replacement: ${replacement.insertedContent?.text}")
                }
            }
        }
    }
}
