package com.gradle;

import com.gradle.maven.extension.api.cache.BuildCacheApi;
import com.gradle.maven.extension.api.cache.MojoMetadataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.AbstractMap;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import static com.gradle.Utils.isNotEmpty;

/**
 * These caching instructions assumes that Quarkus properties are set in the Quarkus configuration file (application.properties).
 * There are <a href="https://quarkus.io/guides/all-config">too many options</a> and ways <a href="https://quarkus.io/guides/config-reference">to configure them</a> to allow a different approach.
 */
final class QuarkusCachingConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomGradleEnterpriseConfig.class);

    private static final String QUARKUS_CONFIGURATION_FILE = "src/main/resources/application.properties";
    private static final String QUARKUS_KEY_PACKAGE_TYPE = "quarkus.package.type";
    private static final String QUARKUS_KEY_NATIVE_CONTAINER_BUILD = "quarkus.native.container-build";
    private static final String QUARKUS_KEY_NATIVE_BUILDER_IMAGE = "quarkus.native.builder-image";
    private static final String QUARKUS_VALUE_PACKAGE_NATIVE = "native";
    private static final String QUARKUS_VALUE_PACKAGE_UBERJAR = "uber-jar";

    void configureQuarkusPluginCache(BuildCacheApi buildCache) {
        buildCache.registerMojoMetadataProvider(context -> {
            context.withPlugin("quarkus-maven-plugin", () -> {
                if ("build".equals(context.getMojoExecution().getGoal())) {
                    try {
                        Map<String, String> quarkusMavenProperties = getQuarkusMavenProperties(context);

                        String packageType = getProperty(quarkusMavenProperties, QUARKUS_KEY_PACKAGE_TYPE);
                        if(isNativeBuild(packageType)) {
                            configureCacheForNativeBuild(context, quarkusMavenProperties);
                        } else if(isUberJarBuild(packageType)) {
                            configureCacheForUberJarBuild(context, quarkusMavenProperties);
                        } else {
                            LOGGER.info("Caching disabled for Quarkus build, package type is not supported");
                        }
                    } catch (IllegalArgumentException e) {
                        LOGGER.info("Caching disabled for Quarkus build, {}", e.getLocalizedMessage());
                    }
                }
            });
        });
    }

    private Map<String, String> getQuarkusMavenProperties(MojoMetadataProvider.Context context) {
        return context.getProject().getProperties().entrySet().stream()
                .filter(e -> e.getKey().toString().startsWith("quarkus."))
                .collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue().toString()));
    }

    // Read Quarkus property by order of precedence from:
    // - maven properties
    // - environment
    // - application.properties
    private String getProperty(Map<String, String> quarkusMavenProperties, String propertyKey) {
        //FIXME handle classpath location for configuration file
        String propertyValue = getPropertyFromMavenPropertiesOrNull(quarkusMavenProperties, propertyKey);
        if(propertyValue == null) {
            propertyValue = getPropertyFromEnvironmentOrNull(propertyKey);
            if(propertyValue == null) {
                propertyValue = getPropertyFromConfigurationFileOrNull(propertyKey);
                if(propertyValue == null) {
                    LOGGER.warn("Please define quarkus configuration property [{}] to allow goal caching", propertyKey);
                    throw new IllegalArgumentException(String.format("Property [%s] is not set", propertyKey));
                }
            }
        }


        return propertyValue;
    }

    private String getPropertyFromMavenPropertiesOrNull(Map<String, String> quarkusMavenProperties, String propertyKey) {
        return quarkusMavenProperties.get(propertyKey);
    }

    private String getPropertyFromEnvironmentOrNull(String propertyKey) {
        return System.getenv().get(propertyKey);
    }

    private String getPropertyFromConfigurationFileOrNull(String propertyKey) {
        try (InputStream input = Files.newInputStream(Paths.get(QUARKUS_CONFIGURATION_FILE))) {
            Properties prop = new Properties();
            prop.load(input);
            return prop.getProperty(propertyKey);
        } catch (IOException ex) {
            LOGGER.warn("Error reading Quarkus configuration file");
            return null;
        }
    }

    private void configureCacheForNativeBuild(MojoMetadataProvider.Context context, Map<String, String> quarkusMavenProperties) {
        LOGGER.info("Configuring caching for Quarkus native build");
        if(isInContainerBuild(quarkusMavenProperties)) {
            String outputFileName = "target/" + context.getProject().getBuild().getFinalName() + "-runner";
            LOGGER.info("cache output = " + outputFileName);
            context.inputs(inputs -> getInputs(inputs, quarkusMavenProperties))
                   .outputs(outputs -> outputs.file("exe", outputFileName).cacheable("this plugin has CPU-bound goals with well-defined inputs and outputs"));
        } else {
            LOGGER.warn("Caching disabled for Quarkus build, please use stable container build");
        }
    }

    private void configureCacheForUberJarBuild(MojoMetadataProvider.Context context, Map<String, String> quarkusMavenProperties) {
        LOGGER.info("Configuring caching for Quarkus uberjar build");
        String outputFileName = "target/" + context.getProject().getBuild().getFinalName() + ".jar";
        LOGGER.info("cache output = " + outputFileName);
        context.inputs(inputs -> getInputs(inputs, quarkusMavenProperties))
                .outputs(outputs -> outputs.file("jar", outputFileName).cacheable("this plugin has CPU-bound goals with well-defined inputs and outputs"));
    }

    // Reusing the same inputs for Jar and Native. The created Jar is indeed OS dependent.
    private MojoMetadataProvider.Context.Inputs getInputs(MojoMetadataProvider.Context.Inputs inputs, Map<String, String> quarkusMavenProperties) {
        return inputs
                .fileSet("quarkusProperties", "src/main/resources", fileSet -> fileSet.include("application.properties").normalizationStrategy(MojoMetadataProvider.Context.FileSet.NormalizationStrategy.RELATIVE_PATH))
                .fileSet("generatedSourcesDirectory", fileSet -> {
                })
                .properties("appArtifact", "closeBootstrappedApp", "finalName", "ignoredEntries", "manifestEntries", "manifestSections", "skip", "skipOriginalJarRename", "systemProperties", "properties")
                .property("quarkusEnv", hashQuarkusEnvironmentVariables())
                .property("quarkusMavenProps", hashQuarkusMavenProperties(quarkusMavenProperties))
                .property("osName", getOsName())
                .property("osVersion", getOsVersion())
                .property("osArch", getOsArch())
                .ignore("project", "buildDir", "mojoExecution", "session", "repoSession", "repos", "pluginRepos");
    }

    private String hashQuarkusEnvironmentVariables() {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");

            System.getenv().entrySet().stream()
                    .filter(e -> e.getKey().startsWith("quarkus."))
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> messageDigest.update((e.getKey()+e.getValue()).getBytes()));

            return Base64.getEncoder().encodeToString(messageDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            LOGGER.error("Unsupported algorithm", e);
            throw new IllegalStateException(e);
        }
    }

    private String hashQuarkusMavenProperties(Map<String, String> quarkusMavenProperties) {
        if(quarkusMavenProperties != null) {
            try {
                MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");

                quarkusMavenProperties.entrySet().stream()
                        .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue()))
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(e -> messageDigest.update((e.getKey()+e.getValue()).getBytes()));

                return Base64.getEncoder().encodeToString(messageDigest.digest());
            } catch (NoSuchAlgorithmException e) {
                LOGGER.error("Unsupported algorithm", e);
                throw new IllegalStateException(e);
            }
        } else {
            return "";
        }
    }

    private boolean isNativeBuild(String packageType) {
        return QUARKUS_VALUE_PACKAGE_NATIVE.equals(packageType);
    }

    private boolean isUberJarBuild(String packageType) {
        return QUARKUS_VALUE_PACKAGE_UBERJAR.contains(packageType);
    }

    // Verify that the Quarkus configuration is defined in the Quarkus configuration file (which is defined as goal input) and relies on stable container build
    private boolean isInContainerBuild(Map<String, String> quarkusMavenProperties) {
        boolean isContainerBuild = Boolean.parseBoolean(getProperty(quarkusMavenProperties, QUARKUS_KEY_NATIVE_CONTAINER_BUILD));
        String builderImage = getProperty(quarkusMavenProperties, QUARKUS_KEY_NATIVE_BUILDER_IMAGE);

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
