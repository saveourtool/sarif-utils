# Sarif-Utils

Current library provide the set of utilities, directed to work with
`.sarif` files.\
**SARIF - Static Analysis Results Interchange Format**, is a standard,
JSON-based format for the output of static analysis tools, elaborated by Microsoft.

### Fix-Patches module

There are different static analysis tool, but generally all of them
are united by one concept - to detect software vulnerabilities.\
Thus, at the output of the work of static analysis tool there will be the set of `warnings`,\
which point to the code smells. Some tools have gone further and provide an opportunity\
to automatically `fix` such code smells.\
Accordingly, `.sarif` files, which could be the one of possible formats for reports of static analysis tools,
will contain corresponding sections for `warnings` and `fixes`, obtained by such tools.

`Fix-Patches` module is set to work with `fix object` sections from `SARIF` formatted files.

* A `fix object` represents a proposed fix for the problem indicated by tool.\
It specifies a set of artifacts to modify. For each artifact, it specifies regions to remove, and provides new content to insert.
* `Fix-Patches`, in its turn will parse such sections, create copy of your test files, which are presented in `SARIF`,
and automatically apply fixes from `.sarif` to these test copies.

The result output will contain the list of paths to the copies of your test files with applied fixes. 

**Note:** If `.sarif` file will contain multiple fixes for the same line in one file, only the first fix will apply.

#### How to use

Library provide easy-to-use API for fix patches applying, you just need to provide
the path to your `SARIF` file with expected fixes and the list of paths to test files which are presented in `SARIF`.

```kotlin
    val processedFiles: List<Path> = SarifFixAdapter(
                                        sarifFile = sarifFilePath,
                                        testFiles = listOfTestFilesPaths
                                      ).process()
```