FROM java:openjdk-9-jdk

RUN apt-get update
RUN apt-get install -qq maven gradle

# Install Android SDK
#RUN apt-get install -qq lib32z1 lib32ncurses5 lib32bz2-1.0 lib32stdc++6
RUN cd /opt && wget -q http://dl.google.com/android/android-sdk_r24.3.4-linux.tgz 
RUN cd /opt && tar xzf android-sdk_r24.3.4-linux.tgz 
RUN cd /opt && rm -f android-sdk_r24.3.4-linux.tgz
ENV ANDROID_HOME /opt/android-sdk-linux
ENV PATH $PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools
RUN echo y | android update sdk --filter platform-tools,build-tools-23.0.2,build-tools-23.0.1,build-tools-23,build-tools-22.0.1,build-tools-22,build-tools-21.1.2,build-tools-21.1.1,build-tools-21.1,build-tools-21.0.2,build-tools-21.0.1,build-tools-21,build-tools-20,build-tools-19.1,build-tools-19.0.3,build-tools-19.0.2,build-tools-19.0.1,build-tools-19,build-tools-18.1.1,build-tools-18.1,build-tools-18.0.1,build-tools-17,android-23,android-22,android-21,android-20,android-19,android-18,android-17,android-16,android-15,android-14,extra-android-support --no-ui --force --all

# Add this toolchain
RUN apt-get install -qq make
ENV SRCLIBPATH /srclib
ADD . /srclib/srclib-java/
RUN cd /srclib/srclib-java && make

