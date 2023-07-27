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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Caching instructions for the Quarkus build goal.
 */
final class QuarkusCachingConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomGradleEnterpriseConfig.class);

    // Quarkus' configuration keys
    private static final List<String> QUARKUS_CONFIG_KEY_NATIVE_CONTAINER_BUILD = Arrays.asList("quarkus.native.container-build", "quarkus.native.remote-container-build");
    private static final String QUARKUS_CONFIG_KEY_NATIVE_BUILDER_IMAGE = "quarkus.native.builder-image";
    private static final String QUARKUS_CONFIG_KEY_PACKAGE_TYPE = "quarkus.package.type";

    // Quarkus' cacheable package types
    private static final List<String> QUARKUS_CACHEABLE_PACKAGE_TYPES = Arrays.asList("jar", "legacy-jar", "uber-jar", "native");

    // Quarkus' properties which are considered as file inputs
    private static final List<String> QUARKUS_KEYS_AS_FILE_INPUTS = Arrays.asList("quarkus.docker.dockerfile-native-path", "quarkus.docker.dockerfile-jvm-path", "quarkus.openshift.jvm-dockerfile", "quarkus.openshift.native-dockerfile");

    // Inner class to encapsulate Quarkus extension configuration
    private static final class QuarkusExtensionConfiguration {

        // Environment variable key to disable caching
        private static final String GRADLE_QUARKUS_KEY_CACHE_ENABLED = "GRADLE_QUARKUS_CACHE_ENABLED";

        // Environment variable or Maven property key to define extension configuration file location
        private static final String GRADLE_QUARKUS_KEY_CONFIG_FILE = "GRADLE_QUARKUS_EXTENSION_CONFIG_FILE";

        // Extension configuration build profile key
        private static final String GRADLE_QUARKUS_KEY_BUILD_PROFILE = "BUILD_PROFILE";

        // Extension configuration default profile
        private static final String GRADLE_QUARKUS_DEFAULT_BUILD_PROFILE = "prod";

        // Extension configuration dump config file prefix
        private static final String GRADLE_QUARKUS_KEY_DUMP_CONFIG_PREFIX = "DUMP_CONFIG_PREFIX";

        // Extension configuration default dump config file prefix
        private static final String GRADLE_QUARKUS_DEFAULT_DUMP_CONFIG_PREFIX = "quarkus";

        // Extension configuration dump config file suffix
        private static final String GRADLE_QUARKUS_KEY_DUMP_CONFIG_SUFFIX = "DUMP_CONFIG_SUFFIX";

        // Extension configuration default dump config file suffix
        private static final String GRADLE_QUARKUS_DEFAULT_DUMP_CONFIG_SUFFIX = "config-dump";

        private final Properties configuration = new Properties();

        private QuarkusExtensionConfiguration(MojoMetadataProvider.Context context) {
            // loading default properties
            configuration.setProperty(GRADLE_QUARKUS_KEY_CACHE_ENABLED, System.getenv(GRADLE_QUARKUS_KEY_CACHE_ENABLED));
            configuration.setProperty(GRADLE_QUARKUS_KEY_BUILD_PROFILE, GRADLE_QUARKUS_DEFAULT_BUILD_PROFILE);
            configuration.setProperty(GRADLE_QUARKUS_KEY_DUMP_CONFIG_PREFIX, GRADLE_QUARKUS_DEFAULT_DUMP_CONFIG_PREFIX);
            configuration.setProperty(GRADLE_QUARKUS_KEY_DUMP_CONFIG_SUFFIX, GRADLE_QUARKUS_DEFAULT_DUMP_CONFIG_SUFFIX);

            // loading optional overridden locations
            String extensionConfigurationFileFromEnv = System.getenv(GRADLE_QUARKUS_KEY_CONFIG_FILE);
            String extensionConfigurationFileFromMaven =
                context.getProject().getProperties().getProperty(
                    GRADLE_QUARKUS_KEY_CONFIG_FILE.toLowerCase().replace("_",".")
                );

            if(extensionConfigurationFileFromEnv != null && !extensionConfigurationFileFromEnv.isEmpty()) {
                // override default properties from configuration file defined in the environment
                configuration.putAll(loadProperties(extensionConfigurationFileFromEnv));
            } else if(extensionConfigurationFileFromMaven != null && !extensionConfigurationFileFromMaven.isEmpty()) {
                // override default properties from configuration file defined as Maven property
                configuration.putAll(loadProperties(extensionConfigurationFileFromMaven));
            }
        }

        /**
         * @return whether Quarkus cache is enabled or not
         */
        private boolean isQuarkusCacheEnabled() {
            // Quarkus cache is enabled by default
            return !Boolean.FALSE.toString().equals(configuration.get(GRADLE_QUARKUS_KEY_CACHE_ENABLED));
        }

        /**
         * This file contains Quarkus' properties used to configure the application.
         * This file is generated by the Quarkus build goal.
         *
         * @return dump config file name
         */
        private String getDumpConfigFileName() {
            return String.format(".quarkus/%s-%s-%s",
                configuration.getProperty(GRADLE_QUARKUS_KEY_DUMP_CONFIG_PREFIX),
                configuration.getProperty(GRADLE_QUARKUS_KEY_BUILD_PROFILE),
                configuration.getProperty(GRADLE_QUARKUS_KEY_DUMP_CONFIG_SUFFIX)
            );
        }

        /**
         * This file contains Quarkus' properties values when process-resources phase is executed.
         * It is generated by the Quarkus track-config-changes goal.
         *
         * @return config check file name
         */
        private String getCurrentConfigFileName() {
            return String.format("target/%s-%s-config-check",
                configuration.getProperty(GRADLE_QUARKUS_KEY_DUMP_CONFIG_PREFIX),
                configuration.getProperty(GRADLE_QUARKUS_KEY_BUILD_PROFILE)
            );
        }
    }

    void configureQuarkusPluginCache(BuildCacheApi buildCache) {
        buildCache.registerMojoMetadataProvider(context -> {
            context.withPlugin("quarkus-maven-plugin", () -> {
                if("build".equals(context.getMojoExecution().getGoal())) {
                    QuarkusExtensionConfiguration extensionConfiguration = new QuarkusExtensionConfiguration(context);

                    if(extensionConfiguration.isQuarkusCacheEnabled()) {
                        LOGGER.info("Configuring caching for Quarkus build");
                        configureQuarkusBuildGoal(context, extensionConfiguration);
                    } else {
                        LOGGER.info("Quarkus caching is disabled (gradle.quarkus.cache.enabled=false)");
                    }
                }
            });
        });
    }

    private void configureQuarkusBuildGoal(MojoMetadataProvider.Context context, QuarkusExtensionConfiguration extensionConfiguration) {
        // Load Quarkus build properties
        Properties quarkusBuildProperties = loadProperties(extensionConfiguration.getDumpConfigFileName());

        // Check required configuration
        if(isQuarkusBuildCacheable(quarkusBuildProperties, extensionConfiguration)) {
            configureInputs(context, quarkusBuildProperties);
            configureOutputs(context);
        } else {
            LOGGER.info("Caching not possible for Quarkus goal");
        }
    }

    private static Properties loadProperties(String propertyFile) {
        Properties props = new Properties();
        if(new File(propertyFile).exists()) {
            try (InputStream input = new FileInputStream(propertyFile)) {
                props.load(input);
            } catch (IOException e) {
                //TODO check props is not null
                LOGGER.error("Error while loading " + propertyFile, e);
            }
        }
        return props;
    }

    private boolean isQuarkusBuildCacheable(Properties quarkusBuildProperties, QuarkusExtensionConfiguration extensionConfiguration) {
        return isQuarkusPropertiesUnchanged(quarkusBuildProperties, extensionConfiguration)
            && isInContainerBuild(quarkusBuildProperties)
            && isPackagingTypeSupported(quarkusBuildProperties);
    }

    private boolean isQuarkusPropertiesUnchanged(Properties quarkusProperties, QuarkusExtensionConfiguration extensionConfiguration) {
        Properties quarkusCurrentProperties = loadProperties(extensionConfiguration.getCurrentConfigFileName());

        Set<Map.Entry<Object, Object>> quarkusPropertiesCopy = new HashSet<>(quarkusProperties.entrySet());
        quarkusPropertiesCopy.removeAll(quarkusCurrentProperties.entrySet());

        if(quarkusPropertiesCopy.size() > 0) {
            LOGGER.info("Quarkus properties have changed [" + quarkusPropertiesCopy.stream().map(e -> e.getKey().toString()).collect(Collectors.joining(", ")) + "]");
        } else {
            return true;
        }

        return false;
    }

    // Checking native package type is not required as the container build is set by default to true for non native
    private boolean isInContainerBuild(Properties quarkusProperties) {
        String builderImage = quarkusProperties.getProperty(QUARKUS_CONFIG_KEY_NATIVE_BUILDER_IMAGE);
        if(builderImage == null || builderImage.isEmpty()) {
            LOGGER.info("Quarkus build is not using a fixed image");
            return false;
        }

        if(QUARKUS_CONFIG_KEY_NATIVE_CONTAINER_BUILD.stream().noneMatch(key -> Boolean.parseBoolean(quarkusProperties.getProperty(key)))) {
            LOGGER.info("Quarkus build is not in-container");
            return false;
        }

        return true;
    }

    private boolean isPackagingTypeSupported(Properties quarkusProperties) {
        if(QUARKUS_CACHEABLE_PACKAGE_TYPES.stream().noneMatch(key -> key.equals(quarkusProperties.getProperty(QUARKUS_CONFIG_KEY_PACKAGE_TYPE)))) {
            LOGGER.info("Quarkus package type is not cacheable");
            return false;
        }

        return true;
    }

    private void configureInputs(MojoMetadataProvider.Context context, Properties quarkusProperties) {
        context.inputs(inputs -> {
            addOsInputs(inputs);
            addClasspathInput(context, inputs);
            addMojoInputs(inputs);
            addQuarkusPropertiesInput(inputs);
            addQuarkusFilesInputs(inputs, quarkusProperties);
        });
    }

    private void addOsInputs(MojoMetadataProvider.Context.Inputs inputs) {
        inputs.property("osName", System.getProperty("os.name"))
            .property("osVersion", System.getProperty("os.version"))
            .property("osArch", System.getProperty("os.arch"));
    }

    private void addClasspathInput(MojoMetadataProvider.Context context, MojoMetadataProvider.Context.Inputs inputs) {
        try {
            List<String> compileClasspathElements = context.getProject().getCompileClasspathElements();
            inputs.fileSet("quarkusCompileClasspath", compileClasspathElements, fileSet -> fileSet.normalizationStrategy(MojoMetadataProvider.Context.FileSet.NormalizationStrategy.CLASSPATH));
        } catch (DependencyResolutionRequiredException e) {
            throw new IllegalStateException("Classpath can't be resolved");
        }
    }

    private void addMojoInputs(MojoMetadataProvider.Context.Inputs inputs) {
        inputs
            .fileSet("generatedSourcesDirectory", fileSet -> {})
            .properties("appArtifact", "closeBootstrappedApp", "finalName", "ignoredEntries", "manifestEntries", "manifestSections", "skip", "skipOriginalJarRename", "systemProperties", "properties")
            .ignore("project", "buildDir", "mojoExecution", "session", "repoSession", "repos", "pluginRepos", "attachRunnerAsMainArtifact", "bootstrapId", "buildDirectory");
    }

    private void addQuarkusPropertiesInput(MojoMetadataProvider.Context.Inputs inputs) {
        inputs.fileSet("quarkusCopyProperties", new File("target/quarkus-app-prod-config-dump"), fileSet -> fileSet.normalizationStrategy(MojoMetadataProvider.Context.FileSet.NormalizationStrategy.RELATIVE_PATH));
        inputs.fileSet("generatedSourcesDirectory", fileSet -> {});
    }

    private void addQuarkusFilesInputs(MojoMetadataProvider.Context.Inputs inputs, Properties quarkusProperties) {
        for(String quarkusFilePropertyKey : QUARKUS_KEYS_AS_FILE_INPUTS) {
            String quarkusFilePropertyValue = quarkusProperties.getProperty(quarkusFilePropertyKey);
            if(Utils.isNotEmpty(quarkusFilePropertyValue)) {
                inputs.fileSet(quarkusFilePropertyKey, new File(quarkusFilePropertyValue), fileSet -> fileSet.normalizationStrategy(MojoMetadataProvider.Context.FileSet.NormalizationStrategy.RELATIVE_PATH));
            }
        }
    }

    private void configureOutputs(MojoMetadataProvider.Context context) {
        context.outputs(outputs -> {
            String quarkusExeFileName = "target/" + context.getProject().getBuild().getFinalName() + "-runner";
            String quarkusJarFileName = "target/" + context.getProject().getBuild().getFinalName() + ".jar";
            String quarkusUberJarFileName = "target/" + context.getProject().getBuild().getFinalName() + "-runner.jar";

            outputs.cacheable("this plugin has CPU-bound goals with well-defined inputs and outputs");
            outputs.file("quarkusExe", quarkusExeFileName);
            outputs.file("quarkusJar", quarkusJarFileName);
            outputs.file("quarkusUberJar", quarkusUberJarFileName);

            // Do not declare dump config as goal output to avoid
            // Goal execution marked as not cacheable: Build caching was not enabled for this goal execution because pre-existing files were modified. Cacheable goals may only create new files.
            // outputs.file("quarkusProperties", QUARKUS_FILE_PROPERTIES_DUMP);
        });
    }
}
