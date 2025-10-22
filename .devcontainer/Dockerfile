FROM openjdk:11-jdk

# Install Android SDK and other necessary tools
RUN apt-get update && apt-get install -y \
    wget \
    unzip \
    && wget -q https://dl.google.com/android/repository/commandlinetools-linux-7583922_latest.zip -O /cmdline-tools.zip \
    && unzip /cmdline-tools.zip -d /opt/android-sdk/ \
    && rm /cmdline-tools.zip \
    && yes | /opt/android-sdk/cmdline-tools/bin/sdkmanager --sdk_root=/opt/android-sdk --licenses \
    && /opt/android-sdk/cmdline-tools/bin/sdkmanager --sdk_root=/opt/android-sdk --install "platform-tools" "platforms;android-30" "build-tools;30.0.3"

# Set environment variables
ENV ANDROID_HOME /opt/android-sdk
ENV PATH ${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools

# Set the working directory
WORKDIR /workspace

# Expose necessary ports (if needed)
EXPOSE 8080

# Default command
CMD ["bash"]