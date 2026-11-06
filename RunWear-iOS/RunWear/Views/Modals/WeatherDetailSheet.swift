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
        case .feelsLike: return "Feels Like"
        case .actual: return "Actual"
        case .condition: return "Weather Condition"
        case .wind: return "Wind"
        case .humidity: return "Humidity"
        case .precipitation: return "Precipitation"
        case .uvIndex: return "UV Index"
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
        case .feelsLike: return "Feels like"
        case .actual: return "Actual"
        case .condition: return "Condition"
        case .wind: return "Wind"
        case .humidity: return "Humidity"
        case .precipitation: return "Rain"
        case .uvIndex: return "UV"
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
        case .wind(let speed, _, _): return "\(Int(round(speed))) mph"
        case .humidity(let percentage): return "\(percentage)%"
        case .precipitation(let percentage): return "\(percentage)%"
        case .uvIndex(let value): return String(format: "%.1f", value)
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
                    Text("Context")
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
                    Text("Metrics")
                }
            }
            .listStyle(.sidebar)
            // GEN-5 design pass (cream map rows 24/56): the
            // sidebar's rows take the sheet tone instead of the
            // sidebar list's system material.
            .listRowBackground(AppTheme.sheet)
            .scrollContentBackground(.hidden)
            .navigationTitle("Weather Detail")
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
                            Label("Metric", systemImage: "sidebar.right")
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
            temperatureContent(value: value, caption: "Feels like")
        case .actual(let value):
            temperatureContent(value: value, caption: "Actual temperature")
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
                    Text("mph")
                        .font(.appFootnote)
                        .foregroundColor(AppTheme.inkSecondary)
                }

                VStack(spacing: 4) {
                    Text(direction)
                        .font(.largeTitle.weight(.semibold))
                        .foregroundColor(AppTheme.inkPrimary)
                    Text("direction")
                        .font(.appFootnote)
                        .foregroundColor(AppTheme.inkSecondary)
                }

                if gusts > speed {
                    VStack(spacing: 4) {
                        Text("\(Int(round(gusts)))")
                            .font(.largeTitle.weight(.semibold))
                            .monospacedDigit()
                            .foregroundColor(AppTheme.inkPrimary)
                        Text("gusts")
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
            Text("\(percentage)%")
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
            Text("\(percentage)%")
                .font(.system(size: bigValueSize, weight: .semibold))
                .monospacedDigit()
                .foregroundColor(AppTheme.inkPrimary)

            Text("Chance of precipitation")
                .font(.appSecondary)
                .foregroundColor(AppTheme.inkSecondary)
        }
    }

    private func uvIndexContent(_ value: Double) -> some View {
        VStack(spacing: 8) {
            Text(String(format: "%.1f", value))
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
            Text("Running Tip")
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
        case ..<3: return "Low"
        case 3..<6: return "Moderate"
        case 6..<8: return "High"
        case 8..<11: return "Very High"
        default: return "Extreme"
        }
    }

    private func conditionDescription(_ condition: WeatherCondition) -> String {
        switch condition {
        case .clear: return "Perfect conditions for running"
        case .partlyCloudy: return "Some cloud cover, comfortable running"
        case .cloudy: return "Overcast skies, cooler temperatures"
        case .foggy: return "Limited visibility, stay alert"
        case .drizzle: return "Light rain, consider a waterproof layer"
        case .rain: return "Wet conditions, waterproof gear recommended"
        case .snow: return "Snowy conditions, watch your footing"
        case .thunderstorm: return "Dangerous conditions, consider staying indoors"
        case .unknown: return "Check local conditions"
        }
    }

    private func windDescription(speed: Double) -> String {
        switch speed {
        case ..<5: return "Calm conditions"
        case 5..<15: return "Light breeze"
        case 15..<25: return "Moderate wind, may affect pace"
        case 25..<40: return "Strong wind, challenging running"
        default: return "Very strong wind, use caution"
        }
    }

    private func humidityDescription(_ percentage: Int) -> String {
        switch percentage {
        case ..<30: return "Low humidity, stay hydrated"
        case 30..<60: return "Comfortable humidity level"
        case 60..<80: return "Moderate humidity, pace yourself"
        default: return "High humidity, take it easy"
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
            return "Check conditions before heading out."
        case .condition(let condition):
            return Self.conditionTip(condition)
        case .wind(let speed, _, _):
            if speed > 20 {
                return "Start your run heading into the wind so you have it at your back when tired."
            } else {
                return "Light wind can help cool you down during your run."
            }
        case .humidity(let percentage):
            if percentage > 70 {
                return "High humidity makes it harder to cool down. Slow your pace and hydrate frequently."
            } else if percentage < 30 {
                return "Low humidity can be deceptively dehydrating. Drink before you feel thirsty."
            } else {
                return "Comfortable humidity for running. Stay hydrated as usual."
            }
        case .precipitation(let percentage):
            if percentage > 50 {
                return "Consider waterproof gear and protect electronics in a ziplock bag."
            } else {
                return "Low chance of rain, but a light layer won't hurt."
            }
        case .uvIndex(let value):
            if value >= 6 {
                return "High UV - wear sunscreen SPF 30+, sunglasses, and a hat."
            } else if value >= 3 {
                return "Moderate UV - sunscreen recommended for longer runs."
            } else {
                return "Low UV exposure - enjoy your run!"
            }
        }
    }
}

extension WeatherDetailSheet {
    /// The condition's running tip — shared by the condition
    /// metric and (GEN-6 / RW-63) the two temperature metrics.
    static func conditionTip(_ condition: WeatherCondition) -> String {
        switch condition {
        case .clear: return "Great day for a run! Don't forget sunscreen if it's sunny."
        case .partlyCloudy: return "Enjoy the cloud cover - it can help keep you cool."
        case .cloudy: return "Overcast days can be great for longer runs."
        case .foggy: return "Wear bright, reflective clothing for visibility."
        case .drizzle: return "Light rain can be refreshing. A cap helps keep rain off your face."
        case .rain: return "Wear moisture-wicking fabrics and protect your phone."
        case .snow: return "Choose shoes with good traction and shorten your stride."
        case .thunderstorm: return "Safety first - consider an indoor workout."
        case .unknown: return "Check conditions before heading out."
        }
    }
}

// MARK: - Preview

#Preview {
    VStack {
        WeatherDetailSheet(
            detailType: .uvIndex(value: 7.5),
            allDetails: [.uvIndex(value: 7.5)],
            contextLines: ["Preview"],
            isPresented: .constant(true)
        )
    }
}
