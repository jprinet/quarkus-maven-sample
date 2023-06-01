# Custom Maven Extension to make Quarkus build goal cacheable

This project performs programmatic configuration of the Gradle Enterprise Build Cache through a Maven extension. See [here](https://docs.gradle.com/enterprise/maven-extension/#custom_extension) for more details. 

This project is based on [the CCUD Maven extension](https://github.com/gradle/common-custom-user-data-maven-extension).

The modifications required to make the Quarkus build goal cacheable are isolated to the [QuarkusCachingConfig](./src/main/java/com/gradle/QuarkusCachingConfig.java) class.

**It is important to notice that a native executable can be a very large file, and copying it from/to the local cache, or transferring it from/to the remote cache can be an expensive operation that has to be balanced with the caching benefit** 

## Limitations

Only the native and uber-jar [packaging types](https://quarkus.io/guides/maven-tooling#quarkus-package-pkg-package-config_quarkus.package.type) can be made cacheable.

The native packaging is cacheable only if the in-container build strategy (```quarkus.native.container-build=true```) is configured along with a fixed build image (```quarkus.native.builder-image```). 
This in-container build strategy means the build is as reproducible as possible. Even so, some timestamps and instruction ordering may be different even when built on the same system in the same environment.

It is possible to configure the plugin via certain files located in the classpath (```application.properties``` and ```microprofile-config.properties```), however in the current implementation those files will only be considered as part of the cache key if they are located in the current module where the goal is configured. They will not be considered as part of the cache key if they are located in a 3rd party lib.

## Configuration

The caching can be disabled by setting an environment variable:
```
GRADLE_QUARKUS_CACHE_ENABLED=false
```

### Goal Inputs

The `QuarkusCachingConfig` makes the Quarkus build goal cacheable by configuring the following goal inputs:

#### General inputs
- The compilation classpath 
- Generated sources directory
- OS details (name, version, arch)

#### Quarkus properties
See [here](https://quarkus.io/guides/config-reference#configuration-sources) for details

- Quarkus System properties
- Quarkus Maven properties
- Quarkus Environment variables
- Quarkus Environment file
- ```application.properties``` (from ```user.home```)
- ```application.properties``` (from classpath)
- ```microprofile-config.properties``` (from classpath)

#### Quarkus file properties
Some properties are pointing to a file which has to be declared as file input. This allows to have the file content part of the cache key (```ABSOLUTE_PATH``` strategy).
- ```quarkus.docker.dockerfile-native-path```
- ```quarkus.docker.dockerfile-jvm-path```
- ```quarkus.openshift.jvm-dockerfile```
- ```quarkus.openshift.native-dockerfile```

#### Extra config locations
A special property allows to define a csv list of additional configuration files. The files referenced under this property will be added as file inputs  (```ABSOLUTE_PATH``` strategy).
