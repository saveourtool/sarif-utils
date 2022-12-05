/**
 * Utility methods to work with file system
 */

package com.saveourtool.sarifutils.cli.files

import okio.FileSystem
import okio.Path

expect val fs: FileSystem

/**
 * @param path a path to a file
 * @return string from the file
 */
fun FileSystem.readFile(path: Path): String = this.read(path) {
    this.readUtf8()
}
