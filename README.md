# Sarif-Utils

Current library provide the set of utilities, directed to work with
`.sarif` files.\
**SARIF - Static Analysis Results Interchange Format**, is a [standard](https://sarifweb.azurewebsites.net/),
JSON-based format for the output of static analysis tools, elaborated by Microsoft.

### Fix-Patches module

There are different static analysis tool, but generally all of them
are united by one concept - to detect software vulnerabilities.

Thus, at the output of the work of static analysis tool, there will be the set of `warnings`,\
which are pointed to the code smells. Some tools have gone further and provide an opportunity\
to automatically `fix` such code smells.

Accordingly, `SARIF` files, which could be the one of possible formats for reports of static analysis tools,
will contain corresponding sections for `warnings` and `fixes`, obtained by such tools, 
which are pointed to the corresponding vulnerabilities in target files.

`Fix-Patches` module is set to work with `fix object` sections from `SARIF` - formatted files.

* A `fix object` represents a proposed fix for the problem, indicated by tool.\
It specifies a set of artifacts to modify. For each artifact, it specifies regions to remove, and provides new content to insert.
* `Fix-Patches`, in its turn, will parse such sections, create copy of target files, which are presented in `SARIF`,
and automatically apply fixes to these copies.

The result output will contain the list of paths to the copies of provided target files with applied fixes. 

**Note:** If `SARIF` file will contain multiple fixes for the same region in one file, only the first fix will be appied.

#### How to use:

Library provides easy-to-use API for fix patches applying, it's just need to provide
the path to `SARIF` file, which contain the list of fixes (`fix objects`) for target files and the list of paths to these files,
in the manner, in which they are presented in `SARIF` (via absolute/relative paths).

```kotlin
    val processedFiles: List<Path> = SarifFixAdapter(
                                        sarifFile = sarifFilePath,
                                        targetFiles = listOfTargetFilesPaths,
                                        testRoot = "/directory/with/root/save.toml".toPath()
                                      ).process()
```
