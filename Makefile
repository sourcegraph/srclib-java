ifeq ($(OS),Windows_NT)
	GRADLEW = .\gradlew.bat
else
	GRADLEW = ./gradlew
endif

SRC = $(shell /usr/bin/find ./src -type f)

.PHONY: default install test test-gen clean dist upload-bundled-jdk

default: install

build/libs/srclib-java-0.0.1-SNAPSHOT.jar: build.gradle ${SRC}
	${GRADLEW} jar

.bin/srclib-java.jar: build/libs/srclib-java-0.0.1-SNAPSHOT.jar
	cp build/libs/srclib-java-0.0.1-SNAPSHOT.jar .bin/srclib-java.jar

install: .bin/srclib-java.jar

test: .bin/srclib-java.jar
	src -v test -m program

test-gen: .bin/srclib-java.jar
	src -v test -m program --gen

clean:
	rm -f .bin/srclib-java.jar
	rm -rf build


# To distribute, we also bundle the JRE for the OS and
# architecture. These targets automate the uploading and downloading
# of the bundled JREs. See README.md for more info.

# keep this version string in sync with .bin/srclib-java
JDK_VERSION=jdk1.8.0_45

dist: bundled-jdk
	echo hello

bundled-jdk:
	cd build && wget https://srclib-support.s3-us-west-2.amazonaws.com/srclib-java/build/bundled-$(JDK_VERSION).tar.gz
	tar xzfv build/bundled-$(JDK_VERSION).tar.gz

# Upload the bundled JDKs to S3. Requires S3 credentials.
upload-bundled-jdk: build/bundled-$(JDK_VERSION).tar.gz
	aws s3 cp --acl public-read $< s3://srclib-support/srclib-java/$<

build/bundled-$(JDK_VERSION).tar.gz:
	tar czfv $@ bundled-jdk/
