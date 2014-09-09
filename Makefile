.PHONY: default

default: install

install:
	mvn package
	mv target/srclib-java.jar .bin/srclib-java.jar

test:
	src -v test -m program

test-gen:
	src -v test -m program --gen

clean:
	rm .bin/srclib-java.jar
	rm -rf target
