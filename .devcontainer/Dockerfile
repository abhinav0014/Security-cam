
# ============================================
# FILE: .devcontainer/Dockerfile
# ============================================
FROM ubuntu:22.04

# Prevent interactive prompts during package installation
ENV DEBIAN_FRONTEND=noninteractive

# Install basic dependencies
RUN apt-get update && apt-get install -y \
    curl \
    wget \
    unzip \
    git \
    openjdk-17-jdk \
    build-essential \
    file \
    apt-utils \
    && rm -rf /var/lib/apt/lists/*

# Set up Android SDK environment variables
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=$ANDROID_HOME
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
ENV PATH=$PATH:$ANDROID_HOME/platform-tools
ENV PATH=$PATH:$ANDROID_HOME/emulator
ENV PATH=$PATH:$ANDROID_HOME/build-tools/34.0.0

# Create Android SDK directory
RUN mkdir -p $ANDROID_HOME

# Download and install Android Command Line Tools
RUN cd /tmp && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip && \
    unzip -q commandlinetools-linux-11076708_latest.zip -d $ANDROID_HOME && \
    rm commandlinetools-linux-11076708_latest.zip && \
    mkdir -p $ANDROID_HOME/cmdline-tools/latest && \
    mv $ANDROID_HOME/cmdline-tools/bin $ANDROID_HOME/cmdline-tools/latest/ && \
    mv $ANDROID_HOME/cmdline-tools/lib $ANDROID_HOME/cmdline-tools/latest/

# Accept licenses and install SDK packages
RUN yes | sdkmanager --licenses || true

RUN sdkmanager --install \
    "platform-tools" \
    "platforms;android-34" \
    "build-tools;34.0.0" \
    "emulator" \
    "system-images;android-34;google_apis;x86_64"

# Install Gradle
ENV GRADLE_VERSION=8.4
RUN cd /opt && \
    wget -q https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip && \
    unzip -q gradle-${GRADLE_VERSION}-bin.zip && \
    rm gradle-${GRADLE_VERSION}-bin.zip
ENV PATH=$PATH:/opt/gradle-${GRADLE_VERSION}/bin

# Set JAVA_HOME
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

# Create workspace directory
RUN mkdir -p /workspace
WORKDIR /workspace

# Verify installations
RUN java -version && \
    gradle --version && \
    sdkmanager --version && \
    adb --version