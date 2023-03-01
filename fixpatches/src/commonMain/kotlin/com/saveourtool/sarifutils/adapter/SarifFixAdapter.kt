@file:Suppress(
    "FILE_IS_TOO_LONG",
)

package com.saveourtool.sarifutils.adapter

import com.saveourtool.okio.Uri
import com.saveourtool.okio.absolute
import com.saveourtool.okio.createDirectories
import com.saveourtool.okio.isDirectory
import com.saveourtool.okio.isSameFileAsSafe
import com.saveourtool.okio.pathString
import com.saveourtool.okio.relativeToSafe
import com.saveourtool.sarifutils.config.FileReplacements
import com.saveourtool.sarifutils.config.RuleReplacements
import com.saveourtool.sarifutils.files.createTempDir
import com.saveourtool.sarifutils.files.fs
import com.saveourtool.sarifutils.files.readFile
import com.saveourtool.sarifutils.files.readLines
import com.saveourtool.sarifutils.files.writeContentWithNewLinesToFile
import com.saveourtool.sarifutils.net.toLocalPathExt
import com.saveourtool.sarifutils.utils.getUriBaseIdForArtifactLocation
import com.saveourtool.sarifutils.utils.resolveUriBaseId
import com.saveourtool.sarifutils.utils.setLoggingLevel

import io.github.detekt.sarif4k.Replacement
import io.github.detekt.sarif4k.Run
import io.github.detekt.sarif4k.SarifSchema210
import mu.KotlinLogging
import okio.Path
import okio.Path.Companion.toPath

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Adapter for applying sarif fix object replacements to the corresponding target files
 *
 * @param sarifFile path to the sarif file with fix object replacements
 * @param targetFiles list of the target files, to which above fixes need to be applied
 * @param testRoot the root directory of the test suite.
 *   Should be set to a non-`null` value for path relativization to work correctly
 *   (in case SARIF `uri` fields contain absolute paths,
 *   or `file://` URIs pointing to absolute paths).
 */
