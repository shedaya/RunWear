import SwiftUI
import CoreLocation

// MARK: - Location (RW-26, Section E — frames 23–25)
//
// Search on top; a "Current location" row (marked CURRENT when it
// is the active selection); RECENT LOCATIONS — places actually
// selected before, most-recent-first, capped at 5, removed by the
// native swipe → Remove; browse/search results below. First run:
// the recents section is absent and a quiet hint card sits where
// the list will live.
struct LocationPickerSheet: View {
    @Binding var isPresented: Bool
    var isUsingGPS: Bool
    var onUseCurrentLocation: () -> Void
    var onSelectLocation: (CLLocationCoordinate2D, String) -> Void

    @State private var searchText = ""
    @State private var searchResults: [LocationSearchResult] = []
    @State private var isSearching = false
    /// GEN-3 Y-15: the search debounce task and the staleness
    /// gate — see LocationSearchGate for the dead-tap diagnosis.
    @State private var searchTask: Task<Void, Never>?
    @State private var searchGate = LocationSearchGate()
    @StateObject private var recentsModel = RecentLocationsModel()

    private let geocoder = CLGeocoder()

    var body: some View {
        NavigationStack {
            List {
                // Search on top. GEN-5 (RW-28 item 31): the
                // field carries a visible box border whether or
                // not it is focused — the gen-4 census (A-25/N-10)
                // caught the unfocused field floating borderless
                // on the sheet.
                Section {
                    HStack(spacing: 10) {
                        Image(systemName: "magnifyingglass")
                            .foregroundColor(AppTheme.inkMuted)
                        TextField("Search a place", text: $searchText)
                            .autocorrectionDisabled()
                            .onChange(of: searchText) { _, newValue in
                                if newValue.count >= 2 {
                                    scheduleSearch(query: newValue)
                                } else {
                                    searchTask?.cancel()
                                    searchGate.invalidate()
                                    isSearching = false
                                    searchResults = []
                                }
                            }
                        if isSearching {
                            ProgressView()
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .background(RoundedRectangle(cornerRadius: 10).fill(AppTheme.cardFill))
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(AppTheme.pinePayload, lineWidth: 1))
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                }

                // Current location row.
                Section {
                    Button {
                        onUseCurrentLocation()
                        close()
                    } label: {
                        HStack(spacing: 12) {
                            Image(systemName: "location.fill")
                                .foregroundColor(AppTheme.pinePayload)
                            Text("Current location")
                                .font(.appSecondary.weight(.semibold))
                                .foregroundColor(AppTheme.inkPrimary)
                            Spacer()
                            if isUsingGPS {
                                Text("CURRENT")
                                    .font(.appEyebrow)
                                    .foregroundColor(AppTheme.pinePayload)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 3)
                                    .background(Capsule().fill(AppTheme.primaryColor.opacity(0.15)))
                            }
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }

                // Recents — actually-selected places only.
                if recentsModel.recents.isEmpty {
                    Section {
                        // First run (frame 25): no ghost rows —
                        // a quiet hint card where the list will live.
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Places you pick will be kept here")
                                .font(.appSecondary.weight(.semibold))
                                .foregroundColor(AppTheme.inkPrimary)
                            Text("Your recent places will wait here — pick a place once and it joins a short list — home, the office, trip cities — so jumping back takes one tap. Nothing is saved until you choose a place.")
                                .font(.appFootnote)
                                .foregroundColor(AppTheme.inkSecondary)
                        }
                        .padding(.vertical, 4)
                    }
                } else {
                    Section("Recent Locations") {
                        ForEach(recentsModel.recents) { recent in
                            Button {
                                onSelectLocation(
                                    CLLocationCoordinate2D(
                                        latitude: recent.latitude,
                                        longitude: recent.longitude
                                    ),
                                    recent.name
                                )
                                recentsModel.record(recent)
                                close()
                            } label: {
                                HStack {
                                    Text(recent.name)
                                        .font(.appSecondary)
                                        .foregroundColor(AppTheme.inkPrimary)
                                    Spacer()
                                    Image(systemName: "chevron.right")
                                        .font(.appFootnote.weight(.semibold))
                                        .foregroundColor(AppTheme.inkMuted)
                                }
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .swipeActions(edge: .trailing) {
                                Button("Remove", role: .destructive) {
                                    recentsModel.remove(id: recent.id)
                                }
                            }
                        }
                    }
                }

                // Browse / search results below.
                if !searchResults.isEmpty {
                    Section("Results") {
                        ForEach(searchResults) { result in
                            Button {
                                onSelectLocation(result.coordinate, result.displayName)
                                recentsModel.record(RecentLocation(
                                    name: result.displayName,
                                    latitude: result.coordinate.latitude,
                                    longitude: result.coordinate.longitude
                                ))
                                close()
                            } label: {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(result.name)
                                        .font(.appSecondary.weight(.medium))
                                        .foregroundColor(AppTheme.inkPrimary)
                                    Text(result.subtitle)
                                        .font(.appFootnote)
                                        .foregroundColor(AppTheme.inkSecondary)
                                }
                                // GEN-3 Y-15: the hit area is the
                                // WHOLE row, not the text block —
                                // with .plain, the contentShape is
                                // the hit area, and the unframed
                                // VStack made it text-sized (the
                                // sheet's other tappable rows are
                                // full-width HStacks with Spacers).
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .listStyle(.insetGrouped)
            // GEN-5 design pass (cream map rows 23/56): the
            // ROWS take the sheet tone too — build 25 painted
            // the cream ground behind the list and left every
            // row card in the system's white (light) / cool
            // grey (dark). The search row keeps its own clear
            // background (its card is drawn inside the row).
            .listRowBackground(AppTheme.sheet)
            .scrollContentBackground(.hidden)
            .background(AppTheme.background.ignoresSafeArea())
            .navigationTitle("Set Location")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { close() }
                }
            }
        }
    }

    // MARK: - Search Logic

    /// GEN-3 Y-15: debounce the keystroke storm — one search,
    /// 250 ms after typing settles, cancellable by the next
    /// keystroke. The pre-fix code geocoded on EVERY keystroke,
    /// and its trailing responses kept replacing the result rows
    /// (fresh UUID identities — full row rebuilds) for a second
    /// or more after typing stopped, destroying rows mid-touch.
    private func scheduleSearch(query: String) {
        searchTask?.cancel()
        searchTask = Task {
            try? await Task.sleep(nanoseconds: 250_000_000)
            guard !Task.isCancelled else { return }
            searchLocation(query: query)
        }
    }

    private func searchLocation(query: String) {
        let token = searchGate.begin(query: query)
        isSearching = true

        geocoder.cancelGeocode()
        geocoder.geocodeAddressString(query) { placemarks, error in
            // GEN-3 Y-15: a stale completion (an earlier query's
            // answer, or one issued before the text was cleared)
            // NEVER touches the list — the rows on screen, and a
            // touch in flight on them, survive it.
            guard searchGate.shouldApply(token: token, query: query) else { return }
            isSearching = false

            guard let placemarks = placemarks, error == nil else {
                searchResults = []
                return
            }

            let candidates = placemarks.compactMap { placemark -> LocationSearchCandidate? in
                guard let location = placemark.location else { return nil }

                let name = placemark.locality ?? placemark.name ?? "Unknown"
                let subtitle = [placemark.administrativeArea, placemark.country]
                    .compactMap { $0 }
                    .joined(separator: ", ")

                // WS-4: the selection's name is derived through
                // the SimplePlace model ONCE, here at geocode
                // time — city + abbreviated region. The pre-WS-4
                // code concatenated locality + administrativeArea
                // verbatim, so a full region name ("New York,
                // New York") became the saved place, the recents
                // row, and every rendered line downstream.
                let simple = SimplePlace(
                    city: placemark.locality ?? placemark.name,
                    region: placemark.administrativeArea
                )
                return LocationSearchCandidate(
                    name: name,
                    subtitle: subtitle,
                    displayName: simple?.displayName ?? name,
                    latitude: location.coordinate.latitude,
                    longitude: location.coordinate.longitude
                )
            }

            // GEN-3 X-12: dedupe by place identity at result
            // construction — the same place returned twice by
            // the geocoder renders ONCE. Identity (SimplePlace
            // key + coordinate), not the name string, so
            // distinct same-name places both survive.
            searchResults = LocationSearchResults.deduplicated(candidates).map { candidate in
                LocationSearchResult(
                    // GEN-3 Y-15: the row's identity is the
                    // PLACE identity, stable across refreshes —
                    // the pre-fix UUID-per-construction meant
                    // every response rebuilt every row view,
                    // killing touches in flight.
                    id: candidate.identity,
                    name: candidate.name,
                    subtitle: candidate.subtitle,
                    displayName: candidate.displayName,
                    coordinate: CLLocationCoordinate2D(
                        latitude: candidate.latitude,
                        longitude: candidate.longitude
                    )
                )
            }
        }
    }

    /// State-driven dismissal (RW-6.12).
    private func close() {
        isPresented = false
    }
}

/// Observable wrapper over the recents store (the store itself is
/// the pure, test-pinned half).
@MainActor
final class RecentLocationsModel: ObservableObject {
    @Published private(set) var recents: [RecentLocation]
    private let store = RecentLocationsStore()

    init() {
        self.recents = store.recents
    }

    func record(_ selection: RecentLocation) {
        store.record(selection)
        recents = store.recents
    }

    func remove(id: String) {
        store.remove(id: id)
        recents = store.recents
    }
}

// MARK: - Search Result Model

struct LocationSearchResult: Identifiable {
    /// The place identity (LocationSearchCandidate.identity) —
    /// stable across result refreshes, so SwiftUI keeps the row
    /// view (and a touch in flight on it) when a refresh lands.
    let id: String
    let name: String
    let subtitle: String
    let displayName: String
    let coordinate: CLLocationCoordinate2D
}

// MARK: - Preview

#Preview {
    LocationPickerSheet(
        isPresented: .constant(true),
        isUsingGPS: true,
        onUseCurrentLocation: {},
        onSelectLocation: { _, _ in }
    )
}
