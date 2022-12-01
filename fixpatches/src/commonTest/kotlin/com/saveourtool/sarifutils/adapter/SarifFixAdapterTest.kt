package com.saveourtool.sarifutils.adapter

import com.saveourtool.sarifutils.cli.adapter.SarifFixAdapter
import com.saveourtool.sarifutils.cli.files.fs
import com.saveourtool.sarifutils.cli.files.readFile
import io.github.detekt.sarif4k.SarifSchema210
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath

class SarifFixAdapterTest {
    @Test
    @Suppress("TOO_LONG_FUNCTION")
    fun `simple check - should read SARIF report`() {
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
        val sarifFilePath = "src/commonTest/resources/sarif-fixes.json".toPath()
        val sarifFile = fs.readFile(sarifFilePath)
        val sarifSchema210 = Json.decodeFromString<SarifSchema210>(sarifFile)

        val result = sarifSchema210.runs.first()
            .results
            ?.first()!!

        assertEquals(result.message.text, "[ENUM_VALUE] enum values should be in selected UPPER_CASE snake/PascalCase format: NAme_MYa_sayR_")
    }

    @Test
    fun `sarif fix adapter test`() {
        val sarifFilePath = "src/commonTest/resources/sarif-fixes.json".toPath()
        val sarifFixAdapter = SarifFixAdapter(
            sarifFile = sarifFilePath,
            testFiles = emptyList()
        )

        sarifFixAdapter.process()
    }
}
