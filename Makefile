SRC = $(shell find ./src -type f -name '*.java')

.PHONY: default install test test-gen clean

default: install

.bin/srclib-java.jar: pom.xml ${SRC}
	mvn package
	mv target/srclib-java.jar .bin/srclib-java.jar

install: .bin/srclib-java.jar

test: .bin/srclib-java.jar
	src -v test -m program

test-gen: .bin/srclib-java.jar
	src -v test -m program --gen

clean:
	rm -f .bin/srclib-java.jar
	rm -rf target
