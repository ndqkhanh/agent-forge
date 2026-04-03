// gateway — HTTP server with SSE streaming, CLI REPL
dependencies {
    implementation(project(":common"))
    implementation(project(":api-client"))
    implementation(project(":runtime"))
    implementation(project(":tools"))
    implementation(project(":hooks"))
    implementation(project(":mcp"))
    implementation(project(":config"))
}
