plugins {
  id("org.metaborg.gradle.config.java-library")
  id("org.metaborg.spoofax.compiler.gradle.spoofaxcore.eclipse")
}

spoofaxEclipseProject {
  eclipseExternaldepsProject.set(project(":tiger.eclipse.externaldeps"))
  adapterProject.set(project(":tiger.spoofax"))
}
