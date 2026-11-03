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
    @StateObject private var recentsModel = RecentLocationsModel()

    private let geocoder = CLGeocoder()

    var body: some View {
        NavigationStack {
            List {
                // Search on top.
                Section {
                    HStack(spacing: 10) {
                        Image(systemName: "magnifyingglass")
                            .foregroundColor(AppTheme.inkMuted)
                        TextField("Search for a city", text: $searchText)
                            .autocorrectionDisabled()
                            .onChange(of: searchText) { _, newValue in
                                if newValue.count >= 2 {
                                    searchLocation(query: newValue)
                                } else {
                                    searchResults = []
                                }
                            }
                        if isSearching {
                            ProgressView()
                        }
                    }
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
                            Text("Search above and choose a place, and it will be one tap away next time.")
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
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .listStyle(.insetGrouped)
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

    private func searchLocation(query: String) {
        isSearching = true

        geocoder.cancelGeocode()
        geocoder.geocodeAddressString(query) { placemarks, error in
            isSearching = false

            guard let placemarks = placemarks, error == nil else {
                searchResults = []
                return
            }

            searchResults = placemarks.compactMap { placemark in
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
                return LocationSearchResult(
                    name: name,
                    subtitle: subtitle,
                    displayName: simple?.displayName ?? name,
                    coordinate: location.coordinate
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
    let id = UUID()
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
