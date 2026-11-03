# Build toolchain for Umbra: JDK 21 + Android SDK. The source code is not baked into the image;
# docker/build.sh mounts the repository and runs Gradle inside this container.
FROM eclipse-temurin:21.0.13_9-jdk-noble

ARG CMDLINE_TOOLS_VERSION=16111833
ARG CMDLINE_TOOLS_SHA256=0877a1d048fe4a24efe2eff536ca4223f7adeb58648bb81909d33c446918cfa8
ARG ANDROID_PLATFORM=platforms;android-37.0
# Must match the default build-tools version of the Android Gradle Plugin in use.
ARG ANDROID_BUILD_TOOLS=build-tools;36.0.0

ENV ANDROID_HOME=/opt/android-sdk
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${PATH}"

RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL -o /tmp/cmdline-tools.zip \
        "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" \
    && echo "${CMDLINE_TOOLS_SHA256}  /tmp/cmdline-tools.zip" | sha256sum -c - \
    && mkdir -p "${ANDROID_HOME}/cmdline-tools" \
    && unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools \
    && mv /tmp/cmdline-tools/cmdline-tools "${ANDROID_HOME}/cmdline-tools/latest" \
    && rm -rf /tmp/cmdline-tools /tmp/cmdline-tools.zip

RUN yes | sdkmanager --licenses > /dev/null \
    && sdkmanager --install "${ANDROID_PLATFORM}" "${ANDROID_BUILD_TOOLS}" platform-tools > /dev/null \
    # The build runs as the host user; let it read the SDK and add components if AGP needs any.
    && chmod -R a+rwX "${ANDROID_HOME}"

WORKDIR /workspace
