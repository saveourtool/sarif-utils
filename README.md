# Sarif-Utils

Current library provide the set of utilities, directed to work with
`.sarif` files.\
**SARIF - Static Analysis Results Interchange Format**, is a standard,
JSON-based format for the output of static analysis tools, elaborated by Microsoft.

### Fix-Patches

There are different static analysis tool, but generally all of them
are united by one concept - to detect software vulnerabilities.\
Thus, at the output of the work of static analysis tool there will be the set of `warnings`,\
which point to the code smells. Some tools have gone further and provide an opportunity\
to automatically `fix` the above code smells.\
Accordingly, `.sarif` files, which could be the one of possible formats for reports of static analysis tools,
will contain corresponding sections for `warnings` and `fixes`.

`Fix-Patches` module is set to work with `fix` sections from `.sarif` files.\
A `fix` object represents a proposed fix for the problem indicated by tool.\
It specifies a set of artifacts to modify. For each artifact, it specifies regions to remove, and provides new content to insert


This project process Sarif files with `fix object` sections and apply changes to the test files.