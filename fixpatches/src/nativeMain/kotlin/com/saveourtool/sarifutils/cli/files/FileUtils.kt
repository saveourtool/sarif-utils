/**
 * Utility methods to work with file system
 */

package com.saveourtool.sarifutils.cli.files

import okio.FileSystem

actual val fs: FileSystem = FileSystem.SYSTEM
