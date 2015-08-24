@echo off
"%JAVA_HOME%/bin/java.exe" -Xmx4g -classpath "%~dp0/srclib-java.jar" com.sourcegraph.javagraph.Main %*
