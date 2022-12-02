package com.saveourtool.sarifutils.cli.files

import okio.FileSystem
import okio.Path

expect val fs: FileSystem

/**
 * @param path
 * @return
 */
fun FileSystem.readFile(path: Path): String = this.read(path) {
    this.readUtf8()
}
