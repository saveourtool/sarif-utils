/**
 * Utility methods to work with file system
 */

@file:JvmName("FileUtilsJvm")

package com.saveourtool.sarifutils.cli.files

import okio.FileSystem

actual val fs: FileSystem = FileSystem.SYSTEM
