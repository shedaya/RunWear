import Foundation

class OutfitRecommendationService {
    static let shared = OutfitRecommendationService()

    private init() {}

    func getRecommendation(for temperature: Double, condition: WeatherCondition) -> OutfitRecommendation {
        let bracket = TemperatureBracket.from(temperature: temperature)

        let (top, bottom, accessories, extras) = getClothing(for: bracket, condition: condition)

        return OutfitRecommendation(
            temperatureBracket: bracket,
            top: top,
            bottom: bottom,
            accessories: accessories,
            extras: extras
        )
    }

    private func getClothing(for bracket: TemperatureBracket, condition: WeatherCondition) -> (ClothingItem, ClothingItem, [ClothingItem], [ClothingItem]) {
        var accessories: [ClothingItem] = []
        var extras: [ClothingItem] = []

        // Add rain gear if needed
        if condition == .rain || condition == .drizzle || condition == .thunderstorm {
            extras.append(ClothingItem(
                name: "Rain Jacket",
                description: "Lightweight waterproof running jacket",
                icon: "cloud.rain.fill",
                amazonSearchTerm: "waterproof running jacket"
            ))
        }

        switch bracket {
        case .hot:
            return (
                ClothingItem(
                    name: "Tank Top",
                    description: "Lightweight, breathable singlet",
                    icon: "tshirt.fill",
                    amazonSearchTerm: "running tank top men women"
                ),
                ClothingItem(
                    name: "Split Shorts",
                    description: "Light 3-inch running shorts",
                    icon: "figure.run",
                    amazonSearchTerm: "split running shorts"
                ),
                [
                    ClothingItem(
                        name: "Sunglasses",
                        description: "UV protection running sunglasses",
                        icon: "sunglasses.fill",
                        amazonSearchTerm: "running sunglasses"
                    ),
                    ClothingItem(
                        name: "Running Cap",
                        description: "Breathable mesh cap for sun protection",
                        icon: "baseball.cap.fill",
                        amazonSearchTerm: "running cap breathable"
                    )
                ],
                extras
            )

        case .warm:
            return (
                ClothingItem(
                    name: "Short Sleeve Shirt",
                    description: "Moisture-wicking technical tee",
                    icon: "tshirt.fill",
                    amazonSearchTerm: "moisture wicking running shirt"
                ),
                ClothingItem(
                    name: "Running Shorts",
                    description: "5-inch lightweight shorts",
                    icon: "figure.run",
                    amazonSearchTerm: "running shorts 5 inch"
                ),
                [],
                extras
            )

        case .mild:
            return (
                ClothingItem(
                    name: "Long Sleeve Shirt",
                    description: "Lightweight long sleeve technical top",
                    icon: "tshirt.fill",
                    amazonSearchTerm: "long sleeve running shirt"
                ),
                ClothingItem(
                    name: "Running Shorts",
                    description: "Standard running shorts",
                    icon: "figure.run",
                    amazonSearchTerm: "running shorts"
                ),
                [
                    ClothingItem(
                        name: "Arm Sleeves",
                        description: "Optional arm warmers",
                        icon: "hand.raised.fill",
                        amazonSearchTerm: "running arm sleeves"
                    )
                ],
                extras
            )

        case .cool:
            return (
                ClothingItem(
                    name: "Long Sleeve Shirt",
                    description: "Midweight technical long sleeve",
                    icon: "tshirt.fill",
                    amazonSearchTerm: "thermal running shirt"
                ),
                ClothingItem(
                    name: "Running Tights",
                    description: "Lightweight running tights or capris",
                    icon: "figure.run",
                    amazonSearchTerm: "running tights"
                ),
                [
                    ClothingItem(
                        name: "Light Gloves",
                        description: "Thin touchscreen-compatible gloves",
                        icon: "hand.raised.fill",
                        amazonSearchTerm: "lightweight running gloves"
                    )
                ],
                extras
            )

        case .cold:
            accessories = [
                ClothingItem(
                    name: "Running Beanie",
                    description: "Thermal moisture-wicking beanie",
                    icon: "brain.head.profile",
                    amazonSearchTerm: "running beanie thermal"
                ),
                ClothingItem(
                    name: "Running Gloves",
                    description: "Insulated running gloves",
                    icon: "hand.raised.fill",
                    amazonSearchTerm: "insulated running gloves"
                )
            ]
            return (
                ClothingItem(
                    name: "Base Layer + Jacket",
                    description: "Thermal base layer with wind-resistant jacket",
                    icon: "tshirt.fill",
                    amazonSearchTerm: "running base layer thermal"
                ),
                ClothingItem(
                    name: "Thermal Tights",
                    description: "Fleece-lined running tights",
                    icon: "figure.run",
                    amazonSearchTerm: "fleece lined running tights"
                ),
                accessories,
                extras
            )

        case .veryCold:
            accessories = [
                ClothingItem(
                    name: "Balaclava",
                    description: "Face-covering thermal balaclava",
                    icon: "face.dashed",
                    amazonSearchTerm: "running balaclava cold weather"
                ),
                ClothingItem(
                    name: "Heavy Gloves",
                    description: "Heavily insulated mittens or gloves",
                    icon: "hand.raised.fill",
                    amazonSearchTerm: "winter running mittens"
                ),
                ClothingItem(
                    name: "Neck Gaiter",
                    description: "Fleece neck warmer",
                    icon: "scribble.variable",
                    amazonSearchTerm: "fleece neck gaiter running"
                )
            ]
            return (
                ClothingItem(
                    name: "Double Layer Top",
                    description: "Thermal base + insulated mid layer",
                    icon: "tshirt.fill",
                    amazonSearchTerm: "winter running jacket insulated"
                ),
                ClothingItem(
                    name: "Insulated Tights",
                    description: "Heavy thermal running pants",
                    icon: "figure.run",
                    amazonSearchTerm: "insulated running pants winter"
                ),
                accessories,
                extras
            )

        case .extreme:
            accessories = [
                ClothingItem(
                    name: "Full Balaclava",
                    description: "Complete face and neck coverage",
                    icon: "face.dashed",
                    amazonSearchTerm: "extreme cold balaclava"
                ),
                ClothingItem(
                    name: "Insulated Mittens",
                    description: "Extreme cold weather mittens",
                    icon: "hand.raised.fill",
                    amazonSearchTerm: "extreme cold running mittens"
                ),
                ClothingItem(
                    name: "Neck Gaiter",
                    description: "Heavy fleece neck warmer",
                    icon: "scribble.variable",
                    amazonSearchTerm: "heavy fleece neck gaiter"
                )
            ]
            extras.append(ClothingItem(
                name: "Hand Warmers",
                description: "Disposable chemical hand warmers",
                icon: "flame.fill",
                amazonSearchTerm: "hand warmers running"
            ))
            return (
                ClothingItem(
                    name: "Full Thermal System",
                    description: "Base layer + mid layer + windproof shell",
                    icon: "tshirt.fill",
                    amazonSearchTerm: "extreme cold running gear layering"
                ),
                ClothingItem(
                    name: "Wind-Proof Pants",
                    description: "Thermal tights + wind pants",
                    icon: "figure.run",
                    amazonSearchTerm: "windproof running pants winter"
                ),
                accessories,
                extras
            )
        }
    }
}
