FROM ubuntu:14.04

RUN echo cachebuster 2015-11-14
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

# Apply a patch to fix an issue in the javac API
ADD ./hg-27bb4-javac-jdk8u-langtools.patch /tmp/hg-27bb4-javac-jdk8u-langtools.patch
RUN cd langtools && hg pull -r 27bb4c63fd70 && hg update -r 27bb4c63fd70 && patch -p1 < /tmp/hg-27bb4-javac-jdk8u-langtools.patch

RUN apt-get install -qq unzip build-essential zip libX11-dev libxext-dev libxrender-dev libxtst-dev libxt-dev libcups2-dev libasound2-dev libfreetype6-dev
RUN mkdir -p /srclib
# Explicitly specify freetype dirs due to bug http://mail.openjdk.java.net/pipermail/build-dev/2013-October/010874.html.
RUN bash ./configure --prefix=/srclib --with-freetype-lib=/usr/lib/x86_64-linux-gnu --with-freetype-include=/usr/include/freetype2
RUN make all
RUN make install
# Remove the Java we used to bootstrap our custom jdk8u
RUN sudo apt-get remove -qq oracle-java8-installer
# Set up our system to use this JDK.
ENV JAVA_HOME /srclib/jvm/openjdk-1.8.0-internal
ENV PATH $JAVA_HOME/bin:$PATH

ENV LANG C

# Update CA certificates
RUN rm -f $JAVA_HOME/jre/lib/security/cacerts
RUN ln -s /etc/ssl/certs/java/cacerts $JAVA_HOME/jre/lib/security/cacerts

# Install Maven
RUN apt-get install -qq maven

# Install Gradle 2.2
RUN curl -L -o gradle.zip https://services.gradle.org/distributions/gradle-2.2-bin.zip
RUN unzip gradle.zip
RUN mv gradle-2.2 /gradle
ENV PATH /gradle/bin:${PATH}
RUN ln -s /usr/lib/jvm/java-8-oracle /usr/lib/jvm/default-java

# Install Android SDK
RUN apt-get install -qq lib32z1 lib32ncurses5 lib32bz2-1.0 lib32stdc++6
RUN cd /opt && wget -q http://dl.google.com/android/android-sdk_r24.3.4-linux.tgz 
RUN cd /opt && tar xzf android-sdk_r24.3.4-linux.tgz 
RUN cd /opt && rm -f android-sdk_r24.3.4-linux.tgz
ENV ANDROID_HOME /opt/android-sdk-linux
ENV PATH $PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools
RUN echo y | android update sdk --filter platform-tools,build-tools-23.0.2,build-tools-23.0.1,build-tools-23,build-tools-22.0.1,build-tools-22,build-tools-21.1.2,build-tools-21.1.1,build-tools-21.1,build-tools-21.0.2,build-tools-21.0.1,build-tools-21,build-tools-20,build-tools-19.1,build-tools-19.0.3,build-tools-19.0.2,build-tools-19.0.1,build-tools-19,build-tools-18.1.1,build-tools-18.1,build-tools-18.0.1,build-tools-17,android-23,android-22,android-21,android-20,android-19,android-18,android-17,android-16,android-15,android-14,extra-android-support --no-ui --force --all

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
