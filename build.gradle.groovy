if (project == rootProject) {
    task {
        plugins {
            id("com.gradle.build-scan")
        }

        buildScan {
            termsOfServiceUrl = "https://gradle.com/terms-of-service"
            termsOfServiceAgree = "yes"
        }
    }
}