# How to add OpenJDK repositories to Sourcegraph

## What's wrong with them?

OpenJDK source code cannot be graphed as is. We need to detect that we are working with a special repository and graph it specific way. `src` or `srclib` does not give us a hint that we are about to graph repository R so we need to do it manually

## Action plan

We'll try to
- checkout OpenJDK code
- build OpenJDK code
- create Sourcegraph repositories for OpenJDK subprojects: jdk, langtools, and nashorn
- import source code of OpenJDK subprojects: jdk, langtools, and nashorn to Sourcegraph
- manually index OpenJDK subprojects: jdk, langtools, and nashorn 
- Push indexes to Sourcegraph

## Checking out OpenJDK code

We'll checkout OpenJDK code once but then split it to two copies
- clean, that will be imported to Sourcegraph. This copy contains only checked out source code and no temporary or build files
- dirty, copy of clean plus temporary and build files

### Prerequisites
I assume that you have empty directory `$SOURCEGRAPH_OPENJDK_DIR` where we'll put all the files. Please have at least 10Gb of disk space available

### Fetching source code
```
cd $SOURCEGRAPH_OPENJDK_DIR
hg clone http://hg.openjdk.java.net/jdk8/jdk8 clean
cd clean
bash ./get_source.sh
cd ..
cp -R clean dirty
```
As a result we should have two identical directories, `clean` and `dirty`

## Building OpenJDK code

### Prerequisites
Please check http://hg.openjdk.java.net/build-infra/jdk8/raw-file/tip/README-builds.html for details

```
cd $SOURCEGRAPH_OPENJDK_DIR/dirty
bash ./configure
make all
```

It takes about 30 minutes to build it from scratch on my machine.

## Making repositories for OpenJDK projects

```
src repo create hg.openjdk.java.net/jdk8/jdk8/jdk
src repo create hg.openjdk.java.net/jdk8/jdk8/langtools
src repo create hg.openjdk.java.net/jdk8/jdk8/nashorn
```

## Importing source code of OpenJDK subprojects

### Prerequisites
Assume that you have variable `$SOURCEGRAPH_URL` which points to your Sourcegraph server in form `http://HOST:PORT`
```
cd $SOURCEGRAPH_OPENJDK_DIR/clean/jdk
git init
git add *
git commit -m "import"
git remote add srclib $SOURCEGRAPH_URL/hg.openjdk.java.net/jdk8/jdk8/jdk
git push -u srclib master
```
then
```
cd $SOURCEGRAPH_OPENJDK_DIR/clean/langtools
git init
git add *
git commit -m "import"
git remote add srclib $SOURCEGRAPH_URL/hg.openjdk.java.net/jdk8/jdk8/langtools
git push -u srclib master
```
and finally
```
cd $SOURCEGRAPH_OPENJDK_DIR/clean/nashorn
git init
git add *
git commit -m "import"
git remote add srclib $SOURCEGRAPH_URL/hg.openjdk.java.net/jdk8/jdk8/nashorn
git push -u srclib master
```

## Manually indexing OpenJDK subprojects

### Prerequisites
You should have `srclib-java` toolchain installed as `sourcegraph.com/sourcegraph/srclib-java` (if you have different name, please adjust classpath below)

```
cd $SOURCEGRAPH_OPENJDK_DIR/dirty/jdk
# Generating Srcfile
java -classpath ~/.srclib/sourcegraph.com/sourcegraph/srclib-java/.bin/srclib-java.jar com.sourcegraph.javagraph.JDKSrcFileGenerator jdk > Srcfile
# Building units and indexing them
srclib config && srclib make
cd $SOURCEGRAPH_OPENJDK_DIR/dirty/langtools
# Generating Srcfile
java -classpath ~/.srclib/sourcegraph.com/sourcegraph/srclib-java/.bin/srclib-java.jar com.sourcegraph.javagraph.JDKSrcFileGenerator langtools > Srcfile
# Building units and indexing them
srclib config && srclib make
cd $SOURCEGRAPH_OPENJDK_DIR/dirty/nashorn
# Generating Srcfile
java -classpath ~/.srclib/sourcegraph.com/sourcegraph/srclib-java/.bin/srclib-java.jar com.sourcegraph.javagraph.JDKSrcFileGenerator nashorn > Srcfile
# Building units and indexing them
srclib config && srclib make
```
## Pushing indexes to Sourcegraph

It's the most tricky part and I don't have idea how to automate it yet.

First, you need to obtain commit SHA hashes from OpenJDK repositories imported to Sourcegraph (git SHA hash there differes from hg SHA hash, obviously).
Then you need to rename `.srclib-cache/HG_SHA` to `.srclib-cache/GIT_SHA`.
And finally, run
```
src push --repo=hg.openjdk.java.net/jdk8/jdk8/jdk --commit=GIT_SHA
```
Repeat for langtools and nashorn repositories.



