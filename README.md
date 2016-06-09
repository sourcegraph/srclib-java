# srclib-java [![Build Status](https://travis-ci.org/sourcegraph/srclib-java.png?branch=master)](https://travis-ci.org/sourcegraph/srclib-java)

## Requirements

srclib-java requires:

* Oracle JDK 8 or OpenJDK 8
* Maven (in some cases we may execute `mvn generate-sources`)
* Gradle (if project to be indexed doesn't come with Gradle wrapper)

### WARNING

`tools.jar` that comes with the Oracle 8 JDK and OpenJDK 8 are both buggy
as of November 7th, 2014. com.sun.source.util.JavacTask.analyze()
throws a null pointer exception on some repositories. See
https://bugs.openjdk.java.net/browse/JDK-8062359?page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel
for a description of the problem and a workaround. Build instructions for
OpenJDK 8 are here: http://openjdk.java.net/projects/build-infra/guide.html.

You'll probably be able to use your system JDK without this patch on
most projects; only follow those steps if you see NPEs in the javac
API.

## Building

You need to have the following tools available to build Java toolchain:
* make
* JDK 8
* git

srclib-java can be built and registered with the following commands:
```
    make
    mkdir -p $SRCLIBPATH/sourcegraph.com/sourcegraph/
    ln -s $PWD $SRCLIBPATH/sourcegraph.com/sourcegraph/srclib-java
```

or you may run `srclib toolchain install java` which will clone the latest version to `$SRCLIBPATH/sourcegraph.com/sourcegraph/srclib-java` and build it.

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
* Simplify the Java 8 JRE bundling process
* Add support of other build tools and/or projects (Eclipse's `.classpath` for example)
* Add support of Android product flavors and build variants

## Known limitations

### File encoding

Please ensure you have specified file encoding in your .gradle or pom.xml build file, otherwise srclib-java will use platform encoding to compile files and compilation may fail when your files using charset different from the system's one.

### Annotations

We do not support annotations (yet), so if your code relies on annotation processor such as lombok there will be compilation errors and some parts of your code wouldn't be graphed.

### Generated code

If some parts of your code is generated using third party tool or plugin, we may fail to graph your code properly. 

If your project is Maven-based, we are aware about existence of *org.antlr:antlr4-maven-plugin* and will try to generate java files from ANTLR grammar but please make sure that your directory structure matches java package names, i.e. don't put generated classes from foo.bar.baz package to *src/generated*, use *src/generated/foo/bar/baz*. Also please make sure that your ANTLR-based subproject compiles by running `mvn -f SUBPROJECT-DIR/pom.xml generate-sources` command.

If your project is Android-based, we are trying to generate *R.java*, *BuildConfig.java* and AIDL-based java files using:
 * with  Maven: `mvn -f SUBPROJECT-DIR/pom.xml generate-sources`
 * with Gradle: run `:PROJECT:generateDebugSources` or `:PROJECT:assembleDebug` before extracting meta information from Gradle build files.
 
If your project is Gradle-based, we'll run all not-standard (see [Java Plugin](https://docs.gradle.org/current/userguide/java_plugin.html) for the list of tasks we considering "standard") tasks `compile` task depends on before extracting meta information from Gradle build files. These tasks may produce generated source files, but they may also compile some parts of your code.

### AspectJ, Groovy, Closure, Scala

Sorry, but we don't support (yet) if part of your code is written in Groovy, Closure, or Scala or contains ajc files.

### Empty java files

There may be compilation errors if your project contains empty java files.

### Top-level classes located in wrong files

If your file A.java contains two classes A and B, there might be compilation errors while trying to graph your source. It's a good practice to keep single top-level class per java file.

### Unstable build environment

We expect that your project may be compiled out of the box using latest Gradle, Maven, and Java 8. If there are some build pre-requisites (such as some expected environment variables, hardcoded file locations, pre-installed libraries and/or tools, presence of some configuration files), we may fail to graph your source code properly. Please try to make sure that 
* If your project is Gradle-based (but not Android-based) then `gradlew compileJava` works in clean environment
* If your project is Gradle-based (and Android-based) then `gradlew assembleDebug` works in clean environment
* If your project is Maven-based then `mvn compile` works in clean environment
* If your project is Ant-based then `ant` works in clean environment

### Class name conflicts

If few of your modules have class with the same name, we may be unable to resolve which one to use when graphing your source code.

### Possible clashes of Maven and Gradle build files.

When scanning for source units, we are processing ALL pom.xml and gradle build files in your repository. Some of them might be remnants of old build process and we might fail to process them if they were abandonned. Sometimes there may be both pom.xml and .gradle files in the same directory which may define the same group / artifact pair and we can't tell which one will be taken into account when graphing your source code. It's possible that we'll pick the wrong one.

## Maven notes

### Maven version

Please try to support most recent version of Maven (it's currently 3.3.9). Apache Maven 3.x should be fine in most cases.

### Supported Maven plugins 

When graphing your Maven-based project, we are able to identify the following Maven plugins:
 * `com.simpligility.maven.plugins::android-maven-plugin` - we'll run `mvn -f pom.xml generate-sources` to generate source files
 * `org.apache.maven.plugins::maven-compiler-plugin` - we'll try to extract source code encoding and source code version from plugin's configuration
 * `com.jayway.maven.plugins.android.generation2::android-maven-plugin` - we'll run `mvn -f pom.xml generate-sources` to generate source files
 * `com.jayway.maven.plugins.android.generation2::maven-android-plugin` - we'll run `mvn -f pom.xml generate-sources` to generate source files
 * `org.codehaus.mojo::build-helper-maven-plugin` - we'll add extra source directories based on plugin's configuration
 * `org.antlr::antlr4-maven-plugin` - we'll run `mvn -f pom.xml generate-sources` to generate source files

If you think we should add a support for more plugins that may have impact on source code generation or compilation, please let us know.

### Graphing of Maven-based projects

We are trying to be as close as possible to the way Maven gathers dependencies, resolves artifacts, and compiles your code but there may be some differences. 

* When Maven module B depends on module A, Maven will compile A and pass A's output to B classpath while we'll pass A's source directories to B sourcepath.
* We are using separate Maven repository directory `.m2` per repository, thus first `src scan` will try to download all artifacts from remote repositories which may take some time. If your project depends from pre-installed provided artifacts, we may be unable to fetch them.
* We are trying to avoid running Maven goals or compiling project at the `src scan` phase (unless we know that project has generated source code for sure)
* We suggest that your project should be compilable at any level - better resuts may be obtained if you can run `mvn -f PATH-TO/pom.xml compile` without any errors for every pom.xml file in your repository.
* When scanning for source units, we'll try to process ALL pom.xml files in your repository so please try to keep only good ones there.

## Gradle notes

### Gradle version

If you compiling your project using Gradle vX please add `gradlew` Gradle wrapper as suggested here: https://docs.gradle.org/current/userguide/gradle_wrapper.html otherwise please expect that we'll try to use the latest Gradle version. Don't forget to add and check in `gradle/wrapper` wrapper's directory (some don't).

### Scanning and graphing of Gradle-based projects

* When we are extracting meta information from Gradle script files, we'll try to run first all not-standard tasks `compile` task depends on (see [Java Plugin](https://docs.gradle.org/current/userguide/java_plugin.html) for the list of tasks we considering "standard"). Such tasks may generate source code, repack jar files using jarjar or do something else in order to prepare your project for compilation. Unfortunately we can't identify what particular task does thus we may run some tasks that weren't needed. For Android-based projects we'll run either `generateDebugSources` or `assembleDebug` (which one we found first) to generate java files so please make sure that these tasks may be run out of the box and won't fail, otherwise we'll be unable to properly extract project information.
* When scanning for java files to graph, we taking into account only two sourcesets - `main` and `test`.
* When scanning for java files to graph, we taking into account only the following configurations - `compile`, `testCompile`, `provided`, `compileOnly`
* If your project contains `gradlew` and/or `gradlew.bat` please
  * make sure that you provided gradle launch script for both Unix and Windows
  * make sure that you ship gradle-wrapper.jar with your project, otherwise we won't be able to run `gradlew`
* We are using separate gradle user home per repository - `.gradle-srclib`, so first run of `src scan` may take some time while all artifacts being downloaded.
* Please make sure that you can run without errors `gradlew` in each project's directory. If you can't - so do we.

## Graphing OpenJDK

Please see instructions [here](README.jdk.md)

## Graphing Android's libcore, frameworks/base, frameworks/support

Please see instructions [here](README.android.md)

## Apache Ant support

`srclib-java` provides basic support for projects supposed to be built with 
Apache Ant. Currently we support the following Ant tasks and types
* property
* basename
* condition
* dirname
* import
* loadproperties
* xmlproperty
* and
* antversion
* contains
* equals
* filesmatch
* hasfreespace
* hasmethod
* isfailure
* isfalse
* isfileselected
* islastmodified
* isreference
* isset
* issigned
* istrue
* matches
* not
* or
* os
* parsersupports
* resourcecontains
* resourceexists
* resourcesmatch
* typefound
* xor
* available
* uptodate
* fileset
* path
* get
* mkdir
* javacc
* macrodef

`srclib-java` extracts source files, classpath, and bootstrap classpath from 
`javac` tasks (which also may be enclosed into `macrodef`).

`srclib-java` tries to resolve external dependencies by using "search by SHA1" 
feature of Maven Central

### JavaCC

`srclib-java` uses bundled JavaCC 6.1.2.

### Limitations
* `srclib-java` does not support Apache Ivy
* `srclib-java` does not support custom Ant tasks
* there will be compilation errors when project contains code based on Ant 
classes because Ant automatically includes Ant jar files into classpath 
while we don't.
* some Ant tasks may not be executed and some may fail because of requirements 
not met. Such requirements may include missing properties, missing directories 
or missing files which may appear as a result of custom Ant tasks or actions 
we don't support yet.
* there may be errors when repository containing Ant-based project provides 
build properties file in the form of template (for example, 
`build.properties.sample`). We suggest to provide `build.properties` with 
default values and load `local.build.properies` if there is such a file.
* all source files and classpath entries of all `javac` elements encountered 
in `build.xml` file would be merged into the single source unit. 
One `build.xml` === one source unit.
* there may be compilation issues when repository contains few sub-projects 
that may depend on each other. We won't be able to resolve dependencies between
sub-projects.