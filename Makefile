.PHONY: default

default: .bin/srclib-java

.bin/srclib-java:
	mvn package
	mv .bin/srclib-java.jar .bin/srclib-java
	chmod +x .bin/srclib-java
