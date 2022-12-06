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
fun FileSystem.readLines(path: Path): List<String> = this.read(path) {
    generateSequence { readUtf8Line() }.toList()
}

/**
 * @param path a path to a file
 * @return string from the file
 */
fun FileSystem.readFile(path: Path): String = this.read(path) {
    this.readUtf8()
}

/**
 * Create file in [this] [FileSystem], denoted by [Path] [path]
 *
 * @param path path to a new file
 * @return [path]
 */
fun FileSystem.createFile(path: Path): Path {
    sink(path).close()
    return path
}

/**
 * Create a temporary directory
 *
 * @param prefix will be prepended to directory name
 * @return a [Path] representing the created directory
 */
fun FileSystem.createTempDir(prefix: String = "sarifutils-tmp"): Path {
    val dirName = "$prefix-${Random.nextInt()}"
    return (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / dirName).also {
        createDirectory(it)
    }
}

/**
 * Copy file content from [from] into [into]
 *
 * @param [from] path to the file, from which content need to be copied
 * @param [into] path to the file, into which content need to be copied
 * @param mustCreate whether to create file on [into] path
 */
fun FileSystem.copyFileContent(
    from: Path,
    into: Path,
    mustCreate: Boolean = false
) {
    fs.write(into, mustCreate) {
        fs.readLines(from).forEach {
            write(
                (it + "\n").encodeToByteArray()
            )
        }
    }
}
