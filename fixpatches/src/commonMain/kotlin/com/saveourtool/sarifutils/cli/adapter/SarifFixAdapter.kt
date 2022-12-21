package com.saveourtool.sarifutils.cli.adapter

import com.saveourtool.sarifutils.cli.config.FileReplacements
import com.saveourtool.sarifutils.cli.config.RuleReplacements
import com.saveourtool.sarifutils.cli.files.createTempDir
import com.saveourtool.sarifutils.cli.files.fs
import com.saveourtool.sarifutils.cli.files.readFile
import com.saveourtool.sarifutils.cli.files.readLines
import com.saveourtool.sarifutils.cli.utils.adaptedIsAbsolute
import com.saveourtool.sarifutils.cli.utils.getUriBaseIdForArtifactLocation
import com.saveourtool.sarifutils.cli.utils.resolveUriBaseId
import io.github.detekt.sarif4k.Replacement

import io.github.detekt.sarif4k.Run
import io.github.detekt.sarif4k.SarifSchema210
import okio.Path
import okio.Path.Companion.toPath

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Adapter for applying sarif fix object replacements to the corresponding target files
 *
 * @param sarifFile path to the sarif file with fix object replacements
 * @param targetFiles list of the target files, to which above fixes need to be applied
 */
class SarifFixAdapter(
    private val sarifFile: Path,
    private val targetFiles: List<Path>
) {
    private val tmpDir = createTempDir(SarifFixAdapter::class.simpleName!!)

    /**
     * Main entry for processing and applying fixes from sarif file into the target files
     *
     * @return list of files with applied fixes
     */
    fun process(): List<Path> {
        val sarifSchema210: SarifSchema210 = Json.decodeFromString(
            readFile(sarifFile)
        )
        // A run object describes a single run of an analysis tool and contains the output of that run.
        val processedFiles = sarifSchema210.runs.asSequence().flatMapIndexed { index, run ->
            val runReplacements: List<RuleReplacements> = extractFixObjects(run)
            if (runReplacements.isEmpty()) {
                println("Run #$index have no any `fix object` section!")
                emptyList()
            } else {
                val groupedReplacements = groupReplacementsByFiles(runReplacements)
                // if there are several fixes by different rules for the same region within one file, take only first of them
                // and reverse the order of replacements for each file
                val filteredRuleReplacements = filterRuleReplacements(groupedReplacements)
                applyReplacementsToFiles(filteredRuleReplacements, targetFiles)
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
    internal fun extractFixObjects(run: Run): List<RuleReplacements> {
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
                            val uriBaseId = resolveUriBaseId(
                                currentArtifactLocation.getUriBaseIdForArtifactLocation(result),
                                run
                            )
                            val filePath = uriBaseId / currentArtifactLocation.uri!!.toPath()
                            val replacements = artifactChange.replacements
                            FileReplacements(filePath, replacements)
                        }
                    }
                } ?: emptyList()
            }
            ?.toList() ?: emptyList()
    }

    /**
     * Group all replacements from all rules by file name
     *
     * @param runReplacements list of replacements by all rules
     * @return list of [FileReplacements], where [replacements] field contain replacements from all rules
     */
    private fun groupReplacementsByFiles(runReplacements: List<RuleReplacements>): List<FileReplacements> {
        // flat replacements by all rules into single list and group by file path
        return runReplacements.flatten().groupBy { fileReplacements ->
            fileReplacements.filePath
        }.map { entry ->
            // now collect all replacements by all rules for each file into single instance of `FileReplacements`
            val filePath = entry.key
            val fileReplacements = entry.value
            FileReplacements(
                filePath,
                fileReplacements.flatMap {
                    it.replacements
                }
            )
        }
    }

    /**
     * If there are several fixes in one file for the same region by different rules, take only the first one,
     * also return filtered replacements in reverse order, in aim to provide fixes on the next stages without invalidation of indexes,
     * i.e. in case of multiline fixes, which will add new lines, or remove regions, that is, shift all the lines below,
     * apply all fixes, starting from the last fix, thus it won't break startLine's and endLine's for other fixes
     *
     * @param fileReplacementsList list of replacements by all rules
     * @return filtered list of replacements by all rules
     */
    private fun filterRuleReplacements(fileReplacementsList: List<FileReplacements>): List<FileReplacements> = fileReplacementsList.map { fileReplacementsListForSingleFile ->
        val filePath = fileReplacementsListForSingleFile.filePath
        // sort replacements by startLine
        val sortedReplacements = sortReplacementsByStartLine(fileReplacementsListForSingleFile)
        // now, from overlapping fixes, take only the first one
        val nonOverlappingFixes = getNonOverlappingReplacements(fileReplacementsListForSingleFile.filePath, sortedReplacements)
        // save replacements in reverse order
        FileReplacements(
            filePath,
            nonOverlappingFixes.reversed()
        )
    }

    /**
     * Sort provided list of replacements by [startLine]
     *
     * @param fileReplacementsListForSingleFile list of replacements for target file
     * @return sorted list of replacements by [startLine]
     */
    private fun sortReplacementsByStartLine(
        fileReplacementsListForSingleFile: FileReplacements
    ): List<Replacement> = fileReplacementsListForSingleFile.replacements.map { replacement ->
        val updatedReplacement = recoverEndLine(replacement)
        updatedReplacement
    }.sortedWith(
        compareBy({ it.deletedRegion.startLine }, { it.deletedRegion.endLine })
    )

    /**
     * It's not require to present endLine, if fix represents the single line changes,
     * also, maybe it won't present in multiline fix too, since `insertedContent` will contain '\n' by itself
     * so, for consistency we will set `endLine` by ourselves, if it absent
     *
     * @param replacement replacement instance, with probably missing [endLine] field
     * @return updated replacement, with filled [endLine], if it was absent
     */
    private fun recoverEndLine(replacement: Replacement): Replacement = if (replacement.deletedRegion.endLine == null) {
        // count the number of lines in inserted text
        val linesNumber = replacement.insertedContent?.text?.countLines() ?: 1
        // calculate shift from startLine, if linesNumber = 1 => endLine equals startLine, and shift is 0
        // else shift = linesNumber - 1 and endLine = startLine + shift
        val shiftFromStartLine = linesNumber - 1
        val deletedRegion = replacement.deletedRegion.copy(
            endLine = replacement.deletedRegion.startLine!! + shiftFromStartLine
        )
        replacement.copy(
            deletedRegion = deletedRegion
        )
    } else {
        replacement
    }

    /**
     * For the [sortedReplacements] list take only non overlapping replacements.
     * Replacement overlaps, if they are fix the same region, e.g: max(fix1.startLine, fix2.startLine) <= min(fix1.endLine, fix2.endLine),
     * or, for sorted intervals by startLine it's equals to (fix2.startLine <= fix1.endLine)
     *
     * @param filePath file path
     * @param sortedReplacements list of replacements, sorted by [startLine]
     * @return list of non overlapping replacements
     */
    private fun getNonOverlappingReplacements(filePath: Path, sortedReplacements: List<Replacement>): MutableList<Replacement> {
        val nonOverlappingFixes: MutableList<Replacement> = mutableListOf(sortedReplacements[0])
        var currentEndLine = sortedReplacements[0].deletedRegion.endLine!!

        for (i in 1 until sortedReplacements.size) {
            if (sortedReplacements[i].deletedRegion.startLine!! <= currentEndLine) {
                println("Fix ${sortedReplacements[i].prettyString()} for $filePath was ignored, due it overlaps with others." +
                        " Only the first fix for this region will be applied.")
            } else {
                nonOverlappingFixes.add(sortedReplacements[i])
                currentEndLine = sortedReplacements[i].deletedRegion.endLine!!
            }
        }
        return nonOverlappingFixes
    }

    /**
     * Apply fixes from single run to the target files
     *
     * @param fileReplacementsList list of replacements from all rules
     * @param targetFiles list of target files
     */
    private fun applyReplacementsToFiles(fileReplacementsList: List<FileReplacements>, targetFiles: List<Path>): List<Path> = fileReplacementsList.mapNotNull { fileReplacements ->
        val targetFile = targetFiles.find {
            val fullPathOfFileFromSarif = if (!fileReplacements.filePath.adaptedIsAbsolute()) {
                fs.canonicalize(sarifFile.parent!! / fileReplacements.filePath)
            } else {
                fileReplacements.filePath
            }
            fs.canonicalize(it) == fullPathOfFileFromSarif
        }
        if (targetFile == null) {
            println("Couldn't find appropriate target file on the path ${fileReplacements.filePath}, which provided in Sarif!")
            null
        } else {
            applyReplacementsToSingleFile(targetFile, fileReplacements.replacements)
        }
    }

    /**
     * Create copy of the target file and apply fixes from sarif
     *
     * @param targetFile target file which need to be fixed
     * @param replacements corresponding replacements for [targetFile]
     * @return file with applied fixes
     */
    @Suppress("TOO_LONG_FUNCTION")
    private fun applyReplacementsToSingleFile(targetFile: Path, replacements: List<Replacement>): Path {
        val targetFileCopy = tmpDir.resolve(targetFile.name)
        fs.copy(targetFile, targetFileCopy)

        val fileContent = readLines(targetFileCopy).toMutableList()

        replacements.forEach { replacement ->
            val startLine = replacement.deletedRegion.startLine!!.toInt() - 1
            val endLine = replacement.deletedRegion.endLine!!.toInt() - 1
            val startColumn = replacement.deletedRegion.startColumn?.let {
                it.toInt() - 1
            }
            val endColumn = replacement.deletedRegion.endColumn?.let {
                it.toInt() - 1
            }
            val insertedContent = replacement.insertedContent?.text

            applyFix(fileContent, insertedContent, startLine, endLine, startColumn, endColumn)
        }
        fs.write(targetFileCopy) {
            fileContent.forEach { line ->
                writeUtf8(line + '\n')
            }
        }
        return targetFileCopy
    }

    /**
     * Apply fixes into [fileContent]
     *
     * @param fileContent file data, where need to change content
     * @param insertedContent represents inserted content for the [fileContent] starting with line [startLine] and ending with [endLine],
     * or null if fix represent the deletion of region
     * @param startLine start index of line, which need to be changed
     * @param endLine end index of line, which need to be changed
     * @param startColumn index of column, starting from which content should be changed, or null if range ([startLine], [endLine]) will be completely replaced
     * @param endColumn index of column, ending with which content should be changed, or null if range ([startLine], [endLine]) will be completely replaced
     */
    @Suppress("TOO_MANY_PARAMETERS")
    private fun applyFix(
        fileContent: MutableList<String>,
        insertedContent: String?,
        startLine: Int,
        endLine: Int,
        startColumn: Int?,
        endColumn: Int?
    ) {
        if (startLine != endLine) {
            // multiline fix
            applyMultiLineFix(fileContent, insertedContent, startLine, endLine, startColumn, endColumn)
        } else {
            // single line fix
            applySingleLineFix(fileContent, insertedContent, startLine, startColumn, endColumn)
        }
    }

    @Suppress("TOO_MANY_PARAMETERS")
    private fun applyMultiLineFix(
        fileContent: MutableList<String>,
        insertedContent: String?,
        startLine: Int,
        endLine: Int,
        startColumn: Int?,
        endColumn: Int?
    ) {
        insertedContent?.let { content ->
            if (startColumn != null && endColumn != null) {
                // first, remove changeable region
                removeMultiLineRange(fileContent, startLine, endLine, startColumn, endColumn)
                // insertedContent already contains newlines, so could be inserted simply starting from startLine
                fileContent[startLine] = StringBuilder(fileContent[startLine]).apply { insert(startColumn, content) }.toString()
            } else {
                // remove whole changeable region
                removeMultiLines(fileContent, startLine, endLine)
                // insertedContent already contains newlines, so could be inserted simply starting from startLine
                fileContent.add(startLine, content)
            }
        } ?: run {
            // just remove changeable region
            if (startColumn != null && endColumn != null) {
                removeMultiLineRange(fileContent, startLine, endLine, startColumn, endColumn)
            } else {
                removeMultiLines(fileContent, startLine, endLine)
            }
        }
    }

    private fun removeMultiLineRange(
        fileContent: MutableList<String>,
        startLine: Int,
        endLine: Int,
        startColumn: Int,
        endColumn: Int
    ) {
        // remove characters in startLine after startColumn
        fileContent[startLine] = fileContent[startLine].removeRange(startColumn, fileContent[startLine].length)
        // remove lines between startLine and endLine
        for (line in startLine + 1 until endLine) {
            fileContent.removeAt(line)
        }
        // remove characters in endLine before endColumn
        fileContent[endLine].removeRange(0, endColumn)
    }

    private fun removeMultiLines(
        fileContent: MutableList<String>,
        startLine: Int,
        endLine: Int,
    ) {
        // remove whole range (startLine, endLine)
        for (line in startLine..endLine) {
            fileContent.removeAt(line)
        }
    }

    private fun applySingleLineFix(
        fileContent: MutableList<String>,
        insertedContent: String?,
        startLine: Int,
        startColumn: Int?,
        endColumn: Int?
    ) {
        insertedContent?.let { content ->
            if (startColumn != null && endColumn != null) {
                // replace range
                fileContent[startLine] = fileContent[startLine].replaceRange(startColumn, endColumn, content)
            } else {
                // replace whole line
                fileContent[startLine] = content
            }
        } ?: run {
            if (startColumn != null && endColumn != null) {
                // remove range
                fileContent[startLine] = fileContent[startLine].removeRange(startColumn, endColumn)
            } else {
                // remove whole line
                fileContent.removeAt(startLine)
            }
        }
    }

    private fun String.countLines(): Int = this.split('\n').filterNot { it.isBlank() }.size

    private fun Replacement.prettyString(): String = "(startLine: ${this.deletedRegion.startLine}, endLine: ${this.deletedRegion.endLine}, " +
            "startColumn: ${this.deletedRegion.startColumn}, endColumn: ${this.deletedRegion.endColumn}, insertedContent: ${this.insertedContent})"
}
