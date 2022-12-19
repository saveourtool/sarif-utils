/**
 * Utility methods to work with file system
 */

package com.saveourtool.sarifutils.cli.files

import okio.FileSystem
import okio.Path
import kotlin.random.Random

expect val fs: FileSystem

/**
 * @param path a path to a file
 * @return list of strings from the file
 */
internal fun readLines(path: Path): List<String> = fs.read(path) {
    generateSequence { readUtf8Line() }.toList()
}

/**
 * @param path a path to a file
 * @return string from the file
 */
internal fun readFile(path: Path): String = fs.read(path) {
    this.readUtf8()
}

/**
 * Create a temporary directory
 *
 * @param prefix will be prepended to directory name
 * @return a [Path] representing the created directory
 */
internal fun createTempDir(prefix: String = "sarifutils-tmp"): Path {
    val dirName = "$prefix-${Random.nextInt()}"
    return (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / dirName).also {
        fs.createDirectories(it)
    }
}
