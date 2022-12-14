package com.saveourtool.sarifutils.utils

import com.saveourtool.sarifutils.cli.utils.getUriBaseIdForArtifactLocation
import com.saveourtool.sarifutils.cli.utils.resolveBaseUri
import io.github.detekt.sarif4k.SarifSchema210
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals

class SarifUtilsTest {
    private fun getSarif(
        originalUriBaseIds: String,
        uriBaseIdInArtifactLocation: String,
        uriBaseIdInLocations: String,
    ) = """     
        {
          "${'$'}schema": "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json",
          "version": "2.1.0",
          "runs": [
            {
              $originalUriBaseIds 
              "results": [
                {
                  "fixes": [
                    {
                      "artifactChanges": [
                        {
                          "artifactLocation": {
                            "uri": "src/kotlin/EnumValueSnakeCaseTest.kt"${if (uriBaseIdInArtifactLocation.isNotBlank()) "," else ""}
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
                          "uri": "src/kotlin/EnumValueSnakeCaseTest.kt"${if (uriBaseIdInLocations.isNotBlank()) "," else ""}
                          $uriBaseIdInLocations
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
    fun `should resolve base uri 1`() {
        val sarif = getSarif(
            originalUriBaseIds = """
            "originalUriBaseIds": {
                "%SRCROOT%": {
                  "uri": "file:///home/projects/"
                }
              },
                """,
            uriBaseIdInArtifactLocation = "\"uriBaseId\": \"%SRCROOT%\"",
            uriBaseIdInLocations = ""
        )
        assertBaseUri(sarif, "/home/projects".toPath())
    }


    @Test
    fun `should resolve base uri 2`() {
        val sarif = getSarif(
            originalUriBaseIds = """
            "originalUriBaseIds": {
                "%SRCROOT%": {
                  "uri": "file:///home/projects/"
                }
              },
                """,
            uriBaseIdInArtifactLocation = "",
            uriBaseIdInLocations = "\"uriBaseId\": \"%SRCROOT%\""
        )
        assertBaseUri(sarif, "/home/projects".toPath())
    }

    @Test
    fun `should resolve base uri 3`() {
        val sarif = getSarif(
            originalUriBaseIds = "",
            uriBaseIdInArtifactLocation = "\"uriBaseId\": \"%SRCROOT%\"",
            uriBaseIdInLocations = ""
        )
        assertBaseUri(sarif, ".".toPath())
    }

    @Test
    fun `should resolve base uri 4`() {
        val sarif = getSarif(
            originalUriBaseIds = "",
            uriBaseIdInArtifactLocation = "",
            uriBaseIdInLocations = "\"uriBaseId\": \"%SRCROOT%\""
        )
        assertBaseUri(sarif, ".".toPath())
    }

    @Test
    fun `should resolve base uri 5`() {
        val sarif = getSarif(
            originalUriBaseIds = "",
            uriBaseIdInArtifactLocation = "\"uriBaseId\": \"file:///C:/projects/\"",
            uriBaseIdInLocations = ""
        )
        assertBaseUri(sarif, "/home/projects".toPath())
    }

    @Test
    fun `should resolve base uri 6`() {
        val sarif = getSarif(
            originalUriBaseIds = "",
            uriBaseIdInArtifactLocation = "\"uriBaseId\": \"C:/projects/\"",
            uriBaseIdInLocations = ""
        )
        assertBaseUri(sarif, "C:/projects".toPath())
    }

    @Test
    fun `should resolve base uri 7`() {
        val sarif = getSarif(
            originalUriBaseIds = "",
            uriBaseIdInArtifactLocation = "",
            uriBaseIdInLocations = "\"uriBaseId\": \"file:///home/projects/\""
        )
        assertBaseUri(sarif, "/home/projects".toPath())
    }

    @Test
    fun `should resolve base uri 8`() {
        val sarif = getSarif(
            originalUriBaseIds = """
                "originalUriBaseIds": {
                     "PROJECTROOT": {
                        "uri": "file:///C:/Users/Mary/code/TheProject/",
                        "description": {
                          "text": "The root directory for all project files."
                        }
                     },
                      "SRCROOT": {
                        "uri": "src",
                        "uriBaseId": "PROJECTROOT",
                        "description": {
                          "text": "The root of the source tree."
                        }
                      }
                }
                """,
            uriBaseIdInArtifactLocation = "",
            uriBaseIdInLocations = "\"uriBaseId\": \"SRCROOT\""
        )
        assertBaseUri(sarif, "C:/Users/Mary/code/TheProject/src".toPath())
    }


    private fun assertBaseUri(sarif: String, expectedPath: Path) {
        val sarifSchema210: SarifSchema210 = Json.decodeFromString(sarif)

        val run = sarifSchema210.runs.first()

        val result = run
            .results
            ?.first()!!

        val artifactLocation = result.fixes!!.first().artifactChanges.first().artifactLocation

        assertEquals(
            expectedPath,
            resolveBaseUri(
                artifactLocation.getUriBaseIdForArtifactLocation(result),
                run
            )
        )
    }
}
