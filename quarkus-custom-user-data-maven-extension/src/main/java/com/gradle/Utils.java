package com.gradle;

import org.apache.maven.execution.MavenSession;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

final class Utils {

    static Optional<String> envVariable(String name) {
        return Optional.ofNullable(System.getenv(name));
    }

    static Optional<String> projectProperty(MavenSession mavenSession, String name) {
        String value = mavenSession.getSystemProperties().getProperty(name);
        return Optional.ofNullable(value);
    }

    static Optional<String> sysProperty(String name) {
        return Optional.ofNullable(System.getProperty(name));
    }

    static Optional<Boolean> booleanSysProperty(String name) {
        return sysProperty(name).map(Boolean::parseBoolean);
    }

    static Optional<Duration> durationSysProperty(String name) {
        return sysProperty(name).map(Duration::parse);
    }

    static boolean isNotEmpty(String value) {
        return value != null && !value.isEmpty();
    }

    static String stripPrefix(String prefix, String string) {
        return string.startsWith(prefix) ? string.substring(prefix.length()) : string;
    }

    static String appendIfMissing(String str, String suffix) {
        return str.endsWith(suffix) ? str : str + suffix;
    }

    static URI appendPathAndTrailingSlash(URI baseUri, String path) {
        if (isNotEmpty(path)) {
            String normalizedBasePath = appendIfMissing(baseUri.getPath(), "/");
            String normalizedPath = appendIfMissing(stripPrefix("/", path), "/");
            return baseUri.resolve(normalizedBasePath).resolve(normalizedPath);
        }
        return baseUri;
    }

    static String urlEncode(String str) {
        try {
            return URLEncoder.encode(str, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    static String redactUserInfo(String url) {
        try {
            String userInfo = new URI(url).getUserInfo();
            return userInfo == null
                ? url
                : url.replace(userInfo + '@', "******@");
        } catch (URISyntaxException e) {
            return url;
        }
    }

    static Properties readPropertiesFile(String name) {
        try (InputStream input = new FileInputStream(name)) {
            Properties properties = new Properties();
            properties.load(input);
            return properties;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static boolean execAndCheckSuccess(String... args) {
        Runtime runtime = Runtime.getRuntime();
        Process process = null;
        try {
            process = runtime.exec(args);
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (IOException | InterruptedException ignored) {
            return false;
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }
    }

    static String execAndGetStdOut(String... args) {
        Runtime runtime = Runtime.getRuntime();
        Process process;
        try {
            process = runtime.exec(args);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (Reader standard = new BufferedReader(new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
            try (Reader error = new BufferedReader(new InputStreamReader(process.getErrorStream(), Charset.defaultCharset()))) {
                String standardText = readFully(standard);
                String ignore = readFully(error);

                boolean finished = process.waitFor(10, TimeUnit.SECONDS);
                return finished && process.exitValue() == 0 ? trimAtEnd(standardText) : null;
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            process.destroyForcibly();
        }
    }

    private static String readFully(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[1024];
        int nRead;
        while ((nRead = reader.read(buf)) != -1) {
            sb.append(buf, 0, nRead);
        }
        return sb.toString();
    }

    private static String trimAtEnd(String str) {
        return ('x' + str).trim().substring(1);
    }

    private Utils() {
    }

    public static void main(String[] args) {
        Utils config = new Utils();

        Map<String,String> map1 = new HashMap<>();
        Map<String,String> map5 = new HashMap<>();
        map5.put("foo", "bar");
        Map<String,String> map2 = new HashMap<>();
        map2.put("quarkus.titi", "1");
        map2.put("foo", "bar");
        Map<String,String> map3 = new HashMap<>();
        map3.put("quarkus.titi", "1");
        Map<String,String> map4 = new HashMap<>();
        map4.put("foo", "bar");
        map4.put("quarkus.titi", "1");
        Map<String,String> map6 = new HashMap<>();
        map6.put("quarkus.toto", "42");
        map6.put("quarkus.titi", "1");
        Map<String,String> map7 = new HashMap<>();
        map7.put("quarkus.toto", "1");
        map7.put("quarkus.titi", "42");
        Map<String,String> map8 = new HashMap<>();
        map8.put("quarkus.titi", "42");
        map8.put("quarkus.toto", "1");

        System.out.println(config.testHashQuarkusEnvironmentVariables(map1));
        System.out.println(config.testHashQuarkusEnvironmentVariables(map5));
        System.out.println(config.testHashQuarkusEnvironmentVariables(map2));
        System.out.println(config.testHashQuarkusEnvironmentVariables(map3));
        System.out.println(config.testHashQuarkusEnvironmentVariables(map4));
        System.out.println(config.testHashQuarkusEnvironmentVariables(map6));
        System.out.println(config.testHashQuarkusEnvironmentVariables(map7));
        System.out.println(config.testHashQuarkusEnvironmentVariables(map8));
    }

    private String testHashQuarkusEnvironmentVariables(Map<String,String> test) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");

            test.entrySet().stream()
                    .filter(e -> e.getKey().startsWith("quarkus."))
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> messageDigest.update((e.getKey()+e.getValue()).getBytes()));

            return Base64.getEncoder().encodeToString(messageDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

}
