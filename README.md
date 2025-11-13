## sbt project compiled with Scala 3

### Usage

This is a normal sbt project. You can compile code with `sbt compile`, run it with `sbt run`, and `sbt console` will start a Scala 3 REPL.

For more information on the sbt-dotty plugin, see the
[scala3-example-project](https://github.com/scala/scala3-example-project/blob/main/README.md).

### Building and Running with Buildah

This project can be built into a container image using Buildah. A `build.sh` script is provided to automate this process.

**Prerequisites:**
*   Buildah installed on your system.
*   `sbt` installed locally (or you can modify `build.sh` to use a local `sbt` installation if preferred).

**Build the Image:**
To build the container image, simply run the `build.sh` script from the project root:
```bash
./build.sh
```
This will create a container image named `nebula-rolling`.

**Run the Config Server:**
To run the `config-server` application in a container, exposing its default port (8080):
```bash
podman run -p 8080:8080 nebula-rolling
```

**Run the Cert Roller:**
The `cert-roller` is a one-shot application that requires a `pubs` directory containing public keys and will output generated certificates. You can run it by overriding the container's default command and mounting the necessary volumes:
```bash
# Create a 'pubs' directory with your public keys (e.g., pubs/host1.pub)
mkdir -p pubs

# Run the cert-roller, mounting 'pubs' and specifying an output base directory
podman run -v $(pwd)/pubs:/app/pubs nebula-rolling /app/cert-roller/bin/nebula-cert-roller /app
```
After running the `cert-roller`, a new `config_<timestamp>` directory will be created in your current working directory (if `/app` was mapped to `$(pwd)`).