// swift-tools-version: 5.9
import PackageDescription

// Google's official iOS runtime, without cloning the multi-platform binary history.
// The checksum matches the upstream Swift package's native dependency.
let package = Package(
    name: "LiteRTLM",
    platforms: [.iOS(.v15)],
    products: [.library(name: "LiteRTLM", targets: ["CLiteRTLM"])],
    targets: [
        .binaryTarget(
            name: "CLiteRTLM",
            url: "https://github.com/google-ai-edge/LiteRT-LM/releases/download/v0.17.1/CLiteRTLM.xcframework.zip",
            checksum: "c94fc12aa0403cb47208e419cc3bfe258214ea17035f7a63c16de536869f2186"
        ),
    ]
)
