import com.aureusapps.gradle.PluginUtils.loadLocalProperties
import com.aureusapps.gradle.PublishLibraryConstants.GROUP_ID

loadLocalProperties(project)
project.extra[GROUP_ID] = "com.aureusapps.android"

plugins {
    alias(libs.plugins.com.android.application) apply false
    alias(libs.plugins.com.android.library) apply false
    alias(libs.plugins.org.jetbrains.kotlin.android) apply false
    alias(libs.plugins.com.aureusapps.gradle.update.version) apply true
    alias(libs.plugins.com.aureusapps.gradle.publish.library) apply true
}