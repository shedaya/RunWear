import SwiftUI

// MARK: - Glass Morphism View Modifier

struct GlassMorphismModifier: ViewModifier {
    var cornerRadius: CGFloat = 16
    var opacity: Double = 0.1
    var borderOpacity: Double = 0.15

    func body(content: Content) -> some View {
        content
            .background(
                RoundedRectangle(cornerRadius: cornerRadius)
                    .fill(Color.white.opacity(opacity))
                    .background(
                        RoundedRectangle(cornerRadius: cornerRadius)
                            .fill(.ultraThinMaterial)
                    )
            )
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius)
                    .stroke(Color.white.opacity(borderOpacity), lineWidth: 1)
            )
    }
}

// MARK: - Dark Glass Morphism Modifier

struct DarkGlassMorphismModifier: ViewModifier {
    var cornerRadius: CGFloat = 16
    var opacity: Double = 0.3
    var borderOpacity: Double = 0.2

    func body(content: Content) -> some View {
        content
            .background(
                RoundedRectangle(cornerRadius: cornerRadius)
                    .fill(Color.black.opacity(opacity))
                    .background(
                        RoundedRectangle(cornerRadius: cornerRadius)
                            .fill(.ultraThinMaterial)
                    )
            )
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius)
                    .stroke(Color.white.opacity(borderOpacity), lineWidth: 1)
            )
    }
}

// MARK: - Pill Style Modifier

struct GlassPillModifier: ViewModifier {
    var isSelected: Bool = false

    func body(content: Content) -> some View {
        content
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(
                Capsule()
                    .fill(isSelected ? Color.white.opacity(0.25) : Color.white.opacity(0.1))
                    .background(
                        Capsule()
                            .fill(.ultraThinMaterial)
                    )
            )
            .overlay(
                Capsule()
                    .stroke(Color.white.opacity(isSelected ? 0.3 : 0.15), lineWidth: 1)
            )
    }
}

// MARK: - View Extensions

extension View {
    /// Applies glass morphism effect with white background
    func glassMorphism(
        cornerRadius: CGFloat = 16,
        opacity: Double = 0.1,
        borderOpacity: Double = 0.15
    ) -> some View {
        modifier(GlassMorphismModifier(
            cornerRadius: cornerRadius,
            opacity: opacity,
            borderOpacity: borderOpacity
        ))
    }

    /// Applies dark glass morphism effect
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

    /// Applies glass pill style
    func glassPill(isSelected: Bool = false) -> some View {
        modifier(GlassPillModifier(isSelected: isSelected))
    }
}

// MARK: - Glass Button Style

struct GlassButtonStyle: ButtonStyle {
    var isSelected: Bool = false

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(isSelected ? Color.white.opacity(0.25) : Color.white.opacity(0.1))
                    .background(
                        RoundedRectangle(cornerRadius: 12)
                            .fill(.ultraThinMaterial)
                    )
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color.white.opacity(isSelected ? 0.3 : 0.15), lineWidth: 1)
            )
            .scaleEffect(configuration.isPressed ? 0.95 : 1.0)
            .animation(.easeInOut(duration: 0.1), value: configuration.isPressed)
    }
}

// MARK: - Glass Card Style

struct GlassCardModifier: ViewModifier {
    var padding: CGFloat = 16

    func body(content: Content) -> some View {
        content
            .padding(padding)
            .background(
                RoundedRectangle(cornerRadius: AppTheme.cardCornerRadius)
                    .fill(Color.white.opacity(0.1))
                    .background(
                        RoundedRectangle(cornerRadius: AppTheme.cardCornerRadius)
                            .fill(.ultraThinMaterial)
                    )
            )
            .overlay(
                RoundedRectangle(cornerRadius: AppTheme.cardCornerRadius)
                    .stroke(Color.white.opacity(0.15), lineWidth: 1)
            )
    }
}

extension View {
    /// Applies glass card style with padding
    func glassCard(padding: CGFloat = 16) -> some View {
        modifier(GlassCardModifier(padding: padding))
    }
}
