/**
 * Utility methods to work with file system
 */

package com.saveourtool.sarifutils.files

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
 * Write [content] to the [targetFile], some of the elements in [content] could represent the multiline strings,
 * which already contain all necessary escape characters, in this case, write them as-is, otherwise add newline at the end
 *
 * @param targetFile file whether to write [content]
 * @param content data to be written
 * @return [Unit]
 */
internal fun writeContentWithNewLinesToFile(targetFile: Path, content: List<String>) = fs.write(targetFile) {
    content.forEach { line ->
        if (!line.contains('\n')) {
            writeUtf8(line + '\n')
        } else {
            writeUtf8(line)
        }
    }
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
