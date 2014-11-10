# srclib-java [![Build Status](https://travis-ci.org/sourcegraph/srclib-java.png?branch=master)](https://travis-ci.org/sourcegraph/srclib-java)

## Requirements

srclib-java Requires Oracle JDK 8, or OpenJDK 8 to run, as well as Maven to build.

Additionally, run the following command to register `tools.jar` as an artifact
that can be included from Maven.
```
mvn install:install-file -DgroupId=com.sun -DartifactId=tools -Dversion=1.8 -Dpackaging=jar -Dfile="/usr/lib/jvm/java-8-oracle/lib/tools.jar"
```

### WARNING

`tools.jar` that comes with the Oracle 8 JDK and OpenJDK 8 are both buggy as of November 7th, 2014. com.sun.source.util.JavacTask.analyze() throws a null pointer exception on some repositories. See https://bugs.openjdk.java.net/browse/JDK-8062359?page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel for a description of the problem and a workaround. Build instructions for OpenJDK 8 are here: http://openjdk.java.net/projects/build-infra/guide.html.

## Building

srclib-java can be build and registered with the following two commands.
```
make
src toolchain add sourcegraph.com/sourcegraph/srclib-java
```

## Testing

Run `git submodule update --init` the first time to fetch the submodule test cases in `testdata/case`.

`make test` - Test in program mode

`make test-gen` - Generate new test data in program mode

## Limitations

The code currently assumes that all dependencies are stored on Maven Central,
and have a pom.xml file.
