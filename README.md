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

## Known limitations

### File encoding

Please ensure you have specified file encoding in your .gradle or pom.xml build file, otherwise srclib-java will use platform encoding to compile files and compilation may fail when your files using charset different from the system's one.

### Annotations

We do not support annotations (yet), so if your code relies on annotation processor such as lombok there will be compilation errors and some parts of your code wouldn't be graphed.

### Generated code

If some parts of your code is generated using third party tool or plugin, we may fail to graph your code properly. 

If your project is Maven-based, we are aware about existence of *org.antlr:antlr4-maven-plugin* and will try to generate java files from ANTLR grammar but please make sure that your directory structure matches java package names, i.e. don't put generated classes from foo.bar.baz package to *src/generated*, use *src/generated/foo/bar/baz*. Also please make sure that your ANTLR-based subproject compiles by running `mvn -f SUBPROJECT-DIR/pom.xml generate-sources` command.

If your project is Android-based, we are trying to generate *R.java*, *BuildConfig.java* and aidl-based java files using:
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

We expect that your project may be compiled out of the box using latest Gradle, Maven, and Java 8. If there are some build pre-requisites (such as some expected environment variables, hardcoded file locations, pre-installed libraries and/or tools), we may fail to graph your source code properly.

### Class name conflicts

If few of your modules have class with the same name, we may be unable to resolve which one to use when graphing your source code.

### Possible clashes of Maven and Gradle build files.

When scanning for source units, we are processing ALL pom.xml and gradle build files in your repository. Some of them might be remnants of old build process and we might fail to process them if they were abandonned. Sometimes there may be both pom.xml and .gradle files in the same directory which may define the same group / artifact pair and we can't tell which one will be taken into account when graphing your source code. It's possible that we'll pick the wrong one.

## Maven notes

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

If you'd like to graph OpenJDK you have the following options
* Grab prebuilt source graph [here](https://github.com/alexsaveliev/srclib-java-prebuilts/tree/master/openjdk)
* Graph it yourself
  * Grab and `build` OpenJDK using the following [instructions](http://hg.openjdk.java.net/build-infra/jdk8/raw-file/tip/README-builds.html)
  * Switch your java back to java 8 (I believe you switched it to java 7 at the previous step)
  * Run `src config && src make` in `jdk`, `langtools`, `nashorn`, `jaxp`, `jaxws`, there will be some compilation errors for `jdk` project, you may ignore them
  * Copy the results (content of .srclib-cache) to the target directory

## Graphing Android's libcore, frameworks/base, frameworks/support

If you'd like to graph Anroid libraries, you have the following options
* Grab prebuilt source graph [here](https://github.com/alexsaveliev/srclib-java-prebuilts/tree/master/android)
* Graph it yourself
  * Grab and `build` Android using the following [instructions](http://source.android.com/source/index.html). Note, that if you are running x64 Linux, follow instructions from http://superuser.com/questions/344533/no-such-file-or-directory-error-in-bash-but-the-file-exists if you see errors
  * Switch your java back to java 8 (I believe you switched it to java 7 at the previous step)
  * Run `src config && src make` in `libcore` and `frameworks/base`
  * Graphing `frameworks/support` is a little bit tricky
    * Edit `v4/build.gradle` and replace there `compileSdkVersion 4` with     `compileSdkVersion "current"`
    * Backup `prebuilts/sdk/current` and replace it with the content of platform-22 directory from Android SDK
    *  Make a symlinks for `aapt` and `llvm-rs-cc` in `prebuilts/sdk/tools/linux` to point to matching files in `prebuilts/sdk/tools/linux/bin` (similar as done for aidl)
    * Run `src config && src make` in `frameworks/support`

