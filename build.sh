#!/bin/bash
set -euxo pipefail

# Define image names and versions with full paths
SBT_BUILD_IMAGE="docker.io/sbtscala/sbt:eclipse-temurin-17"
RUNTIME_IMAGE="docker.io/library/eclipse-temurin:17-jre-focal"
FINAL_IMAGE_NAME="nebula-rolling"

# --- Build Stage ---
echo "--- Starting Build Stage ---"

# Create a build container
build_container=$(buildah from "$SBT_BUILD_IMAGE")
buildah config --label name="nebula-rolling-builder" "$build_container"

# Copy source code into the build container
buildah copy "$build_container" . /app

# Set working directory inside the build container
buildah workingdir "$build_container" /app

# Run sbt to package the applications
# The 'universal:packageBin' task creates zip files in target/universal
buildah run "$build_container" sbt universal:packageBin

# --- Runtime Stage ---
echo "--- Starting Runtime Stage ---"

# Create a runtime container
runtime_container=$(buildah from "$RUNTIME_IMAGE")
buildah config --label name="nebula-rolling-runtime" "$runtime_container"

# Set working directory inside the runtime container
buildah workingdir "$runtime_container" /app

# Install curl (needed to download nebula-cert)
buildah run "$runtime_container" apt-get update && apt-get install -y curl --no-install-recommends && rm -rf /var/lib/apt/lists/*

# Download and install nebula-cert from nightly releases
# The unzipped file name is typically 'nebula-cert'
buildah run "$runtime_container" curl -L "https://github.com/NebulaOSS/nebula-nightly/releases/download/latest/nebula-linux-amd64.tar.gz" -o /tmp/nebula.tar.gz
buildah run "$runtime_container" tar -xzf /tmp/nebula.tar.gz -C /usr/local/bin/
buildah run "$runtime_container" mv /usr/local/bin/nebula-linux-amd64 /usr/local/bin/nebula-cert # Rename if necessary, assuming it extracts to nebula-linux-amd64
buildah run "$runtime_container" rm /tmp/nebula.tar.gz

# Copy packaged applications from the build container
# The sbt-native-packager creates zip files, we need to unzip them
buildah copy --from "$build_container" "$runtime_container" /app/cert-roller/target/universal/nebula-cert-roller-0.1.0-SNAPSHOT.zip /tmp/cert-roller.zip
buildah run "$runtime_container" unzip /tmp/cert-roller.zip -d /app
buildah run "$runtime_container" mv /app/nebula-cert-roller-0.1.0-SNAPSHOT /app/cert-roller
buildah run "$runtime_container" rm /tmp/cert-roller.zip

buildah copy --from "$build_container" "$runtime_container" /app/config-server/target/universal/nebula-config-server-0.1.0-SNAPSHOT.zip /tmp/config-server.zip
buildah run "$runtime_container" unzip /tmp/config-server.zip -d /app
buildah run "$runtime_container" mv /app/nebula-config-server-0.1.0-SNAPSHOT /app/config-server
buildah run "$runtime_container" rm /tmp/config-server.zip

# Expose the default port for config-server
buildah config --port 8080 "$runtime_container"

# Set the default command to run the config-server
buildah config --cmd "/app/config-server/bin/nebula-config-server" "$runtime_container"

# Commit the runtime container to a new image
buildah commit "$runtime_container" "$FINAL_IMAGE_NAME"

echo "--- Build Complete ---"
echo "Image '$FINAL_IMAGE_NAME' created successfully."
echo "To run the config-server: podman run -p 8080:8080 $FINAL_IMAGE_NAME"
echo "To run the cert-roller: podman run -v \$(pwd)/pubs:/app/pubs $FINAL_IMAGE_NAME /app/cert-roller/bin/nebula-cert-roller <base-directory>"

# Clean up intermediate containers
buildah rm "$build_container"
buildah rm "$runtime_container"