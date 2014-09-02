.PHONY: default

default: install

install:
	mvn package
	mv .bin/srclib-java.jar .bin/srclib-java
	chmod +x .bin/srclib-java
