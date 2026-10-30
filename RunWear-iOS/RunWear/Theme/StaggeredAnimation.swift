import SwiftUI

// MARK: - Stagger Timing (RW-6.6)
//
// One timing source for every cascade in the app. The cascade is capped:
// past `maxCascadedIndex` items appear at the last delay instead of the
// tail drifting later and later down a long list (≈5 items of travel max).
enum StaggerTiming {
    /// Highest index that still adds delay; later items reuse this delay.
    static let maxCascadedIndex = 4

    /// Effective delay for an item at `index`, with the cascade capped.
    static func effectiveDelay(index: Int, baseDelay: Double, staggerDelay: Double) -> Double {
        let cappedIndex = min(max(index, 0), maxCascadedIndex)
        return baseDelay + Double(cappedIndex) * staggerDelay
    }
}

// MARK: - Staggered Animation Modifier

struct StaggeredAnimationModifier: ViewModifier {
    let index: Int
    let isVisible: Bool
    let baseDelay: Double
    let staggerDelay: Double
    let duration: Double

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var hasAppeared = false

    func body(content: Content) -> some View {
        content
            // Reduce Motion: content is simply present — no travel, no fade.
            .opacity(reduceMotion || hasAppeared ? 1 : 0)
            .offset(y: reduceMotion || hasAppeared ? 0 : 20)
            .onAppear {
                guard !hasAppeared else { return }
                if reduceMotion {
                    hasAppeared = true
                    return
                }
                let delay = StaggerTiming.effectiveDelay(
                    index: index, baseDelay: baseDelay, staggerDelay: staggerDelay
                )
                withAnimation(.easeOut(duration: duration).delay(delay)) {
                    hasAppeared = true
                }
            }
            .onChange(of: isVisible) { _, newValue in
                if newValue {
                    if reduceMotion {
                        hasAppeared = true
                        return
                    }
                    hasAppeared = false
                    let delay = StaggerTiming.effectiveDelay(
                        index: index, baseDelay: baseDelay, staggerDelay: staggerDelay
                    )
                    withAnimation(.easeOut(duration: duration).delay(delay)) {
                        hasAppeared = true
                    }
                }
            }
    }
}

// MARK: - Staggered Scale Animation

struct StaggeredScaleAnimationModifier: ViewModifier {
    let index: Int
    let isVisible: Bool
    let baseDelay: Double
    let staggerDelay: Double
    let duration: Double

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var hasAppeared = false

    func body(content: Content) -> some View {
        content
            // Reduce Motion: content is simply present — no scale, no fade.
            .opacity(reduceMotion || hasAppeared ? 1 : 0)
            .scaleEffect(reduceMotion || hasAppeared ? 1 : 0.8)
            .onAppear {
                guard !hasAppeared else { return }
                if reduceMotion {
                    hasAppeared = true
                    return
                }
                let delay = StaggerTiming.effectiveDelay(
                    index: index, baseDelay: baseDelay, staggerDelay: staggerDelay
                )
                withAnimation(.spring(response: duration, dampingFraction: 0.7).delay(delay)) {
                    hasAppeared = true
                }
            }
            .onChange(of: isVisible) { _, newValue in
                if newValue {
                    if reduceMotion {
                        hasAppeared = true
                        return
                    }
                    hasAppeared = false
                    let delay = StaggerTiming.effectiveDelay(
                        index: index, baseDelay: baseDelay, staggerDelay: staggerDelay
                    )
                    withAnimation(.spring(response: duration, dampingFraction: 0.7).delay(delay)) {
                        hasAppeared = true
                    }
                }
            }
    }
}

// MARK: - View Extensions

extension View {
    /// Applies staggered fade-in animation with vertical offset
    /// - Parameters:
    ///   - index: Position in the stagger sequence (0, 1, 2...)
    ///   - isVisible: Trigger to restart animation
    ///   - baseDelay: Initial delay before first item animates (default: 0)
    ///   - staggerDelay: Delay between each item (default: 0.05 = 50ms)
    ///   - duration: Animation duration (default: 0.4 = 400ms)
    func staggeredAnimation(
        index: Int,
        isVisible: Bool = true,
        baseDelay: Double = 0,
        staggerDelay: Double = AppTheme.staggerDelay,
        duration: Double = AppTheme.staggerDuration
    ) -> some View {
        modifier(StaggeredAnimationModifier(
            index: index,
            isVisible: isVisible,
            baseDelay: baseDelay,
            staggerDelay: staggerDelay,
            duration: duration
        ))
    }

    /// Applies staggered scale animation
    func staggeredScaleAnimation(
        index: Int,
        isVisible: Bool = true,
        baseDelay: Double = 0,
        staggerDelay: Double = AppTheme.staggerDelay,
        duration: Double = AppTheme.staggerDuration
    ) -> some View {
        modifier(StaggeredScaleAnimationModifier(
            index: index,
            isVisible: isVisible,
            baseDelay: baseDelay,
            staggerDelay: staggerDelay,
            duration: duration
        ))
    }
}

// MARK: - Staggered Container

/// A container that automatically applies staggered animations to its children
struct StaggeredVStack<Content: View>: View {
    let spacing: CGFloat
    let alignment: HorizontalAlignment
    let isVisible: Bool
    @ViewBuilder let content: () -> Content

    init(
        spacing: CGFloat = 12,
        alignment: HorizontalAlignment = .leading,
        isVisible: Bool = true,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.spacing = spacing
        self.alignment = alignment
        self.isVisible = isVisible
        self.content = content
    }

    var body: some View {
        VStack(alignment: alignment, spacing: spacing) {
            content()
        }
    }
}

// MARK: - Animated List Item

/// Wrapper for items that should animate in a list
struct AnimatedListItem<Content: View>: View {
    let index: Int
    let isVisible: Bool
    @ViewBuilder let content: () -> Content

    var body: some View {
        content()
            .staggeredAnimation(index: index, isVisible: isVisible)
    }
}

// MARK: - Preview

#Preview {
    ScrollView {
        VStack(spacing: 16) {
            ForEach(0..<5) { index in
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.blue.opacity(0.3))
                    .frame(height: 80)
                    .overlay(Text("Item \(index + 1)"))
                    .staggeredAnimation(index: index)
            }
        }
        .padding()
    }
    .background(Color.black)
}
