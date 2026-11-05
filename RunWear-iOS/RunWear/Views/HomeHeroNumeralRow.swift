import SwiftUI

// MARK: - Home hero numeral row (GEN-6, RW-67)
//
// The sheet header's hero row, extracted from WeatherOutfitView
// — composition unchanged — so the gate's render tests can
// compose the exact production row: the feels-like numeral
// (ink, never temperature-colored) + the temperature chip
// (filled dot + bracket label), with the right-justified
// weather figures (WIND / RAIN / HUMIDITY / UV in a strict
// two-column grid) beside the numeral, its block top aligned
// to the numeral's top. WeatherOutfitView's whole dependency
// closure is not in the test target (the RW-66 scope note);
// this row is, so its overflow behaviour at large Dynamic Type
// with three-digit and negative values is render-proven, not
// inferred from constraints.

struct HomeHeroNumeralRow: View {
    /// The feels-like figure, formatted in the user's unit
    /// (e.g. "72°F", "115°F", "-13°F").
    let formattedTemperature: String
    /// The temperature bracket the chip names and colors.
    let bracket: HeroTempBracket
    let windMph: Double
    let rainPercent: Int
    let humidityPercent: Int
    let uvIndex: Double

    @ScaledMetric(relativeTo: .largeTitle) private var sheetNumeralSize: CGFloat = 80

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            HStack(alignment: .center, spacing: 14) {
                Text(formattedTemperature)
                    .font(.system(size: sheetNumeralSize, weight: .semibold))
                    .monospacedDigit()
                    .foregroundColor(AppTheme.inkPrimary)
                    .dynamicTypeSize(...DynamicTypeSize.accessibility1)

                HStack(spacing: 6) {
                    Circle()
                        .fill(AppTheme.temperatureColor(for: bracket))
                        .frame(width: 8, height: 8)
                    Text(bracket.rawValue)
                        .font(.appEyebrow)
                        .foregroundColor(AppTheme.inkPrimary)
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Capsule().fill(AppTheme.cardOnSheet))
            }

            Spacer(minLength: 0)

            weatherFigures
                .padding(.top, 8)
        }
    }

    /// The two-column figures grid (labels in one fixed
    /// column, values flush right), exactly as the home header
    /// composed it before the extraction.
    private var weatherFigures: some View {
        Grid(horizontalSpacing: 12, verticalSpacing: 3) {
            GridRow {
                figureLabel("WIND")
                figureValue("\(Int(windMph.rounded())) mph")
            }
            GridRow {
                figureLabel("RAIN")
                figureValue("\(rainPercent)%")
            }
            GridRow {
                figureLabel("HUMIDITY")
                figureValue("\(humidityPercent)%")
            }
            GridRow {
                figureLabel("UV")
                figureValue("\(Int(uvIndex.rounded())) of 11")
            }
        }
    }

    private func figureLabel(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 10, weight: .bold))
            .tracking(0.7)
            .foregroundColor(AppTheme.inkMuted)
            .gridColumnAlignment(.leading)
    }

    private func figureValue(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 13.5, weight: .semibold))
            .monospacedDigit()
            .foregroundColor(AppTheme.inkPrimary)
            .gridColumnAlignment(.trailing)
    }
}
