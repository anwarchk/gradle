// tag::artifact-only-dependency-declaration[]
repositories {
    ivy {
        url = uri("https://ajax.googleapis.com/ajax/libs")
        patternLayout {
            artifact("[organization]/[revision]/[module](.[classifier]).[ext]")
        }
    }
}

configurations {
    create("js")
}

dependencies {
    "js"("jquery:jquery:3.2.1:min@js")
}
// end::artifact-only-dependency-declaration[]

tasks.register<Copy>("copyLibs") {
    from(configurations["js"])
    into("$buildDir/libs")
}
