package com.gradle;

import com.gradle.maven.extension.api.GradleEnterpriseApi;
import com.gradle.maven.extension.api.cache.BuildCacheApi;
import com.gradle.maven.extension.api.cache.MojoMetadataProvider;
import com.gradle.maven.extension.api.scan.BuildScanApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

import static com.gradle.Utils.isNotEmpty;

/**
 * Provide standardized Gradle Enterprise configuration.
 * By applying the extension, these settings will automatically be applied.
 */
final class CustomGradleEnterpriseConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomGradleEnterpriseConfig.class);
    private static final String QUARKUS_CONFIGURATION_FILE = "src/main/resources/application.properties";
    private static final String QUARKUS_NATIVE_CONTAINER_BUILD = "quarkus.native.container-build";
    private static final String QUARKUS_NATIVE_BUILDER_IMAGE = "quarkus.native.builder-image";

    void configureGradleEnterprise(GradleEnterpriseApi gradleEnterprise) {
        /* Example of Gradle Enterprise configuration
        gradleEnterprise.setServer("http://localhost:5086");
        gradleEnterprise.setAllowUntrustedServer(true);

        gradleEnterprise.setServer("https://enterprise-samples.gradle.com");
        gradleEnterprise.setAllowUntrustedServer(false);

        */
    }

    void configureBuildScanPublishing(BuildScanApi buildScans) {
        buildScans.tag("api-config");
        /* Example of build scan publishing configuration

        boolean isCiServer = System.getenv().containsKey("CI");

        buildScans.publishAlways();
        buildScans.setCaptureGoalInputFiles(true);
        buildScans.setUploadInBackground(!isCiServer);

        */
    }

    void configureBuildCache(BuildCacheApi buildCache) {
        configureQuarkusPluginCache(buildCache);
    }

    private void configureQuarkusPluginCache(BuildCacheApi buildCache) {
        buildCache.registerMojoMetadataProvider(context -> {
            context.withPlugin("quarkus-maven-plugin", () -> {
                if ("build".equals(context.getMojoExecution().getExecutionId())) {
                    if(isInContainerBuild(context.getProject().getProperties())) {
                        context.inputs(inputs -> inputs
                                        .fileSet("quarkusProperties", "src/main/resources",fileSet -> fileSet.include("application.properties").normalizationStrategy(MojoMetadataProvider.Context.FileSet.NormalizationStrategy.RELATIVE_PATH))
                                        .fileSet("generatedSourcesDirectory", fileSet -> {})
                                        .properties("appArtifact", "closeBootstrappedApp", "finalName", "ignoredEntries", "manifestEntries", "manifestSections", "skip", "skipOriginalJarRename", "systemProperties", "properties")
                                        .property("osName", getOsName())
                                        .property("osVersion", getOsVersion())
                                        .property("osArch", getOsArch())
                                        .ignore("project", "buildDir", "mojoExecution", "session", "repoSession", "repos", "pluginRepos")
                                )
                                .outputs(outputs -> outputs.file("exe", "${project.build.directory}/${project.name}-${project.version}-runner").cacheable("this plugin has CPU-bound goals with well-defined inputs and outputs"));
                    } else {
                        LOGGER.warn("Caching disabled for Quarkus build, please use stable container build");
                    }
                }
            });
        });
    }

    private String getOsName() {
        LOGGER.warn(System.getProperty("os.name"));
        return System.getProperty("os.name");
    }

    private String getOsVersion() {
        LOGGER.warn(System.getProperty("os.version"));
        return System.getProperty("os.version");
    }

    private String getOsArch() {
        LOGGER.warn(System.getProperty("os.arch"));
        return System.getProperty("os.arch");
    }

    // Verify that the Quarkus configuration is defined in the Quarkus configuration file (which is defined as goal input) and relies on stable container build
    // - native packaging
    // - container build
    // - a tagged image build
    private boolean isInContainerBuild(Properties mavenProperties) {
        boolean isContainerBuild = Boolean.parseBoolean(getQuarkusProperty(mavenProperties, QUARKUS_NATIVE_CONTAINER_BUILD));
        String builderImage = getQuarkusProperty(mavenProperties, QUARKUS_NATIVE_BUILDER_IMAGE);

        return isContainerBuild && isNotEmpty(builderImage);
    }

    // Read Quarkus property from application.properties
    private String getQuarkusProperty(Properties mavenProperties, String propertyKey) {
        String propertyValue = getQuarkusPropertyFromConfigurationFileOrNull(propertyKey);
        if(propertyValue == null) {
            if(getQuarkusPropertyFromMavenPropertiesOrNull(mavenProperties, propertyKey) != null) {
                LOGGER.warn("Please define quarkus configuration [{}] in its application.properties to allow goal caching", propertyKey);
            }
        }

        return propertyValue;
    }

    private String getQuarkusPropertyFromConfigurationFileOrNull(String propertyKey) {
        try (InputStream input = Files.newInputStream(Paths.get(QUARKUS_CONFIGURATION_FILE))) {
            Properties prop = new Properties();
            prop.load(input);
            return prop.getProperty(propertyKey);
        } catch (IOException ex) {
            LOGGER.warn("Error reading Quarkus configuration file");
            return null;
        }
    }

    private String getQuarkusPropertyFromMavenPropertiesOrNull(Properties mavenProperties, String propertyKey) {
        return mavenProperties.getProperty(propertyKey);
    }
}

