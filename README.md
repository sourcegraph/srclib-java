# srclib-java [![Build Status](https://travis-ci.org/sourcegraph/srclib-java.png?branch=master)](https://travis-ci.org/sourcegraph/srclib-java)

## Requirements

srclib-java requires:

* Oracle JDK 8 or OpenJDK 8
* Maven 3
* Gradle 2.1

### WARNING

`tools.jar` that comes with the Oracle 8 JDK and OpenJDK 8 are both buggy
as of November 7th, 2014. com.sun.source.util.JavacTask.analyze()
throws a null pointer exception on some repositories. See
https://bugs.openjdk.java.net/browse/JDK-8062359?page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel
for a description of the problem and a workaround. Build instructions for
OpenJDK 8 are here: http://openjdk.java.net/projects/build-infra/guide.html.

See the Dockerfile for how to check out the right version of jdk8u and
apply the patch.

You'll probably be able to use your system JDK without this patch on
most projects; only follow those steps if you see NPEs in the javac
API.

## Building

srclib-java can be build and registered with the following two commands:

    make
    src toolchain add sourcegraph.com/sourcegraph/srclib-java

## Testing

Run `git submodule update --init` the first time to fetch the submodule test
cases in `testdata/case`.

`make test` - Test in program mode

`make test-gen` - Generate new test data in program mode


## Release and packaging

srclib-java requires Java 8 to build and run. To allow it to run on
systems that don't have Java 8 installed or activated by default, it
can be bundled with a Java 8 JRE.

Currently this process requires tedious manual effort; fixing it is
noted as a TODO.

The bundled JREs should be present in the `bundled-jdk` directory. The
`.bin/srclib-java` script checks for their existence and uses the
bundled JRE for the current platform, if it exists.

Running `make bundled-jdk` downloads and unarchives the bundled JREs
from S3. If you are producing a new srclib-java release, then you MUST
make sure the srclib-java toolchain bundle contains these bundled JREs.

If you need to update the versions of the bundled JREs or add some for
other platforms, edit the `.bin/srclib-java` script and the
`Makefile`. These files contain hardcoded lists of platforms and JDK
versions that you'll need to update.


## TODOs

* Don't emit unresolved refs as refs to the same pkg
* If running in Docker, use a m2-srclib directory not inside the repo if in Docker since the Docker source volume is readonly.
* Simplify the Java 8 JRE bundling process
