plugins {
  java
  application
  jacoco
  id("org.hypertrace.docker-java-application-plugin")
  id("org.hypertrace.docker-publish-plugin")
  id("org.hypertrace.jacoco-report-plugin")
}

application {
  mainClass.set("org.hypertrace.core.serviceframework.PlatformServiceLauncher")
}

hypertraceDocker {
  defaultImage {
    javaApplication {
      serviceName.set("${project.name}")
      adminPort.set(8099)
    }
  }
}

tasks.test {
  useJUnitPlatform()
}

dependencies {
  // common and framework
  implementation("org.hypertrace.core.serviceframework:platform-service-framework:0.1.49")
  implementation("org.hypertrace.core.serviceframework:platform-metrics:0.1.49")

  // open telemetry
  implementation("io.opentelemetry:opentelemetry-sdk-metrics:1.7.0-alpha")
  // TODO: Upgrade opentelemetry-exporter-prometheus to 1.8.0 release when available
  // to include time stamp related changes
  // https://github.com/open-telemetry/opentelemetry-java/pull/3700
  // For now, the exported time stamp will be the current time stamp.
  implementation("io.opentelemetry:opentelemetry-exporter-prometheus:1.7.0-alpha")
  implementation("com.google.protobuf:protobuf-java:3.22.0")

  // open telemetry proto
  implementation("io.opentelemetry:opentelemetry-proto:1.6.0-alpha")

  // kafka
  implementation("org.apache.kafka:kafka-clients:7.2.1-ccs")

  // test
  testImplementation("org.junit.jupiter:junit-jupiter:5.9.0")
  testImplementation("org.mockito:mockito-core:4.7.0")
  testImplementation("com.google.code.gson:gson:2.8.9")
}
