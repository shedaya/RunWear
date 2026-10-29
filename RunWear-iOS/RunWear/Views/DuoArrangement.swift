import SwiftUI

/// Two-pane arrangement for the Duo landscape layout (design record
/// §5.2, frame f5): the hero pane and the outfit column sit on
/// opposite sides of the division region — the fold — with the
/// even-column gap on the division itself.
///
/// On the iOS 27.1 SDK this is Apple's ArrangementView, which reads
/// the scene's reserved regions (.division when the device is
/// partially folded, .occlusion for the cameras) and places the two
/// columns around them. ArrangementView is an iOS 27.1 API and the
/// 27.1 SDK is newer than every current CI toolchain, so the native
/// path is behind a compiler gate as well as the runtime
/// availability check: older SDKs never see the symbol.
///
/// The pre-27.1 fallback reproduces the same geometry by hand: the
/// f5 split is asymmetric (455 pt leading / 372 pt trailing on the
/// 951 pt landscape window), so the fallback keeps that proportion
/// with a gap where the division region sits. Nothing here gates
/// functionality on fold pose — pose is never read; the layout keys
/// off the container's size only.
struct DuoSplitArrangement<Primary: View, Secondary: View>: View {
    @ViewBuilder let primary: () -> Primary
    @ViewBuilder let secondary: () -> Secondary

    var body: some View {
        #if compiler(>=6.4)
        if #available(iOS 27.1, *) {
            ArrangementView {
                primary()
            } secondary: {
                secondary()
            }
            .arrangementViewStyle(.split)
        } else {
            fallback
        }
        #else
        fallback
        #endif
    }

    /// Proportional split: 455 : 372 with the gap on the division.
    private var fallback: some View {
        GeometryReader { geo in
            let gap = min(40, max(16, geo.size.width * 0.046))
            let available = max(0, geo.size.width - gap)
            let primaryWidth = available * 455.0 / (455.0 + 372.0)
            HStack(alignment: .top, spacing: gap) {
                primary()
                    .frame(width: primaryWidth, alignment: .topLeading)
                secondary()
                    .frame(maxWidth: .infinity, alignment: .topLeading)
            }
            .frame(width: geo.size.width, height: geo.size.height, alignment: .topLeading)
        }
    }
}
