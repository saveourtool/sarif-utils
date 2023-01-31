/**
 * Utility methods to work with log settings.
 */

package com.saveourtool.sarifutils.utils

import mu.KotlinLoggingConfiguration
import mu.KotlinLoggingLevel

actual fun setLoggingLevel() {
    KotlinLoggingConfiguration.logLevel = KotlinLoggingLevel.INFO
}
