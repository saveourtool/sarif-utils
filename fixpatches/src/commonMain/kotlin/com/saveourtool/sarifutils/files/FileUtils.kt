/**
 * Utility methods to work with file system
 */

package com.saveourtool.sarifutils.files

import com.saveourtool.okio.absolute
import okio.FileSystem
import okio.IOException
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
    return (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / dirName).createDirectories()
}

/**
 * Returns the _real_ path of an existing file.
 *
 * If this path is relative then its absolute path is first obtained, as if by
 * invoking the [Path.absolute] method.
 *
 * @return an absolute path represent the _real_ path of the file located by
 *   this object.
 * @throws IOException if the file does not exist or an I/O error occurs.
 * @see Path.toRealPathSafe
 */
@Throws(IOException::class)
internal fun Path.toRealPath(): Path =
        fs.canonicalize(this)

/**
 * Same as [Path.toRealPath], but doesn't throw an exception if the path doesn't
 * exist.
 *
 * @return an absolute path represent the _real_ path of the file located by
 *   this object, or an absolute normalized path if the file doesn't exist.
 * @see Path.toRealPath
 */
internal fun Path.toRealPathSafe(): Path =
        try {
            toRealPath()
        } catch (_: IOException) {
            absolute().normalized()
        }

/**
 * Checks if the file located by this path points to the same file or directory
 * as [other].
 *
 * @param other the other path.
 * @return `true` if, and only if, the two paths locate the same file.
 * @throws IOException if an I/O error occurs.
 * @see Path.isSameFileAsSafe
 */
@Throws(IOException::class)
internal fun Path.isSameFileAs(other: Path): Boolean =
        this.toRealPath() == other.toRealPath()

/**
 * Checks if the file located by this path points to the same file or directory
 * as [other]. Same as [Path.isSameFileAs], but doesn't throw an exception if
 * any of the paths doesn't exist.
 *
 * @param other the other path.
 * @return `true` if the two paths locate the same file.
 * @see Path.isSameFileAs
 */
internal fun Path.isSameFileAsSafe(other: Path): Boolean =
        try {
            this.isSameFileAs(other)
        } catch (_: IOException) {
            this.toRealPathSafe() == other.toRealPathSafe()
        }

/**
 * Creates a directory, ensuring that all nonexistent parent directories exist
 * by creating them first.
 *
 * If the directory already exists, this function does not throw an exception.
 *
 * @return this path.
 * @throws IOException if an I/O error occurs.
 */
@Throws(IOException::class)
internal fun Path.createDirectories(): Path {
    fs.createDirectories(this)
    return this
}

/**
 * Same as [Path.relativeTo], but doesn't throw an [IllegalArgumentException] if
 * `this` and [other] are both absolute paths, but have different file system
 * roots.
 *
 * @param other the other path.
 * @return this path relativized against [other],
 *   or `this` if this and other have different file system roots.
 */
internal fun Path.relativeToSafe(other: Path): Path {
    return try {
        relativeTo(other)
    } catch (_: IllegalArgumentException) {
        this
    }
}
