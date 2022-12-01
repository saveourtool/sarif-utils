package com.saveourtool.sarifutils.cli.config

import io.github.detekt.sarif4k.Replacement
import okio.Path


typealias RuleReplacements = List<FileReplacements>

data class FileReplacements(
    val filePath: Path,
    val replacements: List<Replacement>
)