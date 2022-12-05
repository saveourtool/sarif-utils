// ;warn:2:9: [PACKAGE_NAME_INCORRECT_PREFIX] package name should start from company's domain: com.saveourtool.sarifutils{{.*}}
package com.saveourtool.diktat.test.resources.test.paragraph1.naming.enum_

// ;warn:4:1: [MISSING_KDOC_TOP_LEVEL] all public and internal top-level classes and functions should have Kdoc: EnumValueSnakeCaseTest (cannot be auto-corrected){{.*}}
// ;warn:9:5: [ENUMS_SEPARATED] enum is incorrectly formatted: enums must end with semicolon{{.*}}
// ;warn:9:5: [ENUMS_SEPARATED] enum is incorrectly formatted: last enum entry must end with a comma{{.*}}
enum class EnumValueSnakeCaseTest {
    // ;warn:10:5: [ENUM_VALUE] enum values should be in selected UPPER_CASE snake/PascalCase format: NAme_MYa_sayR_{{.*}}
    NAme_MYa_sayR_
}
