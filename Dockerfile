FROM openjdk:17-jdk-slim

# Install dependencies
RUN apt-get update && apt-get install -y \
    curl \
    git \
    unzip \
    wget \
    gradle \
    && rm -rf /var/lib/apt/lists/*

# Set up Android SDK
ENV ANDROID_SDK_ROOT=/opt/android-sdk
RUN mkdir -p $ANDROID_SDK_ROOT/cmdline-tools && \
    curl -L -o cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip && \
    unzip -q cmdline-tools.zip -d $ANDROID_SDK_ROOT/cmdline-tools && \
    mv $ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools $ANDROID_SDK_ROOT/cmdline-tools/latest && \
    rm cmdline-tools.zip

ENV PATH=$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools

# Accept Android SDK licenses
RUN yes | sdkmanager --licenses

WORKDIR /app/android-native
COPY android-native/ .

# Build with the checked-in Gradle wrapper.
RUN ./gradlew assembleDebug