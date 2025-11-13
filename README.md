## sbt project compiled with Scala 3

### Usage

This is a normal sbt project. You can compile code with `sbt compile`, run it with `sbt run`, and `sbt console` will start a Scala 3 REPL.

For more information on the sbt-dotty plugin, see the
[scala3-example-project](https://github.com/scala/scala3-example-project/blob/main/README.md).

### Building and Running with Buildah

This project can be built into a container image using Buildah. A `build.sh` script is provided to automate this process.

**Prerequisites:**
*   Buildah installed on your system.

**Build the Image:**
To build the container image, simply run the `build.sh` script from the project root:
```bash
./build.sh
```
This will create a container image named `nebula-rolling`.

### Running the Container

The container is designed to be run with a single data volume mounted at `/data`. This volume centralizes all configuration, input, and output data.

**1. Prepare Your Data Directory**

On your host machine, create a directory to be mounted into the container. This directory should contain your configuration files and public keys.

Example structure:
```
my_data/
├── config/
│   ├── config-server.conf
│   └── cert-roller.conf
└── pubs/
    └── host1.pub
```

**Important:** Your configuration files inside `my_data/config/` should be updated to use paths relative to the `/data` mount point inside the container. For example, in `cert-roller.conf`, you should have `pubDir = "pubs"`, and in `config-server.conf`, you should have `configDir = "/data"`.

**2. Run the Config Server**

The default command for the container starts the `config-server`. It assumes its configuration is located at `/data/application.conf`.

```bash
podman run -p 8080:8080 -v $(pwd)/my_data:/data:Z nebula-rolling
```
The `:Z` at the end of the volume mount is important for SELinux systems to ensure the container can write to the mounted volume.

**3. Run the Cert Roller**

To run the `cert-roller`, you override the container's default command. This command generates certificates based on the public keys in `/data/pubs` and writes them to a new `config_<timestamp>` directory inside `/data`.

```bash
podman run -v $(pwd)/my_data:/data:Z nebula-rolling /app/cert-roller/bin/nebula-cert-roller --config /data/application.conf /data
```
After running, a new `config_<timestamp>` directory will appear in your local `my_data` directory.
