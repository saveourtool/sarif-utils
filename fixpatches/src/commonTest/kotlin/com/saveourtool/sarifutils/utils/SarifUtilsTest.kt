package com.saveourtool.sarifutils.utils

import com.saveourtool.sarifutils.cli.utils.getUriBaseIdForArtifactLocation
import com.saveourtool.sarifutils.cli.utils.resolveBaseUri
import io.github.detekt.sarif4k.SarifSchema210
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals

class SarifUtilsTest {
    private fun getSarif(
        uri: String,
        uriBaseIdInArtifactLocation: String,
        uriBaseIdInLocations: String,
    ) = """     
        {
          "${'$'}schema": "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json",
          "version": "2.1.0",
          "runs": [
            {
              "originalUriBaseIds": {
                "%SRCROOT%": {
                  "uri": "file://D:/projects/"
                }
              },
              "results": [
                {
                  "fixes": [
                    {
                      "artifactChanges": [
                        {
                          "artifactLocation": {
                            "uri": "src/kotlin/EnumValueSnakeCaseTest.kt"
                            $uriBaseIdInArtifactLocation
                          },
                          "replacements": [
                            {
                              "deletedRegion": {
                                "endColumn": 19,
                                "endLine": 9,
                                "startColumn": 5,
                                "startLine": 9
                              },
                              "insertedContent": {
                                "text": "nameMyaSayR"
                              }
                            }
                          ]
                        }
                      ],
                      "description": {
                        "text": "[ENUM_VALUE] enum values should be in selected UPPER_CASE snake/PascalCase format: NAme_MYa_sayR_"
                      }
                    }
                  ],
                  "locations": [
                    {
                      "physicalLocation": {
                        "artifactLocation": {
                          "uri": "src/kotlin/EnumValueSnakeCaseTest.kt"
                          "uriBaseId": "%SRCROOT%"
                        },
                        "region": {
                          "endColumn": 19,
                          "endLine": 9,
                          "snippet": {
                            "text": "NAme_MYa_sayR_"
                          },
                          "startColumn": 5,
                          "startLine": 9
                        }
                      }
                    }
                  ],
                  "message": {
                    "text": "[ENUM_VALUE] enum values should be in selected UPPER_CASE snake/PascalCase format: NAme_MYa_sayR_"
                  },
                  "ruleId": "diktat-ruleset:identifier-naming"
                }
              ],
              "tool": {
                "driver": {
                  "downloadUri": "https://github.com/pinterest/ktlint/releases/tag/0.42.0",
                  "fullName": "ktlint",
                  "informationUri": "https://github.com/pinterest/ktlint/",
                  "language": "en",
                  "name": "ktlint",
                  "organization": "pinterest",
                  "rules": [
                  ],
                  "semanticVersion": "0.42.0",
                  "version": "0.42.0"
                }
              }
            }
          ]
        }
        """.trimIndent()


    @Test
    @Suppress("TOO_LONG_FUNCTION")
    fun `should read SARIF report`() {
        val uri = "file:///C:/dev/sarif/sarif-tutorials/samples/Introduction/simple-example.js"
        val sarif = getSarif(
            uri,
            "",
            "\",uriBaseId\": \"%SRCROOT%\""
        )
        val sarifSchema210: SarifSchema210 = Json.decodeFromString(sarif)

        val run = sarifSchema210.runs.first()

        val result = run
            .results
            ?.first()!!

        val artifactLocation = result.fixes!!.first().artifactChanges.first().artifactLocation

        assertEquals(
            resolveBaseUri(
                artifactLocation.getUriBaseIdForArtifactLocation(result),
                run
            ),
            "D:/projects".toPath()
        )

    }
}