import SwiftUI

// MARK: - Staggered Animation Modifier

struct StaggeredAnimationModifier: ViewModifier {
    let index: Int
    let isVisible: Bool
    let baseDelay: Double
    let staggerDelay: Double
    let duration: Double

    @State private var hasAppeared = false

    func body(content: Content) -> some View {
        content
            .opacity(hasAppeared ? 1 : 0)
            .offset(y: hasAppeared ? 0 : 20)
            .onAppear {
                guard !hasAppeared else { return }
                let delay = baseDelay + (Double(index) * staggerDelay)
                withAnimation(.easeOut(duration: duration).delay(delay)) {
                    hasAppeared = true
                }
            }
            .onChange(of: isVisible) { _, newValue in
                if newValue {
                    hasAppeared = false
                    let delay = baseDelay + (Double(index) * staggerDelay)
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

    @State private var hasAppeared = false

    func body(content: Content) -> some View {
        content
            .opacity(hasAppeared ? 1 : 0)
            .scaleEffect(hasAppeared ? 1 : 0.8)
            .onAppear {
                guard !hasAppeared else { return }
                let delay = baseDelay + (Double(index) * staggerDelay)
                withAnimation(.spring(response: duration, dampingFraction: 0.7).delay(delay)) {
                    hasAppeared = true
                }
            }
            .onChange(of: isVisible) { _, newValue in
                if newValue {
                    hasAppeared = false
                    let delay = baseDelay + (Double(index) * staggerDelay)
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
