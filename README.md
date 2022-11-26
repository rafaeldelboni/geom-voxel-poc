# geom-voxel-poc

Small [geom](https://github.com/thi-ng/geom) poc to generate and view voxel stl files.

## Installation

Download from https://github.com/rafaeldelboni/geom-voxel-poc

## Usage

Run the nrepl for this project
```bash
clojure -M:nrepl
```

Run the project directly, via `:main-opts` (`-m geom-voxel-poc.core`):
```bash
clojure -M:run
```

Run the project, overriding the name to be greeted:
```bash
clojure -M:run Via-Main
```

Run the project's tests (they'll fail until you edit them):
```bash
clojure -T:build test
```

Run the project's CI pipeline and build an uberjar (this will fail until you edit the tests to pass):
```bash
clojure -T:build ci
```

This will produce an updated `pom.xml` file with synchronized dependencies inside the `META-INF`
directory inside `target/classes` and the uberjar in `target`. You can update the version (and SCM tag)
information in generated `pom.xml` by updating `build.clj`.

If you don't want the `pom.xml` file in your project, you can remove it. The `ci` task will
still generate a minimal `pom.xml` as part of the `uber` task, unless you remove `version`
from `build.clj`.

Run that uberjar:

```bash
java -jar target/geom-voxel-poc-0.1.0-SNAPSHOT.jar
```

If you remove `version` from `build.clj`, the uberjar will become `target/geom-voxel-poc-standalone.jar`.
