#!/bin/bash

set -e
set -o pipefail

POMS=$(find . -type f -name pom.xml)

MVN_OPTS=-Dmaven.repo.local="$PWD"/.m2-srclib

mvn --fail-never $MVN_OPTS dependency:resolve || echo mvn dependency:resolve failed but continuing

remove_xml_comments() {
    # remove comments to make the other operations simpler
    perl -0777 -pi -e 's/<!--.*?-->//sg;' "$POM"
}

remove_other_compile_goal_plugins() {
    sed -i 's/<goal>compile<\/goal>//g' "$1"
}

insert_ecj_build_plugin_xml() {
    # add <build></build> if not exists
    if ! grep -q '<build>' "$1"; then
        sed -i 's/<\/project>/<build><\/build><\/project>/' "$1"
    fi

    # add <plugins></plugins> if not exists
    if ! grep -q '<plugins>' "$1"; then
        sed -i 's/<\/build>/<plugins><\/plugins><\/build>/' "$1"
    fi

    # insert the compiler plugin XML
    TAG="COMPILER PLUGIN XML INSERTED BY SRCLIB-JAVA"
    ECJ_BUILD_PLUGIN_XML="<!-- $TAG --><plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId><version>3.2</version><configuration><compilerId>eclipse</compilerId><fork>true</fork><failOnError>false</failOnError><compilerArgs><arg>-nowarn2</arg><arg>-h</arg><arg>-1.7</arg><arg>?</arg></compilerArgs><compilerArgument>-?</compilerArgument><compilerArguments><org.eclipse.jdt.core.compiler.compliance>1.9</org.eclipse.jdt.core.compiler.compliance><proceedOnError/><verbose/></compilerArguments></configuration><dependencies><dependency><groupId>org.codehaus.plexus</groupId><artifactId>plexus-compiler-eclipse</artifactId><version>2.5</version></dependency></dependencies></plugin><!-- END: INSERTED BY SRCLIB-JAVA -->"
    EXE_BUILD_PLUGIN_XML="<!-- $TAG --><plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId><version>3.2</version><configuration><source>1.7</source><target>1.7</target><fork>true</fork><failOnError>false</failOnError><executable>ecj</executable><compilerArgs><arg>-proceedOnError</arg><arg>-maxProblems</arg><arg>1</arg></compilerArgs></configuration></plugin><!-- END: INSERTED BY SRCLIB-JAVA -->"
    JAVAC_BUILD_PLUGIN_XML="<!-- $TAG --><plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId><version>3.2</version><configuration><fork>true</fork><failOnError>false</failOnError><forceJavacCompilerUse>true</forceJavacCompilerUse><proc>none</proc><source>1.8</source><target>1.8</target><compilerArgs><arg>-XDcompilePolicy=bytodo</arg></compilerArgs></configuration></plugin><!-- END: INSERTED BY SRCLIB-JAVA -->"
    if ! grep -q "$TAG" "$1"; then
        sed -i 's/<\/plugins>/'"${EXE_BUILD_PLUGIN_XML//\//\\/}"'<\/plugins>/' "$1"
    fi
}

disable_deps() {
    TAG="DEPENDENCIES DISABLED BY SRCLIB-JAVA"
    if ! grep -q "$TAG" "$1"; then
        # need perl because it's a multi-line regexp
        perl -0777 -pi -e 's/<dependencies>.*<\/dependencies>//sg' "$1"
    fi
    echo OK
}

for POM in $POMS; do
    echo $POM

    # reset before munging
    cp "$POM" "$POM".bak
    
    remove_xml_comments "$POM"
    remove_other_compile_goal_plugins "$POM"
    disable_deps "$POM" # build plugin has dependencies, so must go first
    insert_ecj_build_plugin_xml "$POM"
    echo $POM .. done
done

mvn $1 $MVN_OPTS install -DskipTests || echo mvn installed failed but continuing

for POM in $POMS; do
    mv "$POM".bak "$POM"
    echo $POM .. reverted
done


# if you get SSL errors in that mvn command, your java truststore
# might not be set up. set it with something like:
# 
# mvn -Djavax.net.ssl.trustStore=/usr/lib/jvm/java-8-oracle/jre/lib/security/cacerts
