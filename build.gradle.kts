import com.aureusapps.gradle.PluginUtils.loadLocalProperties
import com.aureusapps.gradle.PublishLibraryConstants.GROUP_ID
import com.aureusapps.gradle.PublishLibraryConstants.VERSION_CODE
import com.aureusapps.gradle.PublishLibraryConstants.VERSION_NAME

loadLocalProperties(project)
project.extra[GROUP_ID] = "com.aureusapps.android"
project.extra[VERSION_CODE] = 2
project.extra[VERSION_NAME] = "1.0.1"

plugins {
    alias(libs.plugins.com.android.application) apply false
    alias(libs.plugins.com.android.library) apply false
    alias(libs.plugins.org.jetbrains.kotlin.android) apply false
    alias(libs.plugins.com.aureusapps.gradle.update.version) apply true
    alias(libs.plugins.com.aureusapps.gradle.publish.library) apply true
}