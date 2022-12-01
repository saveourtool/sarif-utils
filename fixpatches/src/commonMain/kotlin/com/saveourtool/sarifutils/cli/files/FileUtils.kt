package com.saveourtool.sarifutils.cli.files

import okio.FileSystem
import okio.Path

expect val fs: FileSystem

fun FileSystem.readFile(path: Path): String = this.read(path) {
    this.readUtf8()
}