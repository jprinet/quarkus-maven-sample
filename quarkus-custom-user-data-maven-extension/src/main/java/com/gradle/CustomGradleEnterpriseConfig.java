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

    private final QuarkusCachingConfig quarkusCachingConfig = new QuarkusCachingConfig();

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
        quarkusCachingConfig.configureQuarkusPluginCache(buildCache);
    }

}

