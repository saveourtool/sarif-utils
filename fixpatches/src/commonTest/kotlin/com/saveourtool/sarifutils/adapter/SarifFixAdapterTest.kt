@file:Suppress("FILE_IS_TOO_LONG")

package com.saveourtool.sarifutils.adapter

import com.saveourtool.sarifutils.cli.adapter.SarifFixAdapter
import com.saveourtool.sarifutils.cli.files.readFile
import com.saveourtool.sarifutils.cli.files.readLines

import io.github.detekt.sarif4k.Replacement
import io.github.detekt.sarif4k.SarifSchema210
import io.github.petertrr.diffutils.diff
import io.github.petertrr.diffutils.patch.ChangeDelta
import io.github.petertrr.diffutils.patch.Patch
import io.github.petertrr.diffutils.text.DiffRowGenerator
import okio.Path
import okio.Path.Companion.toPath

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

// FixMe: Possible problems with tests on native platforms:
// https://youtrack.jetbrains.com/issue/KT-54634/MPP-Test-Failure-causes-KotlinJvmTestExecutorexecute1-does-not-define-failure
@Suppress("TOO_LONG_FUNCTION")
class SarifFixAdapterTest {
    private val diffGenerator = DiffRowGenerator(
        showInlineDiffs = true,
        mergeOriginalRevised = false,
        inlineDiffByWord = false,
        oldTag = { _, start -> if (start) "[" else "]" },
        newTag = { _, start -> if (start) "<" else ">" },
    )

