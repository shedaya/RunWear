import SwiftUI

/// Types of weather details that can be shown. GEN-6 (RW-63):
/// the settled sheet (frames 40/41; design-spec §3.3) carries
/// SEVEN metrics — feels-like and actual join condition, wind,
/// humidity and UV, with precipitation where carried — all
/// carrying the home state's numbers unchanged.
enum WeatherDetailType {
    case feelsLike(value: String)
    case actual(value: String)
    case condition(WeatherCondition)
    case wind(speed: Double, direction: String, gusts: Double)
    case humidity(percentage: Int)
    case precipitation(percentage: Int)
    case uvIndex(value: Double)

    var title: String {
        switch self {
        case .feelsLike: return AppStrings.detailMetricFeelsLike
        case .actual: return AppStrings.detailMetricActual
        case .condition: return AppStrings.detailMetricCondition
        case .wind: return AppStrings.detailMetricWind
        case .humidity: return AppStrings.detailMetricHumidity
        case .precipitation: return AppStrings.weatherLabelPrecip
        case .uvIndex: return AppStrings.detailMetricUv
        }
    }

    var icon: String {
        switch self {
        case .feelsLike: return "thermometer.sun.fill"
        case .actual: return "thermometer.medium"
        case .condition(let condition): return condition.icon
        case .wind: return "wind"
        case .humidity: return "drop.fill"
        case .precipitation: return "cloud.rain"
        case .uvIndex: return "sun.max.fill"
        }
    }
}

/// The settled metric set the detail view tabs between, in the
/// settled order (feels-like, actual, condition, wind, humidity,
/// UV, then precipitation where carried).
enum WeatherMetricKind: String, CaseIterable, Hashable {
    case feelsLike, actual, condition, wind, humidity, uvIndex, precipitation

    var shortTitle: String {
        switch self {
        case .feelsLike: return AppStrings.weatherLabelFeelsLike
        case .actual: return AppStrings.weatherLabelActual
        case .condition: return AppStrings.weatherLabelCondition
        case .wind: return AppStrings.weatherLabelWind
        case .humidity: return AppStrings.weatherLabelHumidity
        case .precipitation: return AppStrings.detailShortRain
        case .uvIndex: return AppStrings.weatherLabelUv
        }
    }
}

extension WeatherDetailType {
    var kind: WeatherMetricKind {
        switch self {
        case .feelsLike: return .feelsLike
        case .actual: return .actual
        case .condition: return .condition
        case .wind: return .wind
        case .humidity: return .humidity
        case .precipitation: return .precipitation
        case .uvIndex: return .uvIndex
        }
    }

    /// One-line value for list rows ("Clear", "12 mph", "50%"...).
    var summaryValue: String {
        switch self {
        case .feelsLike(let value): return value
        case .actual(let value): return value
        case .condition(let condition): return condition.rawValue
        case .wind(let speed, _, _): return AppStrings.windValue(Int(round(speed)))
        case .humidity(let percentage): return AppStrings.detailPercent(percentage)
        case .precipitation(let percentage): return AppStrings.detailPercent(percentage)
        case .uvIndex(let value): return String(format: AppStrings.detailUvValue, value)
        }
    }
}

extension WeatherDetailType: Identifiable {
    /// One detail per metric kind, so the kind is a stable identity.
    /// The detail sheet is presented with `.sheet(item:)` keyed on
    /// this id — the sheet exists exactly when a detail is selected,
    /// so its content can never evaluate empty (the blank white
    /// sheet of 2026-10-29 came from the old isPresented + if-let
    /// form racing its two state writes).
    var id: WeatherMetricKind { kind }
}

