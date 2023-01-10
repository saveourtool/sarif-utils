package com.saveourtool.sarifutils.config

import io.github.detekt.sarif4k.Replacement
import okio.Path

/**
 * The list of all replacements from one rule (for different files)
 */
typealias RuleReplacements = List<FileReplacements>

/**
 * @property filePath path to the file
 * @property replacements list of artifact changes for this [file]
 */
data class FileReplacements(
    val filePath: Path,
    val replacements: List<Replacement>
)
