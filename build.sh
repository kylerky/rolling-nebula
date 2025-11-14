#!/bin/bash
set -euo pipefail

# Define image names and versions with full paths
SBT_BUILD_IMAGE="docker.io/sbtscala/scala-sbt:eclipse-temurin-alpine-24.0.1_9_1.11.7_3.7.4"
RUNTIME_IMAGE="docker.io/library/eclipse-temurin:24.0.2_12-jre-ubi10-minimal"
FINAL_IMAGE_NAME="nebula-rolling"

# --- Build Stage ---
echo "--- Starting Build Stage ---"

# Create a build container
build_container=$(buildah from "$SBT_BUILD_IMAGE")
buildah config --label name="nebula-rolling-builder" "$build_container"

# Copy source code into the build container
buildah copy "$build_container" . /app

# Set working directory inside the build container
buildah config --workingdir /app "$build_container"

# Run sbt to package the applications
# The 'universal:packageBin' task creates zip files in target/universal
buildah run "$build_container" sbt universal:packageBin

# --- Runtime Stage ---
echo "--- Starting Runtime Stage ---"

# Create a runtime container
runtime_container=$(buildah from "$RUNTIME_IMAGE")
buildah config --label name="nebula-rolling-runtime" "$runtime_container"

# Set working directory inside the runtime container
buildah config --workingdir /app "$runtime_container"

# Create a mount point for persistent data
buildah run "$runtime_container" mkdir -p /data

# Install curl and unzip (needed to download nebula-cert and extract packages)
# Wrap commands in 'bash -c' to ensure they all run inside the container
buildah run "$runtime_container" -- bash -c "/usr/bin/microdnf update -y && /usr/bin/microdnf install -y curl unzip && /usr/bin/microdnf clean all"

# Download and install nebula-cert from the specified nightly release
buildah run "$runtime_container" curl -L "https://github.com/NebulaOSS/nebula-nightly/releases/download/v1.10.0-nightly20251113/nebula-linux-amd64.tar.gz" -o /tmp/nebula.tar.gz
buildah run "$runtime_container" tar -xzf /tmp/nebula.tar.gz -C /usr/local/bin/
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
# Note: The default command now points to a config file in the /data volume
buildah config --cmd "/app/config-server/bin/nebula-config-server --config /data/application.conf" "$runtime_container"

# Commit the runtime container to a new image
buildah commit "$runtime_container" "$FINAL_IMAGE_NAME"

echo "--- Build Complete ---"
echo "Image '$FINAL_IMAGE_NAME' created successfully."
echo ""
echo "To use the image, create a local directory (e.g., 'my_data') with the following structure:"
echo "my_data/"
echo "├── config/"
echo "│   ├── config-server.conf"
echo "│   └── cert-roller.conf"
echo "└── pubs/"
echo "    └── host1.pub"
echo ""
echo "Then, run the containers with a volume mount:"
echo ""
echo "To run the config-server:"
echo "podman run -p 8080:8080 -v \$(pwd)/my_data:/data:Z $FINAL_IMAGE_NAME"
echo ""
echo "To run the cert-roller:"
echo "podman run -v \$(pwd)/my_data:/data:Z $FINAL_IMAGE_NAME /app/cert-roller/bin/nebula-cert-roller --config /data/config/cert-roller.conf /data"


# Clean up intermediate containers
buildah rm "$build_container"
buildah rm "$runtime_container"
