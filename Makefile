.PHONY: default

default: target/javagraph-0.0.1-SNAPSHOT.jar

target/javagraph-0.0.1-SNAPSHOT.jar:
	mvn package