@Suppress("TooManyFunctions")
class SarifFixAdapter(
    private val sarifFile: Path,
    private val targetFiles: List<Path>,
    private val testRoot: Path? = null,
) {
    @Suppress("WRONG_ORDER_IN_CLASS_LIKE_STRUCTURES")  // https://github.com/saveourtool/diktat/issues/1602
    private val classSimpleName = SarifFixAdapter::class.simpleName!!

    @Suppress("WRONG_ORDER_IN_CLASS_LIKE_STRUCTURES")
    private val log = KotlinLogging.logger(classSimpleName)
    private val tmpDir = createTempDir(classSimpleName)
    init {
        check(testRoot == null || testRoot.isDirectory()) {
            "Test root is not a directory: $testRoot"
        }

        setLoggingLevel()
    }

    /**
     * Main entry for processing and applying fixes from sarif file into the target files
     *
     * @return list of files with applied fixes
     */
    fun process(): List<Path> {
        if (targetFiles.isEmpty()) {
            log.warn { "The list of target files is empty." }
            return emptyList()
        }

        val sarifSchema210: SarifSchema210 = Json.decodeFromString(
            readFile(sarifFile)
        )
        // A run object describes a single run of an analysis tool and contains the output of that run.
        val processedFiles = sarifSchema210.runs.asSequence().flatMapIndexed { index, run ->
            val runReplacements: List<RuleReplacements> = extractFixObjects(run)
            if (runReplacements.isEmpty()) {
                log.warn { "Run #${index + 1} doesn't have any `fix object` section!" }
                emptySequence()
            } else {
                val groupedReplacements = groupReplacementsByFiles(runReplacements)
                // if there are several fixes by different rules for the same region within one file, take only first of them
                // and reverse the order of replacements for each file
                val filteredRuleReplacements = filterRuleReplacements(groupedReplacements)

                when {
                    filteredRuleReplacements.isEmpty() -> {
                        log.warn { "Run #${index + 1} doesn't have any replacements." }
                        emptySequence()
                    }

                    else -> applyReplacementsToFiles(filteredRuleReplacements)
                }
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
                            log.error { "Field `uri` is absent in `artifactLocation`! Ignore this artifact change" }
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
     * so, for consistency we will set `endLine` by ourselves, if it absent
     *
     * @param replacement replacement instance, with probably missing [endLine] field
     * @return updated replacement, with filled [endLine], if it was absent
     */
    private fun recoverEndLine(replacement: Replacement): Replacement = if (replacement.deletedRegion.endLine == null) {
        val deletedRegion = replacement.deletedRegion.copy(
            endLine = replacement.deletedRegion.startLine
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
        if (sortedReplacements.isEmpty()) {
            return mutableListOf()
        }

        val nonOverlappingFixes: MutableList<Replacement> = mutableListOf(sortedReplacements[0])
        var currentEndLine = sortedReplacements[0].deletedRegion.endLine!!

        for (i in 1 until sortedReplacements.size) {
            if (sortedReplacements[i].deletedRegion.startLine!! <= currentEndLine) {
                log.warn {
                    "Fix ${sortedReplacements[i].prettyString()} for $filePath was ignored, due it overlaps with others." +
                            " Only the first fix for this region will be applied."
                }
            } else {
                nonOverlappingFixes.add(sortedReplacements[i])
                currentEndLine = sortedReplacements[i].deletedRegion.endLine!!
            }
        }
        return nonOverlappingFixes
    }

    /**
     * Apply fixes from single run to the [target files][targetFiles].
     *
     * @param fileReplacementsList list of replacements from all rules
     * @throws IllegalArgumentException if [fileReplacementsList] is empty.
     * @throws IllegalStateException if [targetFiles] is empty.
     */
    @Suppress(
        "MaxLineLength",
        "TOO_LONG_FUNCTION",
    )
    @Throws(
        IllegalArgumentException::class,
        IllegalStateException::class,
    )
    private fun applyReplacementsToFiles(
        fileReplacementsList: List<FileReplacements>,
    ): Sequence<Path> {
        require(fileReplacementsList.isNotEmpty()) {
            "The list of replacements is empty."
        }
        check(targetFiles.isNotEmpty()) {
            "The list of target files is empty."
        }

        return fileReplacementsList.asSequence()
            .filter { (fileUri, replacements) ->
                val replacementsEmpty = replacements.isEmpty()
                if (replacementsEmpty) {
                    log.warn { "Skipping file at URI: $fileUri, because the list of replacements is empty" }
                }
                !replacementsEmpty
            }
            .mapNotNull { (fileUri, replacements) ->
                log.info { "Processing file at URI: $fileUri" }
                val localPath = try {
                    Uri(fileUri.pathString).toLocalPathExt()
                } catch (_: IllegalArgumentException) {
                    /*
                     * `fileUri` is actually a path, most probably a Windows path.
                     */
                    fileUri
                }

                val absolute = localPath.isAbsolute
                if (localPath != fileUri) {
                    log.info { "Resolved the URI to a local path: (absolute = $absolute): $localPath" }
                }

                /*
                 * No need to check whether `localPath` is absolute: if it is,
                 * `resolve()` will ignore `sarifFile.parent` and return `localPath`
                 * intact.
                 */
                val absoluteLocalPath = (sarifFile.parent!!.absolute() / localPath).normalized()
                if (absoluteLocalPath != localPath) {
                    log.info { "Converted the path: $localPath -> $absoluteLocalPath" }
                }

                val matchingFile = targetFiles.find { targetFile ->
                    targetFile.isSameFileAsSafe(absoluteLocalPath)
                }
                if (matchingFile == null) {
                    val targetFileCount = targetFiles.size
                    log.warn { "None of the $targetFileCount target file(s) matches the file from SARIF replacement: $localPath" }
                    targetFiles.forEachIndexed { index, targetFile ->
                        log.warn { "\t${index + 1} of $targetFileCount: $targetFile" }
                    }

                    null
                } else {
                    applyReplacementsToSingleFile(matchingFile, replacements)
                }
            }
    }

    /**
     * Create copy of the target file and apply fixes from sarif
     *
     * @param targetFile target file which need to be fixed (may be an absolute
     *   or a relative path).
     * @param replacements corresponding replacements for [targetFile]
     * @return file with applied fixes
     */
    @Suppress("TOO_LONG_FUNCTION")
    private fun applyReplacementsToSingleFile(targetFile: Path, replacements: List<Replacement>): Path {
        val relativeTargetFile = targetFile
            .relativeToTestRoot()
            .relativeToFileSystemRoot()

        val targetFileCopy = tmpDir.resolve(relativeTargetFile)
        // additionally create parent directories, before copy of content
        targetFileCopy.parent?.createDirectories()

        check(!targetFile.isSameFileAsSafe(targetFileCopy)) {
            "Refusing to copy $targetFile onto itself."
        }

        fs.copy(targetFile, targetFileCopy)
        log.info { "Copied $targetFile -> $targetFileCopy" }

        val fileContent = readLines(targetFileCopy).toMutableList()
        log.info { "Reading $targetFileCopy: ${fileContent.size} line(s) read." }

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
        writeContentWithNewLinesToFile(targetFileCopy, fileContent)
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
    @Suppress("TOO_MANY_PARAMETERS", "LongParameterList")
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

    @Suppress("TOO_MANY_PARAMETERS", "LongParameterList")
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
        fileContent.subList(startLine + 1, endLine).clear()
        // remove characters in endLine before endColumn
        fileContent[endLine] = fileContent[endLine].removeRange(0, endColumn)
    }

    private fun removeMultiLines(
        fileContent: MutableList<String>,
        startLine: Int,
        endLine: Int,
    ) {
        // remove whole range (startLine, endLine)
        fileContent.subList(startLine, endLine + 1).clear()
    }

    /**
     * @param startLine the 0-based line number,
     */
    private fun applySingleLineFix(
        fileContent: MutableList<String>,
        insertedContent: String?,
        startLine: Int,
        startColumn: Int?,
        endColumn: Int?
    ) {
        if (fileContent.isEmpty()) {
            log.warn { "Unable to apply the fix at line ${startLine + 1}: the file is empty" }
            return
        }

        val lineCount = fileContent.size

        if (startLine >= lineCount) {
            log.warn { "Unable to apply the fix at line ${startLine + 1}: the file only has $lineCount line(s)." }
            return
        }

        log.info { "Applying a single-line fix to line ${startLine + 1} out of $lineCount" }
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

    private fun Replacement.prettyString(): String = "(startLine: ${this.deletedRegion.startLine}, endLine: ${this.deletedRegion.endLine}, " +
            "startColumn: ${this.deletedRegion.startColumn}, endColumn: ${this.deletedRegion.endColumn}, insertedContent: ${this.insertedContent})"

    /**
     * @return this path, relativized against the [test root][testRoot],
     *   assuming this path is absolute and [test root][testRoot] is non-`null`.
     */
    private fun Path.relativeToTestRoot(): Path =
            when (testRoot) {
                null -> this
                else -> relativeToSafe(testRoot)
            }

    private fun Path.relativeToFileSystemRoot(): Path =
            when (val root = root) {
                null -> this

                /*-
                 * `root` is the file system root of the this path`, or `null`
                 * if the path is relative.
                 *
                 * On UNIX, this will always be `/`.
                 * On Windows, this may be `C:\`, `D:\`, etc.
                 */
                else -> relativeTo(root)
            }
}
