plugins {
  `java-library`
  id("org.hypertrace.publish-plugin")
}

tasks.test {
  useJUnitPlatform()
}

dependencies {
  implementation(project(":hypertrace-trace-enricher:enriched-span-constants"))
  implementation("org.hypertrace.core.datamodel:data-model:0.1.27")

  implementation("org.slf4j:slf4j-api:1.7.30")
  implementation("org.apache.commons:commons-lang3:3.12.0")
  implementation("com.google.guava:guava:31.1-jre")

  testImplementation("org.junit.jupiter:junit-jupiter:5.9.0")
  testImplementation("org.mockito:mockito-core:3.9.0")
  testImplementation("org.mockito:mockito-inline:3.9.0")
}
