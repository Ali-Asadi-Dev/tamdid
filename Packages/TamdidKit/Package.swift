// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "TamdidKit",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "TamdidCore", targets: ["TamdidCore"]),
        .library(name: "TamdidData", targets: ["TamdidData"]),
    ],
    targets: [
        // Pure business rules: no UI, no I/O. Builds and tests on Linux too.
        .target(name: "TamdidCore"),
        // Persistence and backup on top of the core models.
        .target(name: "TamdidData", dependencies: ["TamdidCore"]),
        .testTarget(name: "TamdidCoreTests", dependencies: ["TamdidCore"]),
        .testTarget(name: "TamdidDataTests", dependencies: ["TamdidData"]),
    ]
)
