package com.saveourtool.sarifutils.adapter

import com.saveourtool.sarifutils.cli.adapter.SarifFixAdapter
import com.saveourtool.sarifutils.cli.files.copyFileContent
import com.saveourtool.sarifutils.cli.files.createFile
import com.saveourtool.sarifutils.cli.files.createTempDir
import com.saveourtool.sarifutils.cli.files.fs
import com.saveourtool.sarifutils.cli.files.readFile
import com.saveourtool.sarifutils.cli.files.readLines

import io.github.detekt.sarif4k.SarifSchema210
import okio.Path.Companion.toPath

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest

// https://youtrack.jetbrains.com/issue/KT-54634/MPP-Test-Failure-causes-KotlinJvmTestExecutorexecute1-does-not-define-failure
@Suppress("TOO_LONG_FUNCTION")
class SarifFixAdapterTest {
    private val tmpDir = fs.createTempDir(SarifFixAdapterTest::class.simpleName!!)

    @Test
    @Suppress("TOO_LONG_FUNCTION")
    fun `should read SARIF report`() {
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
                        "uri": "file:///C:/dev/sarif/sarif-tutorials/samples/Introduction/simple-example.js"
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
                              "uri": "file:///C:/dev/sarif/sarif-tutorials/samples/Introduction/simple-example.js",
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

        assertEquals(result.message.text, "'x' is assigned a value but never used.")
        assertEquals(result.locations?.first()?.physicalLocation?.artifactLocation
            ?.uri, "file:///C:/dev/sarif/sarif-tutorials/samples/Introduction/simple-example.js")
    }

    @Test
    fun `should read SARIF file`() {
        val sarifFilePath = "src/commonTest/resources/sarif-fixes.sarif".toPath()
        val sarifFile = fs.readFile(sarifFilePath)
        val sarifSchema210: SarifSchema210 = Json.decodeFromString(sarifFile)

        val result = sarifSchema210.runs.first()
            .results
            ?.first()!!

        assertEquals(result.message.text, "[ENUM_VALUE] enum values should be in selected UPPER_CASE snake/PascalCase format: NAme_MYa_sayR_")
    }

    @Test
    fun `should extract SARIF fix objects`() {
        val sarifFilePath = "src/commonTest/resources/sarif-fixes.sarif".toPath()
        val sarifFile = fs.readFile(sarifFilePath)
        val sarifSchema210: SarifSchema210 = Json.decodeFromString(sarifFile)

        val sarifFixAdapter = SarifFixAdapter(
            sarifFile = sarifFilePath,
            testFiles = emptyList()
        )
        val results = sarifSchema210.runs.map {
            sarifFixAdapter.extractFixObject(it)
        }
        // Number of runs
        assertEquals(results.size, 1)

        // Number of fixes (rules) from first run
        val numberOfFixesFromFirstRun = results.first()!!
        assertEquals(numberOfFixesFromFirstRun.size, 1)  // that's mean, that it's only one fix

        // Number of first fix artifact changes (probably for several files)
        val firstFixArtifactChanges = numberOfFixesFromFirstRun.first()!!
        assertEquals(firstFixArtifactChanges.size, 1)

        val firstArtifactChanges = firstFixArtifactChanges.first()

        assertEquals(firstArtifactChanges.filePath, "src/kotlin/EnumValueSnakeCaseTest.kt".toPath())

        // Number of replacements from first artifact change
        assertEquals(firstArtifactChanges.replacements.size, 1)

        val changes = firstArtifactChanges.replacements.first()
        assertEquals(changes.deletedRegion.startLine, 10)
        assertEquals(changes.deletedRegion.startColumn, 5)
        assertEquals(changes.deletedRegion.endLine, 10)
        assertEquals(changes.deletedRegion.endColumn, 19)
        assertEquals(changes.insertedContent!!.text, "nameMyaSayR")
    }

