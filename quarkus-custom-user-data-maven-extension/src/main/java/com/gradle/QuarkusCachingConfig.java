package com.gradle;

import com.gradle.maven.extension.api.cache.BuildCacheApi;
import com.gradle.maven.extension.api.cache.MojoMetadataProvider;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.gradle.Utils.isNotEmpty;

/**
 * Caching instructions for the Quarkus build goal.
 * There are <a href="https://quarkus.io/guides/all-config">too many options</a> and ways <a href="https://quarkus.io/guides/config-reference">to configure them</a> to allow a different approach.
 */
final class QuarkusCachingConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomGradleEnterpriseConfig.class);

    private static final String GRADLE_QUARKUS_KEY_CACHE_ENABLED = "GRADLE_QUARKUS_CACHE_ENABLED";
    private static final String QUARKUS_KEY_PACKAGE_TYPE = "quarkus.package.type";
    private static final String QUARKUS_KEY_NATIVE_CONTAINER_BUILD = "quarkus.native.container-build";
    private static final String QUARKUS_KEY_NATIVE_BUILDER_IMAGE = "quarkus.native.builder-image";
    private static final String QUARKUS_VALUE_PACKAGE_NATIVE = "native";
    private static final String QUARKUS_VALUE_PACKAGE_UBERJAR = "uber-jar";

    void configureQuarkusPluginCache(BuildCacheApi buildCache) {
        buildCache.registerMojoMetadataProvider(context -> {
            context.withPlugin("quarkus-maven-plugin", () -> {
                if ("build".equals(context.getMojoExecution().getGoal())) {
                    // Load Caching configuration
                    if(isCacheEnabled()) {
                        // Load Quarkus properties
                        QuarkusPropertiesHolder quarkusProperties = new QuarkusPropertiesHolder(context);

                        // Configure cache according to package type
                        String packageType = quarkusProperties.getOrNull(QUARKUS_KEY_PACKAGE_TYPE);
                        if(isNativeBuild(packageType)) {
                            configureCacheForNativeBuild(context, quarkusProperties);
                        } else if(isUberJarBuild(packageType)) {
                            configureCacheForUberJarBuild(context, quarkusProperties);
                        } else {
                            LOGGER.info("Caching disabled for Quarkus build, package type is not supported");
                        }
                    } else {
                        LOGGER.warn("Quarkus caching is disabled (gradle.quarkus.cache.enabled=false)");
                    }
                }
            });
        });
    }

    // Cache is enabled by default
    private boolean isCacheEnabled() {
        return !Boolean.FALSE.toString().equals(System.getenv(GRADLE_QUARKUS_KEY_CACHE_ENABLED));
    }

    /**
     * This class holds the Quarkus properties. For more details see https://quarkus.io/guides/config-reference
     */
    private static class QuarkusPropertiesHolder {

        private final String APPLICATION_PROPERTIES = "application.properties";

        // List of properties for which a file input property has to be configured
        private final String[] FILE_INPUTS = new String[] {"quarkus.docker.dockerfile-native-path", "quarkus.docker.dockerfile-jvm-path", "quarkus.openshift.jvm-dockerfile", "quarkus.openshift.native-dockerfile"};

        // Mojo context
        private MojoMetadataProvider.Context context;

        // Quarkus properties configured at system level
        private final Map<String, String> systemProperties = new HashMap<>();

        // Quarkus properties configured at system level
        private final Map<String, String> mavenProperties = new HashMap<>();

        // Quarkus properties configured at environment level
        private final Map<String, String> environmentVariables = new HashMap<>();

        // Quarkus properties configured in .env file
        private final Map<String, String> envFile = new HashMap<>();

        // Quarkus properties configured in $PWD/config/application.properties
        private final Map<String, String> applicationPropertiesHome = new HashMap<>();

        // Quarkus properties configured in application.properties from classpath
        private final Map<String, String> applicationPropertiesClasspath = new HashMap<>();

        // Quarkus properties configured in META-INF/microprofile-config.properties from classpath
        private final Map<String, String> microProfilePropertiesClasspath = new HashMap<>();

        private QuarkusPropertiesHolder(MojoMetadataProvider.Context context) {
            this.context = context;
            loadSystemProperties();
            loadMavenProperties();
            loadEnvironmentVariables();
            loadEnvFile();
            loadApplicationPropsHome();
            loadApplicationPropsClasspath();
            loadMicroProfilePropsClasspath();
        }

        private String getOrNull(String key) {
            String value = systemProperties.get(key);
            if(value == null) {
                value = mavenProperties.get(key);
            }
            if(value == null) {
                value = environmentVariables.get(key);
            }
            if(value == null) {
                value = envFile.get(key);
            }
            if(value == null) {
                value = applicationPropertiesHome.get(key);
            }
            if(value == null) {
                value = applicationPropertiesClasspath.get(key);
            }
            if(value == null) {
                value = microProfilePropertiesClasspath.get(key);
            }
            return value;
        }

        private void loadSystemProperties() {
            systemProperties.putAll(context.getSession().getSystemProperties().entrySet().stream()
                    .filter(e -> e.getKey().toString().startsWith("quarkus."))
                    .collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue().toString())));
        }

        private void loadMavenProperties() {
            mavenProperties.putAll(context.getProject().getProperties().entrySet().stream()
                    .filter(e -> e.getKey().toString().startsWith("quarkus."))
                    .collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue().toString())));
        }

        private void loadEnvironmentVariables() {
            environmentVariables.putAll(System.getenv().entrySet().stream()
                    .filter(e -> e.getKey().startsWith("QUARKUS"))
                    .collect(Collectors.toMap(e -> convert(e.getKey()), Map.Entry::getValue)));
        }

        private String convert(String key) {
            return key.toLowerCase().replaceAll("[^a-zA-Z0-9]", ".");
        }

        private void loadEnvFile() {
            envFile.putAll(loadQuarkusPropertiesFromFile(context.getSession().getExecutionRootDirectory() + "/.env", this::convert));
        }

        private void loadApplicationPropsHome() {
            applicationPropertiesHome.putAll(loadQuarkusPropertiesFromFile(System.getProperty("user.home") + "/" + APPLICATION_PROPERTIES, e -> e));
        }

        private void loadApplicationPropsClasspath() {
            //TODO check for any location in the classpath?
            applicationPropertiesClasspath.putAll(loadQuarkusPropertiesFromFile(context.getProject().getBuild().getOutputDirectory() + "/" + APPLICATION_PROPERTIES, e -> e));
        }

        private void loadMicroProfilePropsClasspath() {
            //TODO check for any location in the classpath?
            microProfilePropertiesClasspath.putAll(loadQuarkusPropertiesFromFile("src/main/resources/META-INF/microprofile-config.properties", e -> e));
        }

        private Map<String, String> loadQuarkusPropertiesFromFile(String filename, Function<String,String> keyConverter) {
            if(new File(filename).exists()) {
                try (InputStream input = new FileInputStream(filename)) {
                    Properties props = new Properties();
                    props.load(input);
                    return props.entrySet().stream()
                            .filter(e -> keyConverter.apply(e.getKey().toString()).startsWith("quarkus."))
                            .collect(Collectors.toMap(e -> keyConverter.apply(e.getKey().toString()), e -> e.getValue().toString()));
                } catch (IOException e) {
                    LOGGER.error("Error while loading " + filename, e);
                }
            }

            return Collections.emptyMap();
        }

        private Map<String, Map<String, String>> getQuarkusPropertiesBySources() {
            Map<String, Map<String,String>> quarkusPropertiesBySource = new HashMap<>();
            quarkusPropertiesBySource.put("quarkusSysProps", systemProperties);
            quarkusPropertiesBySource.put("quarkusMavenProps", mavenProperties);
            quarkusPropertiesBySource.put("quarkusEnvVars", environmentVariables);
            quarkusPropertiesBySource.put("quarkusEnvFile", envFile);
            quarkusPropertiesBySource.put("quarkusAppPropsHome", applicationPropertiesHome);
            quarkusPropertiesBySource.put("quarkusAppPropsClasspath", applicationPropertiesClasspath);
            quarkusPropertiesBySource.put("microProfilePropsClasspath", microProfilePropertiesClasspath);
            return quarkusPropertiesBySource;
        }

        public Map<String, String> getQuarkusFileProperties() {
            Map<String, String> quarkusFileProperties = new HashMap<>();

            // Iterate over list of file inputs
            for(String fileInputProperty : FILE_INPUTS) {
                String fileInputValue = getOrNull(fileInputProperty);
                if(isNotEmpty(fileInputValue)) {
                    quarkusFileProperties.put(fileInputProperty, fileInputValue);
                }
            }

            // Iterate over list of quarkus.config.locations
            String configLocationsAsString = getOrNull("quarkus.config.locations");
            if(isNotEmpty(configLocationsAsString)) {
                String[] configLocations = configLocationsAsString.split(",");
                for(String configLocation : configLocations) {
                    if(new File(configLocation).exists()) {
                        quarkusFileProperties.put("quarkus.config.locations." + configLocation.replace(File.separatorChar, '-'), configLocation);
                    }
                }

            }

            return quarkusFileProperties;
        }
    }

    private void configureCacheForNativeBuild(MojoMetadataProvider.Context context, QuarkusPropertiesHolder quarkusProperties) {
        LOGGER.info("Configuring caching for Quarkus native build");
        if(isInContainerBuild(quarkusProperties)) {
            String outputFileName = "target/" + context.getProject().getBuild().getFinalName() + "-runner";
            LOGGER.info("cache output = " + outputFileName);
            context.inputs(inputs -> configureInputs(context, inputs, quarkusProperties))
                   .outputs(outputs -> outputs.file("exe", outputFileName).cacheable("this plugin has CPU-bound goals with well-defined inputs and outputs"));
        } else {
            LOGGER.warn("Caching disabled for Quarkus build, please use stable container build");
        }
    }

    private void configureCacheForUberJarBuild(MojoMetadataProvider.Context context, QuarkusPropertiesHolder quarkusProperties) {
        LOGGER.info("Configuring caching for Quarkus uberjar build");
        String outputFileName = "target/" + context.getProject().getBuild().getFinalName() + "-runner.jar";
        LOGGER.info("cache output = " + outputFileName);
        context.inputs(inputs -> configureInputs(context, inputs, quarkusProperties))
            .outputs(outputs -> outputs.file("jar", outputFileName).cacheable("this plugin has CPU-bound goals with well-defined inputs and outputs"));
    }

    private void configureInputs(MojoMetadataProvider.Context context, MojoMetadataProvider.Context.Inputs inputs, QuarkusPropertiesHolder quarkusProperties) {
        try {
            List<String> compileClasspathElements = context.getProject().getCompileClasspathElements();

            inputs
                    .fileSet("quarkusCompileClasspath", compileClasspathElements, fileSet -> fileSet.normalizationStrategy(MojoMetadataProvider.Context.FileSet.NormalizationStrategy.CLASSPATH))
                    .fileSet("generatedSourcesDirectory", fileSet -> {})
                    .properties("appArtifact", "closeBootstrappedApp", "finalName", "ignoredEntries", "manifestEntries", "manifestSections", "skip", "skipOriginalJarRename", "systemProperties", "properties")
                    .property("osName", getOsName())
                    .property("osVersion", getOsVersion())
                    .property("osArch", getOsArch())
                    .ignore("project", "buildDir", "mojoExecution", "session", "repoSession", "repos", "pluginRepos");

            // Add Quarkus input properties
            for(Map.Entry<String,Map<String,String>> quarkusPropertiesBySource : quarkusProperties.getQuarkusPropertiesBySources().entrySet()) {
                // Each input has its own map of inputs
                for(Map.Entry<String,String> quarkusPropertiesForSource : quarkusPropertiesBySource.getValue().entrySet()) {
                    inputs.property(quarkusPropertiesBySource.getKey() + "-" + quarkusPropertiesForSource.getKey(), quarkusPropertiesForSource.getValue());
                }
            }

            // Add Quarkus input file properties
            for(Map.Entry<String, String> quarkusFileProperty : quarkusProperties.getQuarkusFileProperties().entrySet()) {
                // Those files can be anywhere on the system, hence the absolute path
                inputs.fileSet(quarkusFileProperty.getKey(), new File(quarkusFileProperty.getValue()), fileSet -> fileSet.normalizationStrategy(MojoMetadataProvider.Context.FileSet.NormalizationStrategy.ABSOLUTE_PATH));
            }
        } catch (DependencyResolutionRequiredException e) {
            throw new IllegalStateException("Classpath can't be resolved");
        }
    }

    private boolean isNativeBuild(String packageType) {
        return QUARKUS_VALUE_PACKAGE_NATIVE.equals(packageType);
    }

    private boolean isUberJarBuild(String packageType) {
        return QUARKUS_VALUE_PACKAGE_UBERJAR.contains(packageType);
    }

    // Verify that the Quarkus configuration is using in-container build mode and relies on stable build image
    private boolean isInContainerBuild(QuarkusPropertiesHolder quarkusProperties) {
        boolean isContainerBuild = Boolean.parseBoolean(quarkusProperties.getOrNull(QUARKUS_KEY_NATIVE_CONTAINER_BUILD));
        String builderImage = quarkusProperties.getOrNull(QUARKUS_KEY_NATIVE_BUILDER_IMAGE);

        return isContainerBuild && isNotEmpty(builderImage);
    }

    private String getOsName() {
        return System.getProperty("os.name");
    }

    private String getOsVersion() {
        return System.getProperty("os.version");
    }

    private String getOsArch() {
        return System.getProperty("os.arch");
    }
}
