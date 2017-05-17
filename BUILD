load("//tools/bzl:plugin.bzl", "gerrit_plugin")

gerrit_plugin(
    name = "automerge-plugin",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: automerge-plugin",
        "Gerrit-Module: com.criteo.gerrit.plugins.automerge.AutomergeModule",
    ],
    resources = glob(["src/main/resources/*"]),
)