    @Test
    fun `should extract SARIF fix objects 2`() {
        val sarifFilePath = "src/commonTest/resources/sarif-fixes-2.sarif".toPath()
        val sarifFile = fs.readFile(sarifFilePath)
        val sarifSchema210: SarifSchema210 = Json.decodeFromString(sarifFile)

        val sarifFixAdapter = SarifFixAdapter(
            sarifFile = sarifFilePath,
            testFiles = emptyList()
        )
        val results = sarifSchema210.runs.map {
            sarifFixAdapter.extractFixObject(it)
        }

        // Number of runs
        assertEquals(results.size, 1)

        // Number of fixes (rules) from first run
        val numberOfFixesFromFirstRun = results.first()!!
        assertEquals(numberOfFixesFromFirstRun.size, 2)

        // Number of first fix artifact changes (probably for several files)
        val firstFixArtifactChanges = numberOfFixesFromFirstRun.first()!!
        assertEquals(firstFixArtifactChanges.size, 1)

        val firstArtifactChangesForFirstFix = firstFixArtifactChanges.first()

        assertEquals(firstArtifactChangesForFirstFix.filePath, "targets/autofix/autofix.py".toPath())

        // Number of replacements from first artifact change
        assertEquals(firstArtifactChangesForFirstFix.replacements.size, 1)

        val changes = firstArtifactChangesForFirstFix.replacements.first()
        assertEquals(changes.deletedRegion.startLine, 5)
        assertEquals(changes.deletedRegion.startColumn, 3)
        assertEquals(changes.deletedRegion.endLine, 5)
        assertEquals(changes.deletedRegion.endColumn, 12)
        assertEquals(changes.insertedContent!!.text, "  inputs.get(x) = 1")

        // ===================================================================//

        // Number of second fix artifact changes (probably for several files)
        val secondFixArtifactChanges = numberOfFixesFromFirstRun.last()!!
        assertEquals(secondFixArtifactChanges.size, 1)

        val firstArtifactChangesForSecondFix = secondFixArtifactChanges.first()

        assertEquals(firstArtifactChangesForSecondFix.filePath, "targets/autofix/autofix.py".toPath())

        // Number of replacements from first artifact change
        assertEquals(firstArtifactChangesForSecondFix.replacements.size, 1)

        val changes2 = firstArtifactChangesForSecondFix.replacements.first()
        assertEquals(changes2.deletedRegion.startLine, 6)
        assertEquals(changes2.deletedRegion.startColumn, 6)
        assertEquals(changes2.deletedRegion.endLine, 6)
        assertEquals(changes2.deletedRegion.endColumn, 19)
        assertEquals(changes2.insertedContent!!.text, "  if inputs.get(x + 1) == True:")
    }

    @Test
    fun `should extract SARIF fix objects 3`() {
        val sarifFilePath = "src/commonTest/resources/sarif-warn-and-fixes.sarif".toPath()
        val sarifFile = fs.readFile(sarifFilePath)
        val sarifSchema210: SarifSchema210 = Json.decodeFromString(sarifFile)

        val sarifFixAdapter = SarifFixAdapter(
            sarifFile = sarifFilePath,
            testFiles = emptyList()
        )
        val results = sarifSchema210.runs.map {
            sarifFixAdapter.extractFixObject(it)
        }

        // Number of runs
        assertEquals(results.size, 1)

        // Number of fixes (rules) from first run
        val numberOfFixesFromFirstRun = results.first()!!
        assertEquals(numberOfFixesFromFirstRun.size, 1)  // that's mean, that it's only one fix

        // Number of first fix artifact changes (probably for several files)
        val firstFixArtifactChanges = numberOfFixesFromFirstRun.first()!!
        assertEquals(firstFixArtifactChanges.size, 2)

        val firstArtifactChanges = firstFixArtifactChanges.first()

        assertEquals(firstArtifactChanges.filePath, "NeedsFix.cs".toPath())

        // Number of replacements from first artifact change
        assertEquals(firstArtifactChanges.replacements.size, 1)

        val changes = firstArtifactChanges.replacements.first()
        assertEquals(changes.deletedRegion.startLine, 7)
        assertEquals(changes.deletedRegion.startColumn, 17)
        assertEquals(changes.deletedRegion.endLine, null)
        assertEquals(changes.deletedRegion.endColumn, 22)
        assertEquals(changes.insertedContent!!.text, "word")

        val secondArtifactChanges = firstFixArtifactChanges.last()

        assertEquals(secondArtifactChanges.filePath, "NeedsFix.cs".toPath())

        // Number of replacements from second artifact change
        assertEquals(secondArtifactChanges.replacements.size, 1)

        val changes2 = secondArtifactChanges.replacements.first()
        assertEquals(changes2.deletedRegion.startLine, 7)
        assertEquals(changes2.deletedRegion.startColumn, 17)
        assertEquals(changes2.deletedRegion.endLine, null)
        assertEquals(changes2.deletedRegion.endColumn, 23)
        assertEquals(changes2.insertedContent, null)
    }

    @Test
    fun `sarif fix adapter test`() {
        val sarifFilePath = "src/commonTest/resources/sarif-fixes.sarif".toPath()
        val testFile = "src/commonTest/resources/src/kotlin/EnumValueSnakeCaseTest.kt".toPath()

        val testFileCopy = fs.createFile(tmpDir / "CopyEnumValueSnakeCaseTest.kt")
        fs.copyFileContent(testFile, testFileCopy)

        val res = fs.readLines(testFileCopy)


        println(res)
        return

        val sarifFixAdapter = SarifFixAdapter(
            sarifFile = sarifFilePath,
            testFiles = listOf(testFileCopy)
        )

        sarifFixAdapter.process()
    }

    @AfterTest
    fun tearDown() {
        fs.deleteRecursively(tmpDir)
    }
}
