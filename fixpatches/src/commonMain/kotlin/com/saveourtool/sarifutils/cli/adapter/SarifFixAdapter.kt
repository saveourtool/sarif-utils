package com.saveourtool.sarifutils.cli.adapter

import com.saveourtool.sarifutils.cli.config.FileReplacements
import com.saveourtool.sarifutils.cli.config.RuleReplacements
import com.saveourtool.sarifutils.cli.files.fs
import com.saveourtool.sarifutils.cli.files.readFile
import io.github.detekt.sarif4k.Replacement
import io.github.detekt.sarif4k.Run
import io.github.detekt.sarif4k.SarifSchema210
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okio.Path
import okio.Path.Companion.toPath

class SarifFixAdapter(
    private val sarifFile: Path,
    private val testFiles: List<Path>
) {
    fun process() {
        val sarifSchema210 = Json.decodeFromString<SarifSchema210>(
            fs.readFile(sarifFile)
        )
        // A run object describes a single run of an analysis tool and contains the output of that run.
        sarifSchema210.runs.forEach {
            val runReplacements: List<RuleReplacements?>? = it.extractFixObject()
            applyReplacementsToFile(runReplacements, testFiles)
        }
    }

    // TODO what with nullability of returning type?
    private fun Run.extractFixObject(): List<RuleReplacements?>? {
        // TODO Note, all fields could be absent
        // TODO support multiline fixes. Should we? In microsoft they don't use endLine at all
        // A result object describes a single result detected by an analysis tool.
        // Each result is produced by the evaluation of a rule.
        return this.results?.map { result ->
            // A fix object represents a proposed fix for the problem indicated by the result.
            // It specifies a set of artifacts to modify.
            // For each artifact, it specifies regions to remove, and provides new content to insert.
            result.fixes?.flatMap { fix ->
                fix.artifactChanges.map { artifactChange ->
                    // TODO: What if uri is not provided?
                    val filePath = artifactChange.artifactLocation.uri!!.toPath()
                    val replacements = artifactChange.replacements
                    FileReplacements(filePath, replacements)
                }
            }
        }
    }

    private fun applyReplacementsToFile(runReplacements: List<RuleReplacements?>?, testFiles: List<Path>) {
        runReplacements?.forEach { ruleReplacements ->
            ruleReplacements?.forEach {
                println("\n-------------------Replacement for file ${it.filePath}-------------------------\n")
                it.replacements.forEach {
                    println("Start line: ${it.deletedRegion.startLine}, start column ${it.deletedRegion.startColumn}, replacement: ${it.insertedContent?.text}")
                }
            }
        }
    }
}
