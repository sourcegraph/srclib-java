SRC = $(shell /usr/bin/find ./src -type f)

.PHONY: default install test test-gen clean

default: install

build/libs/srclib-java-0.0.1-SNAPSHOT.jar: build.gradle ${SRC}
	./gradlew jar

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
