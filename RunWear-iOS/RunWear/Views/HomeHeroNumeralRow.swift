import SwiftUI

// MARK: - Home hero numeral row (GEN-6, RW-67)
//
// The sheet header's hero row, extracted from WeatherOutfitView
// so the gate's render tests can compose the exact production
// row: the feels-like numeral (ink, never temperature-colored)
// + the temperature chip (filled dot + bracket label), with
// the right-justified weather figures (WIND / RAIN / HUMIDITY /
// UV in a strict two-column grid) beside the numeral, its
// block top aligned to the numeral's top. WeatherOutfitView's
// whole dependency closure is not in the test target (the
// RW-66 scope note); this row is, so its overflow behaviour at
// large Dynamic Type with three-digit and negative values is
// render-proven, not inferred from constraints.
//
// RW-67 FIX (the owner's build-26 walkthrough — the big
// number overflows on iPhone): as shipped, the row's fixed
// content exceeded the sheet width at EVERY type size
// (measured on the 358 pt sheet canvas: natural fitting width
// 415 pt at 72°F/default, 453 pt at 115°F, 471 pt at −13°F),
// so SwiftUI compressed the only compressible element — the
// numeral Text itself — which WRAPPED mid-value ("72" over
// "°F") while the figures ran off the trailing edge. And the
// numeral's @ScaledMetric(80, .largeTitle) was effectively
// UNCAPPED: the .dynamicTypeSize(...accessibility1) modifier
// sat on the Text, but the ScaledMetric was declared on the
// parent view and resolves in the PARENT's environment — at
// accessibility5 the fitting width hit 763 pt and the numeral
// shattered into one glyph per line.
//
// The fix is composition, never clipping, never truncation:
// the numeral lives in a child view (HeroNumeralText) whose
// environment the row caps at accessibility1 — the cap the
// original modifier intended — so its ScaledMetric resolves
// inside the cap; and the numeral Text is lineLimit(1) +
// minimumScaleFactor, so it SHRINKS TO FIT its slot as one
// unbroken value (three-digit and negative included), still
// the row's dominant element.

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

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            // RW-67: the accessibility1 cap is applied to the
            // whole numeral cluster (numeral + chip) from
            // OUTSIDE it, so it governs the environment the
            // numeral's @ScaledMetric resolves in — and keeps
            // the chip's eyebrow from starving the numeral's
            // slot at accessibility sizes. On the Text itself
            // (as shipped) the same modifier capped nothing:
            // the metric lived on the parent view, outside the
            // modifier's scope.
            HStack(alignment: .center, spacing: 14) {
                HeroNumeralText(text: formattedTemperature)

                HStack(spacing: 6) {
                    Circle()
                        .fill(AppTheme.temperatureColor(for: bracket))
                        .frame(width: 8, height: 8)
                    Text(bracket.displayRange)
                        .font(.appEyebrow)
                        .foregroundColor(AppTheme.inkPrimary)
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Capsule().fill(AppTheme.cardOnSheet))
                // RW-67 (render round 2): the chip keeps its
                // natural width — without priority, the stack's
                // compression split between the shrinking
                // numeral and the chip, and the chip's label
                // wrapped mid-word ("FREE/ZING", "WAR/M") at
                // accessibility1. The numeral absorbs the
                // compression alone; it has the scale factor
                // for exactly that.
                .layoutPriority(1)
            }
            .dynamicTypeSize(...DynamicTypeSize.accessibility1)

            Spacer(minLength: 0)

            heroWeatherFigures
                .padding(.top, 8)
        }
    }

    /// The two-column figures grid (labels in one fixed
    /// column, values flush right), exactly as the home header
    /// composed it before the extraction.
    private var heroWeatherFigures: some View {
        Grid(horizontalSpacing: 12, verticalSpacing: 3) {
            GridRow {
                figureLabel(AppStrings.statWindLabel)
                figureValue(AppStrings.windValue(Int(windMph.rounded())))
            }
            GridRow {
                figureLabel(AppStrings.statRainLabel)
                figureValue(AppStrings.detailPercent(rainPercent))
            }
            GridRow {
                figureLabel(AppStrings.statHumidityLabel)
                figureValue(AppStrings.detailPercent(humidityPercent))
            }
            GridRow {
                figureLabel(AppStrings.sheetUvLabel)
                figureValue(AppStrings.sheetUvValue(Int(uvIndex.rounded())))
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

/// The hero numeral itself (GEN-6, RW-67). The @ScaledMetric
/// lives in this child so the row can cap the environment it
/// resolves in; the Text shrinks to fit its slot as a single
/// line rather than wrapping or truncating the value. It stays
/// display-only: a plain Text, no gesture, no animation, no
/// content transition (the WS-2 / T-1 rule StateTruth pins).
private struct HeroNumeralText: View {
    let text: String

    @ScaledMetric(relativeTo: .largeTitle) private var sheetNumeralSize: CGFloat = 80

    var body: some View {
        Text(text)
            .font(.system(size: sheetNumeralSize, weight: .semibold))
            .monospacedDigit()
            .foregroundColor(AppTheme.inkPrimary)
            .lineLimit(1)
            .minimumScaleFactor(0.2)
    }
}
