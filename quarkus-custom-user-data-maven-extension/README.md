# Custom Maven Extension to make Quarkus build goal cacheable

This project performs programmatic configuration of the Gradle Enterprise Build Cache through a Maven extension. See [there](https://docs.gradle.com/enterprise/maven-extension/#custom_extension) for more details. 

This project is based on [the CCUD Maven extension](https://github.com/gradle/common-custom-user-data-maven-extension).

The modifications required to make the Quarkus build goal cacheable are isolated in the [QuarkusCachingConfig](./src/main/java/com/gradle/QuarkusCachingConfig.java) class.

## Limitations

Only the native and uber-jar [packaging types](https://quarkus.io/guides/maven-tooling#quarkus-package-pkg-package-config_quarkus.package.type) can be made cacheable.

The native packaging is cacheable only if the in-container build strategy is configured. This allows to have a build as reproducible as possible (some timestamps and instructions ordering are changing build over build even on the same system).

It is possible to configure the plugin in some files located in the classpath (```application.properties``` and ```microprofile-config.properties```), however in the current implementation those files can only be located in the current module where the goal is configured (in opposition to a 3rd party lib).

## Configuration

The caching can be disabled by setting an environment variable:
```
GRADLE_QUARKUS_CACHE_ENABLED=false
```

### Goal Inputs

The goal can be made cacheable by configuring the goal inputs which will be used to compute the goal cache key.

#### General inputs
- The compilation classpath 
- Generated sources directory
- OS details (name, version, arch)

### Quarkus properties
See [there](https://quarkus.io/guides/config-reference#configuration-sources) for details

- Quarkus System properties
- Quarkus Maven properties
- Quarkus Environment variables
- Quarkus Environment file
- ```application.properties``` (from ```user.home```)
- ```application.properties``` (from classpath)
- ```microprofile-config.properties``` (from classpath)

### Quarkus file properties
Some properties are pointing to a file which has to be declared as file input. This allows to have the file content part of the cache key (```ABSOLUTE_PATH``` strategy).
- ```quarkus.docker.dockerfile-native-path```
- ```quarkus.docker.dockerfile-jvm-path```
- ```quarkus.openshift.jvm-dockerfile```
- ```quarkus.openshift.native-dockerfile```

### Extra config locations
A special property allows to define a csv list of additional configuration files. The files referenced under this property will be added as file inputs  (```ABSOLUTE_PATH``` strategy).
