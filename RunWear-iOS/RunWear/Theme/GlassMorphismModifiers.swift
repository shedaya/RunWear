import SwiftUI

// MARK: - Liquid Glass (iOS 26 native)
//
// The app floor is iOS 26 (raised 2026-10-30 by standing decision — no
// legacy fallbacks), so every "glass" surface in the app is the native
// Liquid Glass material via `glassEffect`. There is exactly ONE glass
// implementation in the app and it lives in this file; views never
// hand-roll a material recipe.
//
// Placement rules (UX audit RW-6 glass discipline — legibility first):
// - Glass belongs on floating / navigation surfaces only: pills, floating
//   controls over the hero, sheet and toolbar chrome (system-provided).
// - Never glass-on-glass, and never behind dense text: content cards keep
//   the flat `AppTheme.cardFillOnDark` fill.

// MARK: - Glass Surface Modifier

struct GlassMorphismModifier: ViewModifier {
    var cornerRadius: CGFloat = 16
    // Kept for source compatibility with existing call sites. The native
    // glass material defines its own translucency, so these no longer tune
    // a hand-built fill the way the pre-iOS-26 recipe did.
    var opacity: Double = 0.12
    var borderOpacity: Double = 0.15

    func body(content: Content) -> some View {
        content
            .glassEffect(.regular, in: RoundedRectangle(cornerRadius: cornerRadius))
    }
}

// MARK: - Dark Glass Surface Modifier

struct DarkGlassMorphismModifier: ViewModifier {
    var cornerRadius: CGFloat = 16
    var opacity: Double = 0.3
    var borderOpacity: Double = 0.2

    func body(content: Content) -> some View {
        content
            .glassEffect(
                .regular.tint(.black.opacity(0.35)),
                in: RoundedRectangle(cornerRadius: cornerRadius)
            )
    }
}

// MARK: - Pill Style Modifier

struct GlassPillModifier: ViewModifier {
    // Selected/active state carries the brand-teal tint (e.g. the hero's
    // "Now" pill); the unselected state is clear glass.
    var isSelected: Bool = false

    func body(content: Content) -> some View {
        content
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .glassEffect(
                isSelected
                    ? GlassEffect.regular.tint(AppTheme.primaryColor.opacity(0.45)).interactive()
                    : GlassEffect.regular.interactive(),
                in: Capsule()
            )
    }
}

// MARK: - Glass Button Modifier

/// Interactive glass for tappable grid/selection buttons (the date and
/// time picker grids). The selected state is the brand-teal tint of the
/// same glass — one material, two states, no hand-built fill/stroke pair.
struct GlassButtonModifier: ViewModifier {
    var isSelected: Bool = false
    var cornerRadius: CGFloat = 12

    func body(content: Content) -> some View {
        content
            .glassEffect(
                isSelected
                    ? GlassEffect.regular.tint(AppTheme.primaryColor.opacity(0.4)).interactive()
                    : GlassEffect.regular.interactive(),
                in: RoundedRectangle(cornerRadius: cornerRadius)
            )
    }
}

// MARK: - View Extensions

extension View {
    /// Applies the native Liquid Glass surface treatment.
    func glassMorphism(
        cornerRadius: CGFloat = 16,
        opacity: Double = 0.12,
        borderOpacity: Double = 0.15
    ) -> some View {
        modifier(GlassMorphismModifier(
            cornerRadius: cornerRadius,
            opacity: opacity,
            borderOpacity: borderOpacity
        ))
    }

    /// Applies the dark-tinted native Liquid Glass surface treatment.
    func darkGlassMorphism(
        cornerRadius: CGFloat = 16,
        opacity: Double = 0.3,
        borderOpacity: Double = 0.2
    ) -> some View {
        modifier(DarkGlassMorphismModifier(
            cornerRadius: cornerRadius,
            opacity: opacity,
            borderOpacity: borderOpacity
        ))
    }

    /// Applies the glass pill style — the one pill chrome for the app
    /// (hero pills, weather pills). Interactive glass: inside a button the
    /// pill itself responds to the press.
    func glassPill(isSelected: Bool = false) -> some View {
        modifier(GlassPillModifier(isSelected: isSelected))
    }

    /// Applies interactive glass button chrome (picker grid buttons);
    /// `isSelected` tints the same glass with the brand color.
    func glassButton(isSelected: Bool = false, cornerRadius: CGFloat = 12) -> some View {
        modifier(GlassButtonModifier(isSelected: isSelected, cornerRadius: cornerRadius))
    }
}
