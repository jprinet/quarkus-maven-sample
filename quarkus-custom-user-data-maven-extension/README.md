# Custom Maven Extension to make Quarkus build goal cacheable

This project performs programmatic configuration of the Gradle Enterprise Build Cache through a Maven extension. See [here](https://docs.gradle.com/enterprise/maven-extension/#custom_extension) for more details.

This project is based on [the CCUD Maven extension](https://github.com/gradle/common-custom-user-data-maven-extension).

The modifications required to make the Quarkus build goal cacheable are isolated to the [QuarkusCachingConfig](./src/main/java/com/gradle/QuarkusCachingConfig.java) class.

**It is important to notice that a native executable can be a very large file, and copying it from/to the local cache, or transferring it from/to the remote cache can be an expensive operation that has to be balanced with the caching benefit**

## Limitations

Only the *native*, *uber-jar*, *jar* and *legacy-jar* [packaging types](https://quarkus.io/guides/maven-tooling#quarkus-package-pkg-package-config_quarkus.package.type) can be made cacheable.

The native packaging is cacheable only if the in-container build strategy (```quarkus.native.container-build=true```) is configured along with a fixed build image (```quarkus.native.builder-image```).
This in-container build strategy means the build is as reproducible as possible. Even so, some timestamps and instruction ordering may be different even when built on the same system in the same environment.

Only the [prod profile](https://quarkus.io/guides/building-native-image#profiles) is cacheable.

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

Quarkus' properties are fetched from the *config dump* populated by the Quarkus ```build``` goal. See [here](https://github.com/quarkusio/quarkus/pull/34713) for more details.
The ```build``` goal is cacheable only if the ```track-config-changes``` goal generates a *config dump* identical to the one generated by the previous ```build``` execution.
This ensures that the local Quarkus configuration hasn't changed since last build, otherwise a new ```build``` execution is required as a configuration can change the produced artifact.

#### Quarkus file properties
Some properties are pointing to a file which has to be declared as file input. This allows to have the file content part of the cache key (```ABSOLUTE_PATH``` strategy).
- ```quarkus.docker.dockerfile-native-path```
- ```quarkus.docker.dockerfile-jvm-path```
- ```quarkus.openshift.jvm-dockerfile```
- ```quarkus.openshift.native-dockerfile```