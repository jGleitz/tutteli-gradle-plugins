package ch.tutteli.gradle.publish

import com.jfrog.bintray.gradle.BintrayExtension
import org.apache.maven.model.Developer
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.jvm.tasks.Jar

import static Validation.requireNotNullNorBlank

class PublishPluginExtension {
    private static final String DEFAULT_DISTRIBUTION = 'repo'
    private Project project
    private BintrayExtension bintrayExtension

    final Property<String> githubUser
    final Property<SoftwareComponent> component
    final SetProperty<Task> artifacts
    final ListProperty<License> licenses
    final ListProperty<Developer> developers
    final Property<String> propNameBintrayUser
    final Property<String> propNameBintrayApiKey
    final Property<String> propNameBintrayGpgPassphrase
    final Property<String> envNameBintrayUser
    final Property<String> envNameBintrayApiKey
    final Property<String> envNameBintrayGpgPassphrase
    final Property<String> bintrayRepo
    final Property<String> bintrayPkg
    final Property<String> bintrayOrganisation
    final Property<Boolean> signWithGpg
    final Property<String> manifestVendor

    PublishPluginExtension(Project project) {
        this.project = project
        bintrayExtension = project.extensions.getByType(BintrayExtension)
        githubUser = project.objects.property(String)
        component = project.objects.property(SoftwareComponent)
        artifacts = project.objects.setProperty(Task)
        licenses = project.objects.listProperty(License)
        resetLicenses(StandardLicenses.APACHE_2_0, 'repo')
        developers = project.objects.listProperty(Developer)
        propNameBintrayUser = project.objects.property(String)
        propNameBintrayUser.set('bintrayUser')
        propNameBintrayApiKey = project.objects.property(String)
        propNameBintrayApiKey.set('bintrayApiKey')
        propNameBintrayGpgPassphrase = project.objects.property(String)
        propNameBintrayGpgPassphrase.set('bintrayGpgPassphrase')
        envNameBintrayUser = project.objects.property(String)
        envNameBintrayUser.set('BINTRAY_USER')
        envNameBintrayApiKey = project.objects.property(String)
        envNameBintrayApiKey.set('BINTRAY_API_KEY')
        envNameBintrayGpgPassphrase = project.objects.property(String)
        envNameBintrayGpgPassphrase.set('BINTRAY_GPG_PASSPHRASE')
        bintrayRepo = project.objects.property(String)
        bintrayPkg = project.objects.property(String)
        bintrayOrganisation = project.objects.property(String)
        signWithGpg = project.objects.property(Boolean)
        signWithGpg.set(true)
        manifestVendor = project.objects.property(String)

        if (isTutteliProject(project) || isTutteliProject(project.rootProject)) {
            githubUser.set('robstoll')
            bintrayRepo.set('tutteli-jars')
            manifestVendor.set('tutteli.ch')
        }

        // reset group of sub-projects
        if (project.rootProject != project && project.group == project.rootProject.name) {
            project.group = ""
        }

        useJavaComponentIfJavaPluginAvailable()
        addAllJarsToArtifacts()
    }

    private static boolean isTutteliProject(Project project) {
        return project.group?.startsWith("ch.tutteli")
    }

    private void useJavaComponentIfJavaPluginAvailable() {
        def component = project.components.findByName('java')
        if (component != null) {
            this.component.set(component)
        }
    }

    private void addAllJarsToArtifacts() {
        project.tasks.withType(Jar).each {
            artifacts.add(it)
        }
    }

    /**
     * Resets all previously set licenses and adds the given - can be used to override the default license.
     * Use {@link #license(java.lang.String)} to specify additional licenses.
     */
    void resetLicenses(String license) {
        resetLicenses(license, DEFAULT_DISTRIBUTION)
    }

    /**
     * Resets all previously set licenses and adds the given - can be used to override the default license.
     * Use {@link #license(java.lang.String, java.lang.String)} to specify additional licenses.
     */
    void resetLicenses(String license, String distribution) {
        resetLicenses(StandardLicenses.fromShortName(license), distribution)
    }

    /**
     * Resets all previously set licenses and adds the given - can be used to override the default license.
     * Use {@link #license(StandardLicenses)} to specify additional licenses.
     */
    void resetLicenses(StandardLicenses standardLicense) {
        resetLicenses(standardLicense, DEFAULT_DISTRIBUTION)
    }

    /**
     * Resets all previously set licenses and adds the given - can be used to override the default license.
     * Use {@link #license(StandardLicenses, java.lang.String)} to specify additional licenses.
     */
    void resetLicenses(StandardLicenses standardLicense, String distribution) {
        setNewLicense(new LicenseImpl(standardLicense, distribution))
    }

    /**
     * Resets all previously set licenses and adds the given - can be used to override the default license.
     * Use {@link #license(org.gradle.api.Action)} to specify additional licenses.
     */
    void resetLicenses(Action<License> license) {
        setNewLicense(applyClosureToNewLicense(license))
    }

    private setNewLicense(License newLicense) {
        def licenses = new ArrayList<License>(5)
        licenses.add(newLicense)
        this.licenses.set(licenses)
    }

    void license(String additionalLicense) {
        license(additionalLicense, DEFAULT_DISTRIBUTION)
    }

    void license(String additionalLicense, String distribution) {
        license(StandardLicenses.fromShortName(additionalLicense), distribution)
    }

    void license(StandardLicenses standardLicense) {
        license(standardLicense, DEFAULT_DISTRIBUTION)
    }

    void license(StandardLicenses standardLicense, String distribution) {
        addNewLicense(new LicenseImpl(standardLicense, distribution))
    }

    void license(Action<License> license) {
        addNewLicense(applyClosureToNewLicense(license))
    }

    private boolean addNewLicense(License license) {
        licenses.add(license)
    }

    private License applyClosureToNewLicense(Action<License> license) {
        def newLicense = project.objects.newInstance(LicenseImpl as Class<License>)
        newLicense.distribution = 'repo'
        license.execute(newLicense)
        requireNotNullNorBlank(newLicense.shortName, "${PublishPlugin.EXTENSION_NAME}.license.shortName")
        requireNotNullNorBlank(newLicense.longName, "${PublishPlugin.EXTENSION_NAME}.license.longName")
        requireNotNullNorBlank(newLicense.url, "${PublishPlugin.EXTENSION_NAME}.license.url")
        requireNotNullNorBlank(newLicense.distribution, "${PublishPlugin.EXTENSION_NAME}.license.distribution")
        newLicense
    }

    void developer(Action<Developer> developer) {
        def newDeveloper = project.objects.newInstance(Developer)
        developer.execute(newDeveloper)
        developers.add(newDeveloper)
    }

    void bintray(Action<BintrayExtension> bintray) {
        bintray.execute(bintrayExtension)
    }
}
