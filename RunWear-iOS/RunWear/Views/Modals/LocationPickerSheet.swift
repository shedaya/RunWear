import SwiftUI
import CoreLocation

struct LocationPickerSheet: View {
    @Binding var isPresented: Bool
    var isUsingGPS: Bool
    var onUseCurrentLocation: () -> Void
    var onSelectLocation: (CLLocationCoordinate2D, String) -> Void

    @State private var searchText = ""
    @State private var searchResults: [LocationSearchResult] = []
    @State private var isSearching = false
    @State private var dragOffset: CGFloat = 0

    private let dismissThreshold: CGFloat = 100
    private let geocoder = CLGeocoder()

    var body: some View {
        ZStack {
            // Backdrop
            Color.black.opacity(0.5)
                .ignoresSafeArea()
                .onTapGesture {
                    dismiss()
                }

            // Sheet content
            VStack(spacing: 0) {
                Spacer()

                VStack(spacing: 16) {
                    // Drag handle
                    dragHandle

                    // Title
                    Text("Set Location")
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.top, 8)

                    // Use Current Location option
                    currentLocationButton

                    // Divider
                    dividerWithText

                    // Search field
                    searchField

                    // Search results
                    if !searchResults.isEmpty {
                        searchResultsList
                    }

                    Spacer().frame(height: 16)
                }
                .padding(24)
                .background(
                    RoundedRectangle(cornerRadius: AppTheme.modalCornerRadius)
                        .fill(AppTheme.darkBackground)
                )
                .offset(y: dragOffset)
                .gesture(dragGesture)
            }
            .ignoresSafeArea(edges: .bottom)
        }
        .transition(.opacity)
    }

    // MARK: - Drag Handle

    private var dragHandle: some View {
        RoundedRectangle(cornerRadius: 3)
            .fill(Color.white.opacity(0.3))
            .frame(width: 40, height: 5)
            .padding(.top, 8)
    }

    // MARK: - Use Current Location Button

    private var currentLocationButton: some View {
        Button(action: {
            onUseCurrentLocation()
            dismiss()
        }) {
            HStack(spacing: 14) {
                // GPS Icon
                Circle()
                    .fill(AppTheme.primaryColor)
                    .frame(width: 44, height: 44)
                    .overlay(
                        Image(systemName: "location.fill")
                            .font(.system(size: 20))
                            .foregroundColor(.white)
                    )

                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 8) {
                        Text("Use Current Location")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(.white)

                        if isUsingGPS {
                            Text("✓ Active")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(AppTheme.primaryColor)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 2)
                                .background(
                                    RoundedRectangle(cornerRadius: 4)
                                        .fill(AppTheme.primaryColor.opacity(0.2))
                                )
                        }
                    }

                    Text(isUsingGPS ? "GPS location enabled" : "Requires location permission")
                        .font(.system(size: 13))
                        .foregroundColor(.white.opacity(0.6))
                }

                Spacer()
            }
            .padding(16)
            .background(
                RoundedRectangle(cornerRadius: 14)
                    .fill(isUsingGPS ? AppTheme.primaryColor.opacity(0.2) : Color.white.opacity(0.08))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(isUsingGPS ? AppTheme.primaryColor : Color.clear, lineWidth: 2)
            )
        }
    }

    // MARK: - Divider

    private var dividerWithText: some View {
        HStack {
            Rectangle()
                .fill(Color.white.opacity(0.15))
                .frame(height: 1)

            Text("or enter manually")
                .font(.system(size: 13))
                .foregroundColor(.white.opacity(0.5))
                .padding(.horizontal, 12)

            Rectangle()
                .fill(Color.white.opacity(0.15))
                .frame(height: 1)
        }
        .padding(.vertical, 4)
    }

    // MARK: - Search Field

    private var searchField: some View {
        HStack(spacing: 12) {
            Image(systemName: "magnifyingglass")
                .foregroundColor(.white.opacity(0.5))

            TextField("Enter city or ZIP code", text: $searchText)
                .foregroundColor(.white)
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
                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                    .scaleEffect(0.8)
            }
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.white.opacity(0.08))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.white.opacity(0.15), lineWidth: 1)
        )
    }

    // MARK: - Search Results

    private var searchResultsList: some View {
        ScrollView {
            VStack(spacing: 0) {
                ForEach(searchResults) { result in
                    Button(action: {
                        onSelectLocation(result.coordinate, result.displayName)
                        dismiss()
                    }) {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(result.name)
                                    .font(.system(size: 15, weight: .medium))
                                    .foregroundColor(.white)
                                Text(result.subtitle)
                                    .font(.system(size: 13))
                                    .foregroundColor(.white.opacity(0.6))
                            }

                            Spacer()

                            Image(systemName: "chevron.right")
                                .font(.system(size: 14))
                                .foregroundColor(.white.opacity(0.4))
                        }
                        .padding(.vertical, 12)
                    }

                    Divider()
                        .background(Color.white.opacity(0.1))
                }
            }
        }
        .frame(maxHeight: 200)
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

                return LocationSearchResult(
                    name: name,
                    subtitle: subtitle,
                    displayName: [name, placemark.administrativeArea].compactMap { $0 }.joined(separator: ", "),
                    coordinate: location.coordinate
                )
            }
        }
    }

    // MARK: - Gestures

    private var dragGesture: some Gesture {
        DragGesture()
            .onChanged { value in
                if value.translation.height > 0 {
                    dragOffset = value.translation.height
                }
            }
            .onEnded { value in
                if value.translation.height > dismissThreshold {
                    dismiss()
                } else {
                    withAnimation(.easeOut(duration: AppTheme.modalTransitionDuration)) {
                        dragOffset = 0
                    }
                }
            }
    }

    private func dismiss() {
        withAnimation(.easeOut(duration: AppTheme.modalTransitionDuration)) {
            isPresented = false
        }
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
