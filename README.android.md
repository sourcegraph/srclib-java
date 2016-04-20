# How to add Android repositories to Sourcegraph

## Action plan

- build Android from source code 
- add libcore
- add base framework
- add support framework

## Building Android from source code

Before adding Android repositories to Sourcegraph, you should grab and build Android source. Please follow instructions from
* http://source.android.com/source/initializing.html
* http://source.android.com/source/downloading.html
* http://source.android.com/source/building.html

I'm using the following commands (assuming you have Java installed)
```
mkdir ~/bin
PATH=~/bin:$PATH
curl https://storage.googleapis.com/git-repo-downloads/repo > ~/bin/repo
chmod a+x ~/bin/repo
export WORKING_DIRECTORY=/tmp/android-sdk
mkdir $WORKING_DIRECTORY
cd $WORKING_DIRECTORY
repo init -u https://android.googlesource.com/platform/manifest
repo sync
source build/envsetup.sh
lunch aosp_arm-eng
make -j4
```

Note: On my instance compilation takes about 5 hours, why don't you grab a sandwich meanwhile? :)

## Configuring Android libcore repository on your server

### Prerequisites

Please make sure you have `$SRC_URL` variable set, which points to `http[s]://YOUR-SOURCEGRAPH-SERVER[:PORT]`

### Mirroring repository

You need to create repository named `android.googlesource.com/platform/libcore` on your Sourcegraph server and mirror `https://android.googlesource.com/platform/libcore` there. You can use the following commands:

```
mkdir /tmp/mirror
cd /tmp/mirror
src repo create android.googlesource.com/platform/libcore
git clone --mirror https://android.googlesource.com/platform/libcore
cd libcore.git/
git remote set-url --push origin $SRC_URL/android.googlesource.com/platform/libcore
git push --mirror
```

### Indexing libcore repository

I assume that you have `srclib-java` toolchain installed to default location.
First we'll generate special `Srcfile` to instruct `srclib-java` how to deal with libcore repository.

```
cd $WORKING_DIRECTORY/libcore
java -classpath ~/.srclib/sourcegraph.com/sourcegraph/srclib-java/.bin/srclib-java.jar com.sourcegraph.javagraph.AndroidCoreSrcFileGenerator > Srcfile
srclib config
srclib make
```

### Pushing indexes
```
cd $WORKING_DIRECTORY/libcore
src push --repo=android.googlesource.com/platform/libcore
```

## Configuring Android base framework repository on your server

### Prerequisites

Please make sure you have `$SRC_URL` variable set, which points to `http[s]://YOUR-SOURCEGRAPH-SERVER[:PORT]`

### Mirroring repository

You need to create repository named `android.googlesource.com/platform/frameworks/base` on your Sourcegraph server and mirror `https://android.googlesource.com/platform/frameworks/base` there. You can use the following commands:

```
mkdir /tmp/mirror
cd /tmp/mirror
src repo create android.googlesource.com/platform/frameworks/base
git clone --mirror https://android.googlesource.com/platform/frameworks/base
cd base.git/
git remote set-url --push origin $SRC_URL/android.googlesource.com/platform/frameworks/base
git push --mirror
```

### Indexing base framework repository

I assume that you have `srclib-java` toolchain installed to default location.
First we'll generate special `Srcfile` to instruct `srclib-java` how to deal with libcore repository.

```
cd $WORKING_DIRECTORY/frameworks/base
java -classpath ~/.srclib/sourcegraph.com/sourcegraph/srclib-java/.bin/srclib-java.jar com.sourcegraph.javagraph.AndroidSDKSrcFileGenerator > Srcfile
srclib config
srclib make
```

### Pushing indexes
```
cd $WORKING_DIRECTORY/frameworks/base
src push --repo=android.googlesource.com/platform/frameworks/base
```

## Configuring Android Support framework repository on your server

### Prerequisites

Please make sure you have `$SRC_URL` variable set, which points to `http[s]://YOUR-SOURCEGRAPH-SERVER[:PORT]`

### Mirroring repository

You need to create repository named `android.googlesource.com/platform/frameworks/support` on your Sourcegraph server and mirror `https://android.googlesource.com/platform/frameworks/support` there. You can use the following commands:

```
mkdir /tmp/mirror
cd /tmp/mirror
src repo create android.googlesource.com/platform/frameworks/support
git clone --mirror https://android.googlesource.com/platform/frameworks/support
cd support.git/
git remote set-url --push origin $SRC_URL/android.googlesource.com/platform/frameworks/support
git push --mirror
```

### Indexing Support framework repository

I assume that you have `srclib-java` toolchain installed to default location.
First we'll generate special `Srcfile` to instruct `srclib-java` how to deal with Support repository.

```
cd $WORKING_DIRECTORY/frameworks/support
java -classpath ~/.srclib/sourcegraph.com/sourcegraph/srclib-java/.bin/srclib-java.jar com.sourcegraph.javagraph.AndroidSupportSrcFileGenerator > Srcfile
srclib config
srclib make
```

### Pushing indexes
```
cd $WORKING_DIRECTORY/frameworks/support
src push --repo=android.googlesource.com/platform/frameworks/support
```


