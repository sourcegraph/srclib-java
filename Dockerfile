FROM ubuntu:14.04

RUN echo cachebuster 2014-12-22
RUN apt-get update -qq
RUN apt-get install -qq curl git python-software-properties software-properties-common

# Install Java 8 (to bootstrap building our own one, below)
RUN add-apt-repository ppa:webupd8team/java
RUN apt-get update -qq
# auto accept oracle jdk license
RUN echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | /usr/bin/debconf-set-selections
RUN apt-get install -y oracle-java8-installer

# Install newer jdk8u to fix
# https://bugs.openjdk.java.net/browse/JDK-8062359?page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel. We can remove this once jdk 8u26+ is released.
RUN apt-get install -qq mercurial
RUN hg clone http://hg.openjdk.java.net/jdk8u/jdk8u /tmp/jdk8u-build
WORKDIR /tmp/jdk8u-build
# Really get jdk8u (otherwise get_source.sh gets jdk8 not jdk8u subrepos)
RUN bash ./get_source.sh
RUN apt-get install -qq unzip build-essential zip libX11-dev libxext-dev libxrender-dev libxtst-dev libxt-dev libcups2-dev libasound2-dev libfreetype6-dev
RUN mkdir -p /srclib
# Explicitly specify freetype dirs due to bug http://mail.openjdk.java.net/pipermail/build-dev/2013-October/010874.html.
RUN bash ./configure --prefix=/srclib --with-freetype-lib=/usr/lib/x86_64-linux-gnu --with-freetype-include=/usr/include/freetype2
RUN make all
RUN make install
# Remove the Java we used to bootstrap our custom jdk8u
RUN sudo apt-get remove -qq oracle-java8-installer
# Set up our system to use this JDK.
ENV PATH /tmp/jdk8u-build/build/linux-x86_64-normal-server-release/images/j2sdk-image/bin:$PATH
RUN env --unset=JAVA_HOME
ENV LANG C
# Use the keystore with SSL certs from jdk7
RUN rm -rf /srclib/jvm/openjdk-1.8.0-internal/jre/lib/security
RUN ln -s /usr/lib/jvm/java-7-openjdk-amd64/jre/lib/security /srclib/jvm/openjdk-1.8.0-internal/jre/lib/security
ENV JAVA_HOME /srclib/jvm/openjdk-1.8.0-internal

# Install Maven
RUN apt-get install -qq maven

# Install Gradle 2.2
RUN curl -L -o gradle.zip https://services.gradle.org/distributions/gradle-2.2-bin.zip
RUN unzip gradle.zip
RUN mv gradle-2.2 /gradle
ENV PATH /gradle/bin:${PATH}
RUN ln -s /usr/lib/jvm/java-8-oracle /usr/lib/jvm/default-java

# Add this toolchain
ADD . /srclib/srclib-java/
WORKDIR /srclib/srclib-java
ENV PATH /srclib/srclib-java/.bin:$PATH

# Add srclib (unprivileged) user
RUN useradd -ms /bin/bash srclib
RUN mkdir /src
RUN chown -R srclib /src /srclib
USER srclib

# Allow determining whether we're running in Docker
ENV IN_DOCKER_CONTAINER true

WORKDIR /src

ENTRYPOINT ["srclib-java"]