struct WeatherDetailSheet: View {
    /// The metric the sheet was opened on; the live selection is
    /// state the picker and the sidebar share.
    let initialDetail: WeatherDetailType
    /// All five metrics, for the segmented picker / sidebar.
    let allDetails: [WeatherDetailType]
    /// Context lines for the split view's sidebar (location, date,
    /// temperature, bracket) — where these numbers belong.
    let contextLines: [String]
    @Binding var isPresented: Bool

    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.colorScheme) private var colorScheme
    @State private var selectedKind: WeatherMetricKind
    @State private var showMetricInspector = false

    // RW-6.3 display-numeral exception: the big values (was fixed
    // 48pt) scale with Dynamic Type relative to .largeTitle.
    @ScaledMetric(relativeTo: .largeTitle) private var bigValueSize: CGFloat = 48

    init(
        detailType: WeatherDetailType,
        allDetails: [WeatherDetailType],
        contextLines: [String],
        isPresented: Binding<Bool>
    ) {
        self.initialDetail = detailType
        self.allDetails = allDetails
        self.contextLines = contextLines
        self._isPresented = isPresented
        self._selectedKind = State(initialValue: detailType.kind)
    }

    /// Dual-mode conversion: the sheet follows the app's
    /// appearance (system + the in-app override, which arrives
    /// here through the colorScheme environment). Semantic hues
    /// resolve through AppTheme's accent pair for this scheme.
    private var isDark: Bool { colorScheme == .dark }

    /// The metric all the content below renders — the existing
    /// content builders switch on this, unchanged.
    private var detailType: WeatherDetailType {
        allDetails.first { $0.kind == selectedKind } ?? initialDetail
    }

    // Presented as a real .sheet (design record §5.2). At regular
    // width the sheet's content is a NavigationSplitView — context
    // sidebar, the selected hour's metric detail in the center,
    // and a metric inspector carrying the segmented picker. At
    // compact/medium sizes it is the picker over the same content.
    var body: some View {
        Group {
            if horizontalSizeClass == .regular {
                splitLayout
            } else {
                pickerLayout
            }
        }
        .background(AppTheme.background.ignoresSafeArea())
    }

    private var pickerLayout: some View {
        VStack(spacing: 20) {
            Picker("", selection: $selectedKind) {
                ForEach(WeatherMetricKind.allCases, id: \.self) { kind in
                    Text(kind.shortTitle).tag(kind)
                }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 24)
            .padding(.top, 16)

            ScrollView {
                VStack(spacing: 20) {
                    // GEN-6 (RW-63): the context lines render in
                    // the phone (compact) layout too — they lived
                    // only in the regular-width split sidebar, so
                    // the phone sheet carried numbers with no
                    // context line at all.
                    if !contextLines.isEmpty {
                        VStack(alignment: .leading, spacing: 4) {
                            ForEach(contextLines, id: \.self) { line in
                                Text(line)
                                    .font(.appSecondary)
                                    .monospacedDigit()
                                    .foregroundColor(AppTheme.inkSecondary)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    // Icon and title
                    headerSection

                    // Content based on type
                    detailContent

                    // Running tips
                    tipsSection

                    Spacer().frame(height: 16)
                }
                .padding(24)
            }
        }
    }

    /// List selection is optional-typed; the picker wants the
    /// plain kind. One state, two bindings.
    private var listSelection: Binding<WeatherMetricKind?> {
        Binding(
            get: { selectedKind },
            set: { kind in
                if let kind { selectedKind = kind }
            }
        )
    }

    private var splitLayout: some View {
        NavigationSplitView {
            List(selection: listSelection) {
                Section {
                    ForEach(contextLines, id: \.self) { line in
                        Text(line)
                            .font(.appSecondary)
                            .monospacedDigit()
                            .foregroundColor(AppTheme.inkSecondary)
                    }
                } header: {
                    Text(AppStrings.detailContext)
                }
                Section {
                    ForEach(allDetails, id: \.kind) { detail in
                        HStack(spacing: 10) {
                            Image(systemName: detail.icon)
                                .foregroundColor(AppTheme.inkSecondary)
                            Text(detail.title)
                                .font(.appSecondary)
                                .foregroundColor(AppTheme.inkPrimary)
                            Spacer()
                            Text(detail.summaryValue)
                                .font(.appSecondary)
                                .monospacedDigit()
                                .foregroundColor(AppTheme.inkSecondary)
                        }
                        .tag(detail.kind)
                    }
                } header: {
                    Text(AppStrings.detailMetrics)
                }
            }
            .listStyle(.sidebar)
            // GEN-5 design pass (cream map rows 24/56): the
            // sidebar's rows take the sheet tone instead of the
            // sidebar list's system material.
            .listRowBackground(AppTheme.sheet)
            .scrollContentBackground(.hidden)
            .navigationTitle(AppStrings.detailTitle)
        } detail: {
            NavigationStack {
                ScrollView {
                    VStack(spacing: 20) {
                        headerSection
                        detailContent
                        Spacer().frame(height: 16)
                    }
                    .padding(24)
                }
                .navigationTitle(detailType.title)
                .toolbar {
                    ToolbarItem(placement: .primaryAction) {
                        Button {
                            showMetricInspector.toggle()
                        } label: {
                            Label(AppStrings.detailMetricLabel, systemImage: "sidebar.right")
                        }
                    }
                }
                .inspector(isPresented: $showMetricInspector) {
                    metricInspector
                }
            }
        }
        .navigationSplitViewStyle(.prominentDetail)
    }

    /// The metric inspector: the native segmented Picker switches
    /// the metric, and the running tip for the selection sits
    /// beneath it. An inspector gets no bar of its own — it lives
    /// inside the detail's container.
    private var metricInspector: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Picker("", selection: $selectedKind) {
                    ForEach(WeatherMetricKind.allCases, id: \.self) { kind in
                        Text(kind.shortTitle).tag(kind)
                    }
                }
                .pickerStyle(.segmented)

                tipsSection
            }
            .padding(20)
        }
        .background(AppTheme.background.ignoresSafeArea())
        .navigationTitle(detailType.title)
        .inspectorColumnWidth(min: 260, ideal: 320, max: 380)
    }

    // MARK: - Header

    private var headerSection: some View {
        VStack(spacing: 12) {
            Image(systemName: detailType.icon)
                .font(.system(size: bigValueSize))
                .foregroundColor(iconColor)

            Text(detailType.title)
                .font(.appSheetTitle)
                .foregroundColor(AppTheme.inkPrimary)
        }
    }

    // MARK: - Detail Content

    @ViewBuilder
    private var detailContent: some View {
        switch detailType {
        case .feelsLike(let value):
            temperatureContent(value: value, caption: AppStrings.weatherLabelFeelsLike)
        case .actual(let value):
            temperatureContent(value: value, caption: AppStrings.detailActualTemperature)
        case .condition(let condition):
            conditionContent(condition)
        case .wind(let speed, let direction, let gusts):
            windContent(speed: speed, direction: direction, gusts: gusts)
        case .humidity(let percentage):
            humidityContent(percentage)
        case .precipitation(let percentage):
            precipitationContent(percentage)
        case .uvIndex(let value):
            uvIndexContent(value)
        }
    }

    private func temperatureContent(value: String, caption: String) -> some View {
        VStack(spacing: 8) {
            Text(value)
                .font(.system(size: bigValueSize, weight: .semibold))
                .monospacedDigit()
                .foregroundColor(AppTheme.inkPrimary)

            Text(caption)
                .font(.appSecondary)
                .foregroundColor(AppTheme.inkSecondary)
        }
    }

    private func conditionContent(_ condition: WeatherCondition) -> some View {
        VStack(spacing: 8) {
            Text(condition.rawValue)
                .font(.largeTitle.weight(.semibold))
                .foregroundColor(AppTheme.inkPrimary)

            Text(conditionDescription(condition))
                .font(.appSecondary)
                .foregroundColor(AppTheme.inkSecondary)
                .multilineTextAlignment(.center)
        }
    }

    private func windContent(speed: Double, direction: String, gusts: Double) -> some View {
        VStack(spacing: 16) {
            HStack(spacing: 24) {
                VStack(spacing: 4) {
                    Text("\(Int(round(speed)))")
                        .font(.largeTitle.weight(.semibold))
                        .monospacedDigit()
                        .foregroundColor(AppTheme.inkPrimary)
                    Text(AppStrings.detailWindUnit)
                        .font(.appFootnote)
                        .foregroundColor(AppTheme.inkSecondary)
                }

                VStack(spacing: 4) {
                    Text(direction)
                        .font(.largeTitle.weight(.semibold))
                        .foregroundColor(AppTheme.inkPrimary)
                    Text(AppStrings.detailWindDirectionWord)
                        .font(.appFootnote)
                        .foregroundColor(AppTheme.inkSecondary)
                }

                if gusts > speed {
                    VStack(spacing: 4) {
                        Text("\(Int(round(gusts)))")
                            .font(.largeTitle.weight(.semibold))
                            .monospacedDigit()
                            .foregroundColor(AppTheme.inkPrimary)
                        Text(AppStrings.detailWindGustsWord)
                            .font(.appFootnote)
                            .foregroundColor(AppTheme.inkSecondary)
                    }
                }
            }

            Text(windDescription(speed: speed))
                .font(.appSecondary)
                .foregroundColor(AppTheme.inkSecondary)
                .multilineTextAlignment(.center)
        }
    }

    private func humidityContent(_ percentage: Int) -> some View {
        VStack(spacing: 8) {
            Text(AppStrings.detailPercent(percentage))
                .font(.system(size: bigValueSize, weight: .semibold))
                .monospacedDigit()
                .foregroundColor(AppTheme.inkPrimary)

            Text(humidityDescription(percentage))
                .font(.appSecondary)
                .foregroundColor(AppTheme.inkSecondary)
                .multilineTextAlignment(.center)
        }
    }

    private func precipitationContent(_ percentage: Int) -> some View {
        VStack(spacing: 8) {
            Text(AppStrings.detailPercent(percentage))
                .font(.system(size: bigValueSize, weight: .semibold))
                .monospacedDigit()
                .foregroundColor(AppTheme.inkPrimary)

            Text(AppStrings.detailPrecipChance)
                .font(.appSecondary)
                .foregroundColor(AppTheme.inkSecondary)
        }
    }

    private func uvIndexContent(_ value: Double) -> some View {
        VStack(spacing: 8) {
            Text(String(format: AppStrings.detailUvValue, value))
                .font(.system(size: bigValueSize, weight: .semibold))
                .monospacedDigit()
                .foregroundColor(AppTheme.inkPrimary)

            Text(uvDescription(value))
                .font(.body.weight(.medium))
                .foregroundColor(uvColor(value))
        }
    }

    // MARK: - Tips Section

    private var tipsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(AppStrings.weatherRunningTip)
                .font(.appEyebrow)
                .foregroundColor(AppTheme.inkSecondary)

            Text(runningTip)
                .font(.appSecondary)
                .foregroundColor(AppTheme.inkPrimary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        // Flat content card (AppTheme tokens) — not glass: running
        // advice is dense text and stays on the flat fill.
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(AppTheme.cardFill)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(AppTheme.cardStroke, lineWidth: 1)
        )
    }

    // MARK: - Helper Properties

    /// Semantic hues resolve through AppTheme's accent pair: the
    /// dark side is the system-color rendering this sheet shipped
    /// with; the light side is the deepened pair (the UV band
    /// label below is body text and clears 4.5:1 on the cream
    /// grounds — see AppTheme's accent note).
    private var iconColor: Color {
        switch detailType {
        case .feelsLike:
            return AppTheme.weatherAccentColor(.orange, isDark: isDark)
        case .actual:
            return AppTheme.weatherAccentColor(.gray, isDark: isDark)
        case .condition(let condition):
            switch condition {
            case .clear: return AppTheme.weatherAccentColor(.yellow, isDark: isDark)
            case .partlyCloudy, .cloudy: return AppTheme.weatherAccentColor(.gray, isDark: isDark)
            case .foggy: return AppTheme.weatherAccentColor(.gray, isDark: isDark)
            case .drizzle, .rain: return AppTheme.weatherAccentColor(.blue, isDark: isDark)
            case .snow: return AppTheme.weatherAccentColor(.cyan, isDark: isDark)
            case .thunderstorm: return AppTheme.weatherAccentColor(.purple, isDark: isDark)
            case .unknown: return AppTheme.inkPrimary
            }
        case .wind: return AppTheme.weatherAccentColor(.cyan, isDark: isDark)
        case .humidity: return AppTheme.weatherAccentColor(.blue, isDark: isDark)
        case .precipitation: return AppTheme.weatherAccentColor(.indigo, isDark: isDark)
        case .uvIndex(let value): return uvColor(value)
        }
    }

    private func uvColor(_ value: Double) -> Color {
        switch value {
        case ..<3: return AppTheme.weatherAccentColor(.green, isDark: isDark)
        case 3..<6: return AppTheme.weatherAccentColor(.yellow, isDark: isDark)
        case 6..<8: return AppTheme.weatherAccentColor(.orange, isDark: isDark)
        case 8..<11: return AppTheme.weatherAccentColor(.red, isDark: isDark)
        default: return AppTheme.weatherAccentColor(.purple, isDark: isDark)
        }
    }

    private func uvDescription(_ value: Double) -> String {
        switch value {
        case ..<3: return AppStrings.uvLow
        case 3..<6: return AppStrings.uvModerate
        case 6..<8: return AppStrings.uvHigh
        case 8..<11: return AppStrings.uvVeryHigh
        default: return AppStrings.uvExtreme
        }
    }

    private func conditionDescription(_ condition: WeatherCondition) -> String {
        switch condition {
        case .clear: return AppStrings.detailConditionClearAdvice
        case .partlyCloudy: return AppStrings.detailConditionPartlyAdvice
        case .cloudy: return AppStrings.detailConditionCloudyAdvice
        case .foggy: return AppStrings.detailConditionFoggyAdvice
        case .drizzle: return AppStrings.detailConditionDrizzleAdvice
        case .rain: return AppStrings.detailConditionRainAdvice
        case .snow: return AppStrings.detailConditionSnowAdvice
        case .thunderstorm: return AppStrings.detailConditionStormAdvice
        case .unknown: return AppStrings.detailConditionUnknownAdvice
        }
    }

    private func windDescription(speed: Double) -> String {
        switch speed {
        case ..<5: return AppStrings.detailWindCalm
        case 5..<15: return AppStrings.detailWindLight
        case 15..<25: return AppStrings.detailWindModerate
        case 25..<40: return AppStrings.detailWindStrong
        default: return AppStrings.detailWindVeryStrong
        }
    }

    private func humidityDescription(_ percentage: Int) -> String {
        switch percentage {
        case ..<30: return AppStrings.detailHumidityLow
        case 30..<60: return AppStrings.detailHumidityComfortable
        case 60..<80: return AppStrings.detailHumidityModerate
        default: return AppStrings.detailHumidityHigh
        }
    }

    private var runningTip: String {
        switch detailType {
        case .feelsLike, .actual:
            // The temperature metrics carry the condition's tip —
            // the sheet has one tip line, and the outfit is priced
            // off the feels-like the home state shows.
            if case .condition(let condition)? = allDetails.first(where: { $0.kind == .condition }) {
                return Self.conditionTip(condition)
            }
            return AppStrings.tipCheckConditions
        case .condition(let condition):
            return Self.conditionTip(condition)
        case .wind(let speed, _, _):
            if speed > 20 {
                return AppStrings.tipWindStart
            } else {
                return AppStrings.tipWindLight
            }
        case .humidity(let percentage):
            if percentage > 70 {
                return AppStrings.tipHumidityHigh
            } else if percentage < 30 {
                return AppStrings.tipHumidityLow
            } else {
                return AppStrings.tipHumidityComfortable
            }
        case .precipitation(let percentage):
            if percentage > 50 {
                return AppStrings.tipRainGear
            } else {
                return AppStrings.tipRainLow
            }
        case .uvIndex(let value):
            if value >= 6 {
                return AppStrings.tipUvHigh
            } else if value >= 3 {
                return AppStrings.tipUvModerate
            } else {
                return AppStrings.tipUvLow
            }
        }
    }
}

extension WeatherDetailSheet {
    /// The condition's running tip — shared by the condition
    /// metric and (GEN-6 / RW-63) the two temperature metrics.
    static func conditionTip(_ condition: WeatherCondition) -> String {
        switch condition {
        case .clear: return AppStrings.tipConditionClear
        case .partlyCloudy: return AppStrings.tipConditionPartly
        case .cloudy: return AppStrings.tipConditionCloudy
        case .foggy: return AppStrings.tipConditionFoggy
        case .drizzle: return AppStrings.tipConditionDrizzle
        case .rain: return AppStrings.tipConditionRain
        case .snow: return AppStrings.tipConditionSnow
        case .thunderstorm: return AppStrings.tipConditionStorm
        case .unknown: return AppStrings.tipCheckConditions
        }
    }
}

// MARK: - Preview

#Preview {
    VStack {
        WeatherDetailSheet(
            detailType: .uvIndex(value: 7.5),
            allDetails: [.uvIndex(value: 7.5)],
            contextLines: [AppStrings.detailPreview],
            isPresented: .constant(true)
        )
    }
}
