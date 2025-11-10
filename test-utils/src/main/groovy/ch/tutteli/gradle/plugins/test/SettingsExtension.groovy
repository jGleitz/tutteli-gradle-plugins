package ch.tutteli.gradle.plugins.test

import org.junit.jupiter.api.extension.*

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

import static java.nio.file.Files.createDirectories

class SettingsExtensionObject {
    public final Path tmpPath
    public final File tmp
    public final File settings
    public final File buildGradle
    public final File gpgKeyRing

    public final List<String> pluginClasspath

    SettingsExtensionObject(Path tmpPath) {
        this.tmpPath = tmpPath
        tmp = tmpPath.toFile()
        settings = new File(tmp, 'settings.gradle')
        buildGradle = new File(tmp, 'build.gradle')
        gpgKeyRing = new File(tmp, 'keyring.gpg')

        URL pluginClasspathResource = getClass().classLoader.getResource('plugin-classpath.txt')
        if (pluginClasspathResource == null) {
            throw new IllegalStateException('Did not find plugin classpath resource, run `createClasspathManifest` build task.')
        }

        pluginClasspath = pluginClasspathResource.readLines()
            .collect { it.replace('\\', '\\\\') }
            .collect { "\'${it}\'" }
    }

    String buildscriptWithKotlin(String kotlinVersion) {
        return """
        import org.gradle.api.tasks.testing.logging.TestLogEvent
        buildscript {
            repositories {
                gradlePluginPortal()
            }
            dependencies {
                classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion'
                classpath files($pluginClasspath)
            }
        }
        """
    }


    static String configureTestLogging() {
        return """
            tasks.withType(Test) {
                testLogging {
                    events TestLogEvent.FAILED,
                        TestLogEvent.PASSED,
                        TestLogEvent.SKIPPED,
                        TestLogEvent.STANDARD_OUT
                }
            }
            """
    }
}

class SettingsExtension implements ParameterResolver {

    @Override
    boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == SettingsExtensionObject.class
    }

    @Override
    Object resolveParameter(ParameterContext parameterContext, ExtensionContext context) {
        return getStore(context).getOrComputeIfAbsent("settingsSetup") {
            def targetDir = Path.of("./build/test-output/" + parameterContext.declaringExecutable.name)
            deleteRecursively(targetDir)
            createDirectories(targetDir)
            println("test folder: " + targetDir)
            new SettingsExtensionObject(targetDir)
        }
    }

    private ExtensionContext.Store getStore(ExtensionContext context) {
        context.getStore(ExtensionContext.Namespace.create(this.class))
    }

    static void deleteRecursively(Path tmpDir) {
        try {
            Files.walkFileTree(tmpDir, new SimpleFileVisitor<Path>() {

                @Override
                FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    return deleteAndContinue(file)
                }

                @Override
                FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    return deleteAndContinue(dir)
                }

                private FileVisitResult deleteAndContinue(Path path) throws IOException {
                    Files.delete(path)
                    return FileVisitResult.CONTINUE
                }
            })
        } catch(NoSuchFileException ignored) {
            // okay
        }
    }

}