    @Test
    @Suppress("TOO_LONG_FUNCTION")
    fun `should read SARIF report`() {
        val uri = "file:///C:/dev/sarif/sarif-tutorials/samples/Introduction/simple-example.js"
        val sarif = """
            {
              "version": "2.1.0",
              "${'$'}schema": "http://json.schemastore.org/sarif-2.1.0-rtm.4",
              "runs": [
                {
                  "tool": {
                    "driver": {
                      "name": "ESLint",
                      "informationUri": "https://eslint.org",
                      "rules": [
                        {
                          "id": "no-unused-vars",
                          "shortDescription": {
                            "text": "disallow unused variables"
                          },
                          "helpUri": "https://eslint.org/docs/rules/no-unused-vars"
                        }
                      ]
                    }
                  },
                  "artifacts": [
                    {
                      "location": {
                        "uri": "$uri"
                      }
                    }
                  ],
                  "results": [
                    {
                      "level": "error",
                      "message": {
                        "text": "'x' is assigned a value but never used."
                      },
                      "locations": [
                        {
                          "physicalLocation": {
                            "artifactLocation": {
                              "uri": "$uri",
                              "index": 0
                            },
                            "region": {
                              "startLine": 1,
                              "startColumn": 5
                            }
                          }
                        }
                      ],
                      "ruleId": "no-unused-vars",
                      "ruleIndex": 0
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val sarifSchema210: SarifSchema210 = Json.decodeFromString(sarif)

        val result = sarifSchema210.runs.first()
            .results
            ?.first()!!

        assertEquals("'x' is assigned a value but never used.", result.message.text)
        assertEquals(uri, result.locations?.first()?.physicalLocation?.artifactLocation
            ?.uri)
    }

    @Test
    fun `should read SARIF file`() {
        val sarifFilePath = "src/commonTest/resources/sarif-fixes.sarif".toPath()
        val sarifFile = readFile(sarifFilePath)
        val sarifSchema210: SarifSchema210 = Json.decodeFromString(sarifFile)

        val result = sarifSchema210.runs.first()
            .results
            ?.first()!!

        assertEquals("[ENUM_VALUE] enum values should be in selected UPPER_CASE snake/PascalCase format: NAme_MYa_sayR_", result.message.text)
    }

    @Test
    fun `should extract SARIF fix objects`() {
        val sarifFilePath = "src/commonTest/resources/sarif-fixes.sarif".toPath()
        val sarifFile = readFile(sarifFilePath)
        val sarifSchema210: SarifSchema210 = Json.decodeFromString(sarifFile)

        val sarifFixAdapter = SarifFixAdapter(
            sarifFile = sarifFilePath,
            targetFiles = emptyList()
        )
        val results = sarifSchema210.runs.map {
            sarifFixAdapter.extractFixObjects(it)
        }
        // Number of runs
        assertEquals(1, results.size)

        // Number of fixes (rules) from first run
        val numberOfFixesFromFirstRun = results.first()
        assertEquals(1, numberOfFixesFromFirstRun.size)  // that's mean, that it's only one fix

        // Number of first fix artifact changes (probably for several files)
        val firstFixArtifactChanges = numberOfFixesFromFirstRun.first()
        assertEquals(1, firstFixArtifactChanges.size)

        val firstArtifactChanges = firstFixArtifactChanges.first()

        assertEquals("src/kotlin/EnumValueSnakeCaseTest.kt".toPath(), firstArtifactChanges.filePath)

        // Number of replacements from first artifact change
        assertEquals(1, firstArtifactChanges.replacements.size)

        val changes = firstArtifactChanges.replacements.first()
        compareDeletedRegion(changes, 9, 5, 9, 19, "nameMyaSayR")
    }

    @Test
    fun `should extract SARIF fix objects 2`() {
        val sarifFilePath = "src/commonTest/resources/sarif-fixes-2.sarif".toPath()
        val sarifFile = readFile(sarifFilePath)
        val sarifSchema210: SarifSchema210 = Json.decodeFromString(sarifFile)

        val sarifFixAdapter = SarifFixAdapter(
            sarifFile = sarifFilePath,
            targetFiles = emptyList()
        )
        val results = sarifSchema210.runs.map {
            sarifFixAdapter.extractFixObjects(it)
        }

        // Number of runs
        assertEquals(1, results.size)

        // Number of fixes (rules) from first run
        val numberOfFixesFromFirstRun = results.first()
        assertEquals(2, numberOfFixesFromFirstRun.size)

        // Number of first fix artifact changes (probably for several files)
        val firstFixArtifactChanges = numberOfFixesFromFirstRun.first()
        assertEquals(1, firstFixArtifactChanges.size)

        val firstArtifactChangesForFirstFix = firstFixArtifactChanges.first()

        assertEquals("targets/autofix/autofix.py".toPath(), firstArtifactChangesForFirstFix.filePath)

        // Number of replacements from first artifact change
        assertEquals(1, firstArtifactChangesForFirstFix.replacements.size)

        val changes = firstArtifactChangesForFirstFix.replacements.first()
        compareDeletedRegion(changes, 5, 3, 5, 16, "  inputs.get(x) = 1")

        // =================================================================== //

        // Number of second fix artifact changes (probably for several files)
        val secondFixArtifactChanges = numberOfFixesFromFirstRun.last()
        assertEquals(1, secondFixArtifactChanges.size)

        val firstArtifactChangesForSecondFix = secondFixArtifactChanges.first()

        assertEquals("targets/autofix/autofix.py".toPath(), firstArtifactChangesForSecondFix.filePath)

        // Number of replacements from first artifact change
        assertEquals(1, firstArtifactChangesForSecondFix.replacements.size)

        val changes2 = firstArtifactChangesForSecondFix.replacements.first()
        compareDeletedRegion(changes2, 6, 3, 6, 28, "  if inputs.get(x + 1) == True:")
    }

    @Test
    fun `should extract SARIF fix objects 3`() {
        val sarifFilePath = "src/commonTest/resources/sarif-fixes-3.sarif".toPath()
        val sarifFile = readFile(sarifFilePath)
        val sarifSchema210: SarifSchema210 = Json.decodeFromString(sarifFile)

        val sarifFixAdapter = SarifFixAdapter(
            sarifFile = sarifFilePath,
            targetFiles = emptyList()
        )
        val results = sarifSchema210.runs.map {
            sarifFixAdapter.extractFixObjects(it)
        }
        // Number of runs
        assertEquals(1, results.size)

        // Number of fixes (rules) from first run
        val numberOfFixesFromFirstRun = results.first()
        assertEquals(1, numberOfFixesFromFirstRun.size)  // that's mean, that it's only one fix

        // Number of first fix artifact changes (probably for several files)
        val firstFixArtifactChanges = numberOfFixesFromFirstRun.first()
        assertEquals(2, firstFixArtifactChanges.size)

        val firstArtifactChanges = firstFixArtifactChanges.first()

        assertEquals("src/kotlin/Test1.kt".toPath(), firstArtifactChanges.filePath)

        // Number of replacements from first artifact change
        assertEquals(1, firstArtifactChanges.replacements.size)

        val changes = firstArtifactChanges.replacements.first()
        compareDeletedRegion(changes, 9, 5, 9, 19, "nameMyaSayR")

        // ========================================================= //

        val secondArtifactChanges = firstFixArtifactChanges.last()

        assertEquals(secondArtifactChanges.filePath, "src/kotlin/Test2.kt".toPath())

        // Number of replacements from second artifact change
        assertEquals(1, secondArtifactChanges.replacements.size)

        val changes2 = secondArtifactChanges.replacements.first()
        compareDeletedRegion(changes2, 9, 5, 9, 19, "nameMyaSayR")
    }

    @Test
    fun `should extract SARIF fix objects 4`() {
        val sarifFilePath = "src/commonTest/resources/sarif-warn-and-fixes.sarif".toPath()
        val sarifFile = readFile(sarifFilePath)
        val sarifSchema210: SarifSchema210 = Json.decodeFromString(sarifFile)

        val sarifFixAdapter = SarifFixAdapter(
            sarifFile = sarifFilePath,
            targetFiles = emptyList()
        )
        val results = sarifSchema210.runs.map {
            sarifFixAdapter.extractFixObjects(it)
        }

        // Number of runs
        assertEquals(1, results.size)

        // Number of fixes (rules) from first run
        val numberOfFixesFromFirstRun = results.first()
        assertEquals(1, numberOfFixesFromFirstRun.size)  // that's mean, that it's only one fix

        // Number of first fix artifact changes (probably for several files)
        val firstFixArtifactChanges = numberOfFixesFromFirstRun.first()
        assertEquals(2, firstFixArtifactChanges.size)

        val firstArtifactChanges = firstFixArtifactChanges.first()

        assertEquals("needsfix/NeedsFix.cs".toPath(), firstArtifactChanges.filePath)

        // Number of replacements from first artifact change
        assertEquals(1, firstArtifactChanges.replacements.size)

        val changes = firstArtifactChanges.replacements.first()
        compareDeletedRegion(changes, 7, 17, null, 22, "word")

        val secondArtifactChanges = firstFixArtifactChanges.last()

        assertEquals("needsfix/NeedsFix.cs".toPath(), secondArtifactChanges.filePath)

        // Number of replacements from second artifact change
        assertEquals(1, secondArtifactChanges.replacements.size)

        val changes2 = secondArtifactChanges.replacements.first()
        compareDeletedRegion(changes2, 7, 17, null, 23, null)
    }

    @Test
    fun `sarif fix test`() {
        val sarifFilePath = "src/commonTest/resources/sarif-fixes.sarif".toPath()
        val testFile = "src/commonTest/resources/src/kotlin/EnumValueSnakeCaseTest.kt".toPath()

        val sarifFixAdapter = SarifFixAdapter(
            sarifFile = sarifFilePath,
            targetFiles = listOf(testFile)
        )

        val processedFile = sarifFixAdapter.process().first()

        val diff = calculateDiff(testFile, processedFile)

        val expectedDelta =
                """
                    ChangeDelta, position 8, lines:
                    -    [NA]me[_]M[Y]a[_s]ayR[_]
                    +    <na>meM<y>a<S>ayR
                """.trimIndent()

        assertEquals(expectedDelta, diff.trimIndent())
    }

    @Test
    fun `sarif fix test 2`() {
        val sarifFilePath = "src/commonTest/resources/sarif-fixes-2.sarif".toPath()
        val testFile = "src/commonTest/resources/targets/autofix/autofix.py".toPath()

        val sarifFixAdapter = SarifFixAdapter(
            sarifFile = sarifFilePath,
            targetFiles = listOf(testFile)
        )

        val processedFile = sarifFixAdapter.process().first()

        val diff = calculateDiff(testFile, processedFile)

        val expectedDelta =
                """
                    ChangeDelta, position 4, lines:
                    -  inputs[[]x[]] = 1
                    +  <  >inputs<.get(>x<)> = 1
                    
                    
                    -  if inputs[[]x + 1[]] == True:
                    +  <  >if inputs<.get(>x + 1<)> == True:
                """.trimIndent()

        assertEquals(expectedDelta, diff.trimIndent())
    }

    @Test
    fun `sarif fix test 3`() {
        val testFiles = listOf(
            "src/commonTest/resources/src/kotlin/Test1.kt".toPath(),
            "src/commonTest/resources/src/kotlin/Test2.kt".toPath()
        )
        val sarifFilePath = "src/commonTest/resources/sarif-fixes-3.sarif".toPath()
        val sarifFixAdapter = SarifFixAdapter(
            sarifFile = sarifFilePath,
            targetFiles = testFiles
        )

        val processedFiles = sarifFixAdapter.process()
        val firstProcessedFile = processedFiles.first()
        val secondProcessedFile = processedFiles.last()

        val diff = calculateDiff(testFiles.first(), firstProcessedFile)
        val expectedDelta =
                """
                        ChangeDelta, position 8, lines:
                        -    [NA]me[_]M[Y]a[_s]ayR[_]
                        +    <na>meM<y>a<S>ayR
                """.trimIndent()

        assertEquals(diff.trimIndent(), expectedDelta)

        // ============================================================ //

        val diff2 = calculateDiff(testFiles.last(), secondProcessedFile)
        val expectedDelta2 =
                """
                        ChangeDelta, position 8, lines:
                        -    [NA]me[_]M[Y]a[_s]ayR[_]
                        +    <na>meM<y>a<S>ayR
                """.trimIndent()

        assertEquals(expectedDelta2, diff2.trimIndent())
    }

    @Test
    fun `sarif fix test 4`() {
        val sarifFilePath = "src/commonTest/resources/sarif-warn-and-fixes.sarif".toPath()
        val testFile = "src/commonTest/resources/needsfix/NeedsFix.cs".toPath()

        val sarifFixAdapter = SarifFixAdapter(
            sarifFile = sarifFilePath,
            targetFiles = listOf(testFile)
        )

        val processedFile = sarifFixAdapter.process().first()

        val diff = calculateDiff(testFile, processedFile)

        val expectedDelta =
                """
                    ChangeDelta, position 6, lines:
                    -        // This wo[o]rd is spelled wrong.
                    +        // This word is spelled wrong.
                """.trimIndent()

        assertEquals(expectedDelta, diff.trimIndent())
    }

    @Test
    fun `sarif multiline fix`() {
        val sarifFilePath = "src/commonTest/resources/sarif-multiline-fixes.sarif".toPath()
        val testFile = "src/commonTest/resources/src/kotlin/EnumValueSnakeCaseTest.kt".toPath()

        val sarifFixAdapter = SarifFixAdapter(
            sarifFile = sarifFilePath,
            targetFiles = listOf(testFile)
        )

        val processedFile = sarifFixAdapter.process().first()

        val diff = calculateDiff(testFile, processedFile)

        val expectedDelta =
            """
                    ChangeDelta, position 8, lines:
                    -    [NA]me[_]M[Y]a[_s]ayR[_]
                    +    <na>meM<y>a<S>ayR
                """.trimIndent()

        assertEquals(expectedDelta, diff.trimIndent())
    }

    @Test
    fun `sarif multiline fix 2`() {
        val sarifFilePath = "src/commonTest/resources/sarif-multiline-fixes-2.sarif".toPath()
        val testFile = "src/commonTest/resources/src/kotlin/EnumValueSnakeCaseTest.kt".toPath()

        val sarifFixAdapter = SarifFixAdapter(
            sarifFile = sarifFilePath,
            targetFiles = listOf(testFile)
        )

        val processedFile = sarifFixAdapter.process().first()

        val diff = calculateDiff(testFile, processedFile)

        val expectedDelta =
            """
                    ChangeDelta, position 8, lines:
                    -    [NA]me[_]M[Y]a[_s]ayR[_]
                    +    <na>meM<y>a<S>ayR
                    
                    InsertDelta(source=[position: 10, size: 0, lines: []], target=[position: 10, size: 1, lines: [// comment]])
                """.trimIndent()

        assertEquals(expectedDelta, diff.trimIndent())
    }

    @Test
    fun `sarif multiline fix 3`() {
        val sarifFilePath = "src/commonTest/resources/sarif-multiline-fixes-3.sarif".toPath()
        val testFile = "src/commonTest/resources/src/kotlin/EnumValueSnakeCaseTest.kt".toPath()

        val sarifFixAdapter = SarifFixAdapter(
            sarifFile = sarifFilePath,
            targetFiles = listOf(testFile)
        )

        val processedFile = sarifFixAdapter.process().first()

        val diff = calculateDiff(testFile, processedFile)

        val expectedDelta =
            """
                ChangeDelta, position 7, lines:
                -    // ;warn:9:5: [ENUM_VALUE] [enum value]s[ sh]o[uld b]e [i]n[ s]e[lected] [UP<br/>PER_CASE snake/Pas]c[alCase f]o[r]m[at: NA]me[_MYa_sayR_{{.*}}]
                +    // ;warn:9:5: [ENUM_VALUE] so<m>e ne<w> comme<nt>
                
                
                -    [NA]me[_]MYa_sayR_
                +    <na>meMYa_sayR_
                """.trimIndent()

        assertEquals(expectedDelta, diff.trimIndent())
    }

    @Suppress("TOO_MANY_PARAMETERS")
    private fun compareDeletedRegion(
        changes: Replacement,
        actualStartLine: Long,
        actualStartColumn: Long,
        actualEndLine: Long?,
        actualEndColumn: Long,
        actualInsertedText: String?,
    ) {
        assertEquals(changes.deletedRegion.startLine, actualStartLine)
        assertEquals(changes.deletedRegion.startColumn, actualStartColumn)
        assertEquals(changes.deletedRegion.endLine, actualEndLine)
        assertEquals(changes.deletedRegion.endColumn, actualEndColumn)
        assertEquals(changes.insertedContent?.text, actualInsertedText)
    }

    private fun Patch<String>.formatToString() = deltas.joinToString("\n") { delta ->
        when (delta) {
            is ChangeDelta -> diffGenerator
                .generateDiffRows(delta.source.lines, delta.target.lines)
                .joinToString(prefix = "ChangeDelta, position ${delta.source.position}, lines:\n", separator = "\n\n") {
                    """-${it.oldLine}
                      |+${it.newLine}
                      |""".trimMargin()
                }
            else -> delta.toString()
        }
    }

    private fun calculateDiff(testFile: Path, processedFile: Path) = diff(readLines(testFile), readLines(processedFile)).let { patch ->
        if (patch.deltas.isEmpty()) {
            ""
        } else {
            patch.formatToString()
        }
    }
}
