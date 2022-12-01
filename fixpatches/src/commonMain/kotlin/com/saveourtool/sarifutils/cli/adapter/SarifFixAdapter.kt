package com.saveourtool.sarifutils.cli.adapter

import com.saveourtool.sarifutils.cli.files.fs
import com.saveourtool.sarifutils.cli.files.readFile
import io.github.detekt.sarif4k.SarifSchema210
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okio.Path

class SarifFixAdapter(
    private val sarifFile: Path,
    private val testFiles: List<Path>
) {
    fun process() {
        val sarifSchema210 = Json.decodeFromString<SarifSchema210>(
            fs.readFile(sarifFile)
        )
        val fixPathces: List<FixPatch> = extractFixObject(sarifSchema210)

    }

    private fun extractFixObject(sarifSchema210: SarifSchema210): List<FixPatch> {
        TODO("Not yet implemented")
    }
}

data class FixPatch(
    val filePath: Path,
    // TODO Type?
    val fixes: List<Any>
)