plugins {
  id("org.metaborg.gradle.config.java-library")
}

dependencies {
  api(platform(project(":depconstraints")))

  api(project(":common"))
  api(project(":jsglr.common"))
  api(project(":stratego.common"))

  api("org.metaborg:log.api")

  compileOnly("org.checkerframework:checker-qual-android")
}
