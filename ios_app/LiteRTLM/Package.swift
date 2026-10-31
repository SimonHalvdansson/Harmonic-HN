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
            url: "https://github.com/google-ai-edge/LiteRT-LM/releases/download/v0.19.0/CLiteRTLM.xcframework.zip",
            checksum: "b7b2450fc084058adc5185498f8b7810a9297e054a10cf55818b62e1d5f8d65d"
        ),
    ]
)
