FROM ubuntu:14.04

RUN echo
RUN apt-get update -qq
RUN apt-get install -qq curl git python-software-properties software-properties-common

# Install Java 8
RUN add-apt-repository ppa:webupd8team/java
RUN apt-get update -qq

# auto accept oracle jdk license
RUN echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | /usr/bin/debconf-set-selections
RUN apt-get install -y oracle-java8-installer

# Install Maven
RUN apt-get install -qq maven

# Install Gradle
RUN apt-get install -qq gradle
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

WORKDIR /src

ENTRYPOINT ["srclib-java"]
