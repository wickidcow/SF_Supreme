#!/usr/bin/env bash
set -euo pipefail

# Verify the published Apache binary before changing the runner's Maven PATH.
MAVEN_VERSION=3.10.0
MAVEN_SHA512=908b1501bfb420bf7c8affb855534a9c407fd6099367bfb9f2f2dcb8e9799102bffb84518cde74c679bd76870247c6528683abdd620581bffa90f95d92d175aa
: "${RUNNER_TEMP:?This installer must run on GitHub Actions}"
: "${GITHUB_PATH:?GitHub Actions PATH file is required}"

archive="${RUNNER_TEMP}/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
curl --fail --location --silent --show-error --retry 3 \
  "https://downloads.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
  --output "$archive"
printf '%s  %s\n' "$MAVEN_SHA512" "$archive" | sha512sum --check --status
tar -xzf "$archive" -C "$RUNNER_TEMP"
maven_bin="${RUNNER_TEMP}/apache-maven-${MAVEN_VERSION}/bin"
"$maven_bin/mvn" --version | grep -F "Apache Maven ${MAVEN_VERSION}"
printf '%s\n' "$maven_bin" >> "$GITHUB_PATH"
