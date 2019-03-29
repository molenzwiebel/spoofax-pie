plugins {
  id("org.metaborg.gradle.config.java-library")
  id("net.ltgt.apt") version "0.21"
  id("net.ltgt.apt-idea") version "0.21"
}

val spoofaxVersion = extra["spoofaxVersion"] as String
val pieVersion = extra["pieVersion"] as String

dependencies {
  api(project(":tiger"))
  api(project(":spoofax.core"))
  api("org.metaborg:pie.api:$pieVersion")
  api("com.google.dagger:dagger:2.21")
  annotationProcessor("com.google.dagger:dagger-compiler:2.21")
  compileOnly("org.checkerframework:checker-qual-android:2.6.0") // Use android version: annotation retention policy is class instead of runtime.
}
