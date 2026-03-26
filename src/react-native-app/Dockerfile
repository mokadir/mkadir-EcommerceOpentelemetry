# syntax=docker/dockerfile:1

# Copyright The OpenTelemetry Authors
# SPDX-License-Identifier: Apache-2.0

# https://github.com/react-native-community/docker-android
# Choosing a tag where the Android build tools match what we have in android/build.gradle to avoid the
# container having to download them
FROM reactnativecommunity/react-native-android:v13.2.1 AS builder

WORKDIR /reactnativesrc/
COPY src/react-native-app/ .

RUN npm install
RUN npm install --no-save @react-native-community/cli-platform-android
RUN ln -s /reactnativesrc/node_modules /reactnativesrc/android/node_modules

WORKDIR /reactnativesrc/android
RUN chmod +x gradlew
RUN mkdir -p build/generated/autolinking && echo '{"dependencies":[]}' > build/generated/autolinking/autolinking.json
RUN ./gradlew assembleRelease

FROM scratch
COPY --from=builder /reactnativesrc/android/app/build/outputs/apk/release/app-release.apk /reactnativeapp.apk
ENTRYPOINT ["/reactnativeapp.apk"]
