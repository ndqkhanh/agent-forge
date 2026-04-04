plugins {
    `java-library`
}

// common — sealed JSON types, model records, error hierarchy
dependencies {
    api(rootProject.libs.jackson.databind)
    api(rootProject.libs.jackson.datatype.jsr310)
}
