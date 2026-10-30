import SwiftUI

/// Types of weather details that can be shown
enum WeatherDetailType {
    case condition(WeatherCondition)
    case wind(speed: Double, direction: String, gusts: Double)
    case humidity(percentage: Int)
    case precipitation(percentage: Int)
    case uvIndex(value: Double)

    var title: String {
        switch self {
        case .condition: return "Weather Condition"
        case .wind: return "Wind"
        case .humidity: return "Humidity"
        case .precipitation: return "Precipitation"
        case .uvIndex: return "UV Index"
        }
    }

    var icon: String {
        switch self {
        case .condition(let condition): return condition.icon
        case .wind: return "wind"
        case .humidity: return "drop.fill"
        case .precipitation: return "cloud.rain"
        case .uvIndex: return "sun.max.fill"
        }
    }
}

/// The five weather metrics the detail view tabs between.
enum WeatherMetricKind: String, CaseIterable, Hashable {
    case condition, wind, humidity, precipitation, uvIndex

    var shortTitle: String {
        switch self {
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
    @State private var selectedKind: WeatherMetricKind
    @State private var showMetricInspector = false

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
        .background(AppTheme.darkBackground.ignoresSafeArea())
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
                            .font(.system(size: 14))
                            .foregroundColor(.white.opacity(0.8))
                    }
                } header: {
                    Text("Context")
                }
                Section {
                    ForEach(allDetails, id: \.kind) { detail in
                        HStack(spacing: 10) {
                            Image(systemName: detail.icon)
                                .foregroundColor(.white.opacity(0.7))
                            Text(detail.title)
                                .font(.system(size: 15))
                                .foregroundColor(.white)
                            Spacer()
                            Text(detail.summaryValue)
                                .font(.system(size: 14))
                                .foregroundColor(.white.opacity(0.6))
                        }
                        .tag(detail.kind)
                    }
                } header: {
                    Text("Metrics")
                }
            }
            .listStyle(.sidebar)
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
        .background(AppTheme.darkBackground.ignoresSafeArea())
        .navigationTitle(detailType.title)
        .inspectorColumnWidth(min: 260, ideal: 320, max: 380)
    }

    // MARK: - Header

    private var headerSection: some View {
        VStack(spacing: 12) {
            Image(systemName: detailType.icon)
                .font(.system(size: 48))
                .foregroundColor(iconColor)

            Text(detailType.title)
                .font(.system(size: 24, weight: .bold))
                .foregroundColor(.white)
        }
    }

    // MARK: - Detail Content

    @ViewBuilder
    private var detailContent: some View {
        switch detailType {
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

    private func conditionContent(_ condition: WeatherCondition) -> some View {
        VStack(spacing: 8) {
            Text(condition.rawValue)
                .font(.system(size: 36, weight: .semibold))
                .foregroundColor(.white)

            Text(conditionDescription(condition))
                .font(.system(size: 15))
                .foregroundColor(.white.opacity(0.7))
                .multilineTextAlignment(.center)
        }
    }

    private func windContent(speed: Double, direction: String, gusts: Double) -> some View {
        VStack(spacing: 16) {
            HStack(spacing: 24) {
                VStack(spacing: 4) {
                    Text("\(Int(round(speed)))")
                        .font(.system(size: 36, weight: .semibold))
                        .foregroundColor(.white)
                    Text("mph")
                        .font(.system(size: 14))
                        .foregroundColor(.white.opacity(0.7))
                }

                VStack(spacing: 4) {
                    Text(direction)
                        .font(.system(size: 36, weight: .semibold))
                        .foregroundColor(.white)
                    Text("direction")
                        .font(.system(size: 14))
                        .foregroundColor(.white.opacity(0.7))
                }

                if gusts > speed {
                    VStack(spacing: 4) {
                        Text("\(Int(round(gusts)))")
                            .font(.system(size: 36, weight: .semibold))
                            .foregroundColor(.white)
                        Text("gusts")
                            .font(.system(size: 14))
                            .foregroundColor(.white.opacity(0.7))
                    }
                }
            }

            Text(windDescription(speed: speed))
                .font(.system(size: 15))
                .foregroundColor(.white.opacity(0.7))
                .multilineTextAlignment(.center)
        }
    }

    private func humidityContent(_ percentage: Int) -> some View {
        VStack(spacing: 8) {
            Text("\(percentage)%")
                .font(.system(size: 48, weight: .semibold))
                .foregroundColor(.white)

            Text(humidityDescription(percentage))
                .font(.system(size: 15))
                .foregroundColor(.white.opacity(0.7))
                .multilineTextAlignment(.center)
        }
    }

    private func precipitationContent(_ percentage: Int) -> some View {
        VStack(spacing: 8) {
            Text("\(percentage)%")
                .font(.system(size: 48, weight: .semibold))
                .foregroundColor(.white)

            Text("Chance of precipitation")
                .font(.system(size: 15))
                .foregroundColor(.white.opacity(0.7))
        }
    }

    private func uvIndexContent(_ value: Double) -> some View {
        VStack(spacing: 8) {
            Text(String(format: "%.1f", value))
                .font(.system(size: 48, weight: .semibold))
                .foregroundColor(.white)

            Text(uvDescription(value))
                .font(.system(size: 17, weight: .medium))
                .foregroundColor(uvColor(value))
        }
    }

    // MARK: - Tips Section

    private var tipsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Running Tip")
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(.white.opacity(0.5))

            Text(runningTip)
                .font(.system(size: 15))
                .foregroundColor(.white.opacity(0.9))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.white.opacity(0.1))
        )
    }

    // MARK: - Helper Properties

    private var iconColor: Color {
        switch detailType {
        case .condition(let condition):
            switch condition {
            case .clear: return .yellow
            case .partlyCloudy, .cloudy: return .gray
            case .foggy: return .gray
            case .drizzle, .rain: return .blue
            case .snow: return .cyan
            case .thunderstorm: return .purple
            case .unknown: return .white
            }
        case .wind: return .cyan
        case .humidity: return .blue
        case .precipitation: return .indigo
        case .uvIndex(let value): return uvColor(value)
        }
    }

    private func uvColor(_ value: Double) -> Color {
        switch value {
        case ..<3: return .green
        case 3..<6: return .yellow
        case 6..<8: return .orange
        case 8..<11: return .red
        default: return .purple
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
        case .condition(let condition):
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


    private func dismiss() {
        withAnimation(.easeOut(duration: AppTheme.modalTransitionDuration)) {
            isPresented = false
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
