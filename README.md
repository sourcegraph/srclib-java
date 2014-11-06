# srclib-java [![Build Status](https://travis-ci.org/sourcegraph/srclib-java.png?branch=master)](https://travis-ci.org/sourcegraph/srclib-java)

## Requirements

srclib-java Requires Oracle JDK 8, or OpenJDK 8 to run, as well as Maven to build.

Additionally, run the following command to register `tools.jar` as an artifact
that can be included from Maven.
```
mvn install:install-file -DgroupId=com.sun -DartifactId=tools -Dversion=1.8 -Dpackaging=jar -Dfile="/usr/lib/jvm/java-8-oracle/lib/tools.jar"
```
## Building

srclib-java can be build and registered with the following two commands.
```
make
src toolchain add sourcegraph.com/sourcegraph/srclib-java
```

## Testing

`make test` - Test in program mode

`make test-gen` - Generate new test data in program mode

## Limitations

The code currently assumes that all dependencies are stored on Maven Central,
and have a pom.xml file.
