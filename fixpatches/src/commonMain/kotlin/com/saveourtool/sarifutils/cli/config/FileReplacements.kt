package com.saveourtool.sarifutils.cli.config

import io.github.detekt.sarif4k.Replacement
import okio.Path

typealias RuleReplacements = List<FileReplacements>

/**
 * @property filePath path to the file
 * @property replacements list of fix replacements for this [file]
 */
data class FileReplacements(
    val filePath: Path,
    val replacements: List<Replacement>
)
