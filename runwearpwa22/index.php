<!DOCTYPE html>
<!-- RunWear PWA v3.9 -->
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
    <meta name="theme-color" content="#00796B">
    <meta name="apple-mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
    <meta name="apple-mobile-web-app-title" content="RunWear">
    <meta name="application-name" content="RunWear">
    <meta name="description" content="Get personalized running outfit recommendations based on real-time weather conditions">
    <meta name="mobile-web-app-capable" content="yes">
    <meta name="impact-site-verification" value="86649e82-8861-41f8-9773-c3a758e9a761">
    <title>RunWear - Running Outfit Guide</title>
    
    <!-- PWA Manifest -->
    <link rel="manifest" href="manifest.json">
    
    <!-- Favicons -->
    <link rel="icon" type="image/x-icon" href="favicon.ico">
    <link rel="icon" type="image/png" sizes="16x16" href="favicon-16.png">
    <link rel="icon" type="image/png" sizes="32x32" href="favicon-32.png">
    
    <!-- Apple Touch Icons -->
    <link rel="apple-touch-icon" href="apple-touch-icon.png">
    <link rel="apple-touch-icon" sizes="180x180" href="apple-touch-icon.png">
    <link rel="apple-touch-icon" sizes="152x152" href="icon-152.png">
    <link rel="apple-touch-icon" sizes="144x144" href="icon-144.png">
    <link rel="apple-touch-icon" sizes="128x128" href="icon-128.png">
    <link rel="apple-touch-icon" sizes="72x72" href="icon-72.png">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link rel="preconnect" href="https://images.unsplash.com">

    <!-- Preload default hero images for instant display -->
    <link rel="preload" as="image" href="https://images.unsplash.com/photo-1571008887538-b36bb32f4571?w=800&h=1200&fit=crop">
    <link rel="preload" as="image" href="https://images.unsplash.com/photo-1486218119243-13883505764c?w=800&h=1200&fit=crop">
    <link rel="preload" as="image" href="https://images.unsplash.com/photo-1552674605-db6ffd4facb5?w=800&h=1200&fit=crop">
    <link rel="preload" as="image" href="https://images.unsplash.com/photo-1476480862126-209bfaa8edc8?w=800&h=1200&fit=crop">
    <link rel="preload" as="image" href="https://images.unsplash.com/photo-1485727749690-d091e8284ef3?w=800&h=1200&fit=crop">
    <link rel="preload" as="image" href="https://images.unsplash.com/photo-1544899489-a083461b088c?w=800&h=1200&fit=crop">
    <link href="https://fonts.googleapis.com/css2?family=Bebas+Neue&family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap" rel="stylesheet">
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }

        :root {
            /* Brand Colors - Deep Teal */
            --primary: #00796B;
            --primary-dark: #004D40;
            --primary-light: #4DB6AC;
            --primary-glow: rgba(0, 121, 107, 0.3);

            /* Dark Mode Backgrounds */
            --bg-dark: #0A0A0A;
            --bg-card: #1A1A1A;
            --bg-card-light: #262626;

            /* Text Colors */
            --text-primary: #FFFFFF;
            --text-secondary: #B3B3B3;
            --text-muted: #737373;

            /* Temperature Bracket Colors */
            --temp-freezing: #8B5CF6;  /* <20°F */
            --temp-cold: #6366F1;      /* 20-34°F */
            --temp-cool: #3B82F6;      /* 35-49°F */
            --temp-mild: #10B981;      /* 50-64°F */
            --temp-warm: #F59E0B;      /* 65-79°F */
            --temp-hot: #F97316;       /* 80°F+ */

            /* Category Colors */
            --cat-top: #00796B;
            --cat-bottom: #1565C0;
            --cat-head: #2E7D32;
            --cat-hands: #5E35B1;
            --cat-accessories: #E65100;

            /* Glass Morphism */
            --glass-bg: rgba(255, 255, 255, 0.12);
            --glass-border: rgba(255, 255, 255, 0.15);
            --glass-blur: 20px;

            /* Legacy mappings for compatibility */
            --bg: var(--bg-dark);
            --surface: var(--bg-card);
            --surface-dim: var(--bg-card-light);
            --text: var(--text-primary);
            --border: rgba(255, 255, 255, 0.1);
        }

        body {
            font-family: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: var(--bg-dark);
            color: var(--text-primary);
            min-height: 100vh;
            min-height: 100dvh;
            overflow-x: hidden;
            -webkit-font-smoothing: antialiased;
        }

        .display-font {
            font-family: 'Bebas Neue', sans-serif;
            letter-spacing: -0.02em;
        }

        /* ========== ANIMATIONS ========== */
        @keyframes slideUp {
            from { transform: translateY(20px); opacity: 0; }
            to { transform: translateY(0); opacity: 1; }
        }

        @keyframes fadeIn {
            from { opacity: 0; }
            to { opacity: 1; }
        }

        @keyframes shimmer {
            0% { background-position: -200% 0; }
            100% { background-position: 200% 0; }
        }

        @keyframes pullRefreshSpin {
            from { transform: rotate(0deg); }
            to { transform: rotate(360deg); }
        }

        @keyframes heroImageIn {
            from { opacity: 0; transform: scale(1.15); }
            to { opacity: 1; transform: scale(1.05); }
        }

        /* ========== HERO SECTION ========== */
        .hero {
            position: relative;
            height: 75vh;
            min-height: 500px;
            max-height: 700px;
            overflow: hidden;
            background: var(--bg-dark);
        }

        .hero-image-container {
            position: absolute;
            inset: 0;
        }

        .hero-image {
            width: 100%;
            height: 100%;
            object-fit: cover;
            object-position: center 30%;
            transform: scale(1.05);
            animation: heroImageIn 1s ease forwards;
        }

        .hero-image-placeholder {
            width: 100%;
            height: 100%;
            background: linear-gradient(135deg, var(--bg-card) 0%, var(--bg-dark) 100%);
        }

        /* ========== SKELETON LOADING ========== */
        .skeleton {
            background: linear-gradient(90deg, var(--bg-card) 25%, rgba(255,255,255,0.08) 50%, var(--bg-card) 75%);
            background-size: 200% 100%;
            animation: shimmer 1.5s infinite;
            border-radius: 8px;
        }

        .skeleton-hero {
            height: 75vh;
            min-height: 500px;
            max-height: 700px;
            background: var(--bg-card);
            position: relative;
        }

        .skeleton-hero-content {
            position: absolute;
            bottom: 0;
            left: 0;
            right: 0;
            padding: 24px 20px;
        }

        .skeleton-datetime {
            width: 180px;
            height: 36px;
            margin-bottom: 16px;
            border-radius: 100px;
        }

        .skeleton-temp {
            width: 140px;
            height: 96px;
            margin-bottom: 16px;
            border-radius: 16px;
        }

        .skeleton-pills {
            display: flex;
            gap: 8px;
        }

        .skeleton-pill {
            width: 70px;
            height: 36px;
            border-radius: 100px;
        }

        .skeleton-section {
            padding: 20px;
        }

        .skeleton-section-title {
            width: 160px;
            height: 24px;
            margin-bottom: 16px;
        }

        .skeleton-card {
            height: 72px;
            margin-bottom: 12px;
            border-radius: 16px;
        }

        /* ========== PULL TO REFRESH ========== */
        .pull-refresh-indicator {
            position: fixed;
            top: 0;
            left: 50%;
            transform: translateX(-50%) translateY(-60px);
            z-index: 1000;
            width: 40px;
            height: 40px;
            background: var(--bg-card);
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            box-shadow: 0 4px 20px rgba(0,0,0,0.3);
            transition: transform 0.3s ease;
            opacity: 0;
        }

        .pull-refresh-indicator.pulling {
            opacity: 1;
        }

        .pull-refresh-indicator.refreshing {
            transform: translateX(-50%) translateY(20px);
            opacity: 1;
        }

        .pull-refresh-indicator svg {
            width: 24px;
            height: 24px;
            fill: var(--text-primary);
            transition: transform 0.2s ease;
        }

        .pull-refresh-indicator.refreshing svg {
            animation: pullRefreshSpin 1s linear infinite;
        }

        /* ========== WEATHER PILLS EXPANDED ========== */
        .weather-pills-container {
            position: relative;
        }

        .weather-pill {
            cursor: pointer;
            transition: all 0.2s ease;
        }

        .weather-pill:active {
            transform: scale(0.95);
        }

        .weather-detail-popup {
            position: fixed;
            bottom: 0;
            left: 0;
            right: 0;
            background: var(--bg-card);
            border-radius: 24px 24px 0 0;
            padding: 24px 20px;
            transform: translateY(100%);
            transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
            z-index: 1001;
            max-height: 50vh;
            overflow-y: auto;
        }

        .weather-detail-popup.active {
            transform: translateY(0);
        }

        .weather-detail-header {
            display: flex;
            align-items: center;
            gap: 12px;
            margin-bottom: 20px;
        }

        .weather-detail-icon {
            width: 48px;
            height: 48px;
            background: rgba(255,255,255,0.1);
            border-radius: 12px;
            display: flex;
            align-items: center;
            justify-content: center;
        }

        .weather-detail-icon svg {
            width: 28px;
            height: 28px;
            fill: var(--text-primary);
        }

        .weather-detail-title {
            font-size: 20px;
            font-weight: 600;
        }

        .weather-detail-grid {
            display: grid;
            grid-template-columns: repeat(2, 1fr);
            gap: 12px;
        }

        .weather-detail-item {
            background: rgba(255,255,255,0.05);
            padding: 16px;
            border-radius: 12px;
        }

        .weather-detail-label {
            font-size: 12px;
            color: var(--text-secondary);
            margin-bottom: 4px;
        }

        .weather-detail-value {
            font-size: 18px;
            font-weight: 600;
        }

        /* ========== OUTFIT CARD EXPANDED ========== */
        .outfit-card {
            cursor: pointer;
        }

        .outfit-card:active {
            transform: scale(0.98);
        }

        .outfit-detail-sheet {
            position: fixed;
            bottom: 0;
            left: 0;
            right: 0;
            background: var(--bg-card);
            border-radius: 24px 24px 0 0;
            padding: 24px 20px;
            transform: translateY(100%);
            transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
            z-index: 1001;
            max-height: 70vh;
            overflow-y: auto;
        }

        .outfit-detail-sheet.active {
            transform: translateY(0);
        }

        .outfit-detail-handle {
            width: 40px;
            height: 4px;
            background: rgba(255,255,255,0.3);
            border-radius: 2px;
            margin: 0 auto 20px;
        }

        .outfit-detail-header {
            display: flex;
            align-items: center;
            gap: 16px;
            margin-bottom: 20px;
        }

        .outfit-detail-icon {
            width: 64px;
            height: 64px;
            border-radius: 16px;
            display: flex;
            align-items: center;
            justify-content: center;
        }

        .outfit-detail-icon svg {
            width: 32px;
            height: 32px;
        }

        .outfit-detail-name {
            font-size: 22px;
            font-weight: 600;
            margin-bottom: 4px;
        }

        .outfit-detail-category {
            color: var(--text-secondary);
            font-size: 14px;
        }

        .outfit-detail-desc {
            background: rgba(255,255,255,0.05);
            padding: 16px;
            border-radius: 12px;
            margin-bottom: 20px;
            line-height: 1.5;
        }

        .outfit-detail-actions {
            display: flex;
            gap: 12px;
        }

        .outfit-detail-btn {
            flex: 1;
            padding: 14px;
            border-radius: 12px;
            font-size: 15px;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.2s ease;
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
        }

        .outfit-detail-btn.primary {
            background: var(--accent);
            color: white;
            border: none;
        }

        .outfit-detail-btn.secondary {
            background: rgba(255,255,255,0.1);
            color: var(--text-primary);
            border: none;
        }

        .outfit-detail-btn:active {
            transform: scale(0.98);
        }

        /* ========== TIP CARD EXPANDED ========== */
        .tip-card {
            cursor: pointer;
            transition: all 0.2s ease;
        }

        .tip-card:active {
            transform: scale(0.98);
        }

        .tip-card.expanded .tip-text {
            -webkit-line-clamp: unset;
            max-height: none;
        }

        .tip-card .tip-more {
            display: none;
            margin-top: 12px;
            padding-top: 12px;
            border-top: 1px solid rgba(255,255,255,0.1);
        }

        .tip-card.expanded .tip-more {
            display: block;
        }

        /* ========== OVERLAY FOR SHEETS ========== */
        .sheet-overlay {
            position: fixed;
            inset: 0;
            background: rgba(0,0,0,0.5);
            opacity: 0;
            pointer-events: none;
            transition: opacity 0.3s ease;
            z-index: 1000;
        }

        .sheet-overlay.active {
            opacity: 1;
            pointer-events: auto;
        }

        /* ========== TOAST NOTIFICATION ========== */
        .toast {
            position: fixed;
            bottom: 100px;
            left: 50%;
            transform: translateX(-50%) translateY(20px);
            background: var(--bg-card);
            color: var(--text-primary);
            padding: 12px 24px;
            border-radius: 100px;
            font-size: 14px;
            font-weight: 500;
            box-shadow: 0 4px 20px rgba(0,0,0,0.4);
            opacity: 0;
            transition: all 0.3s ease;
            z-index: 2000;
        }

        .toast.visible {
            opacity: 1;
            transform: translateX(-50%) translateY(0);
        }

        /* ========== SHARE BUTTON ========== */
        .share-btn {
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 6px;
            padding: 8px 14px;
            background: rgba(255, 255, 255, 0.1);
            backdrop-filter: blur(8px);
            -webkit-backdrop-filter: blur(8px);
            border: 1px solid rgba(255, 255, 255, 0.15);
            border-radius: 100px;
            color: var(--text-primary);
            font-size: 13px;
            font-weight: 500;
            cursor: pointer;
            transition: all 0.2s ease;
        }

        .share-btn:hover {
            background: rgba(255, 255, 255, 0.15);
        }

        .share-btn:active {
            transform: scale(0.95);
        }

        .share-btn svg {
            width: 16px;
            height: 16px;
            fill: currentColor;
        }

        /* Temperature Tint Overlay */
        .hero-tint {
            position: absolute;
            inset: 0;
            pointer-events: none;
            z-index: 2;
            transition: background 0.5s ease;
        }

        .hero-tint.freezing {
            background: linear-gradient(to bottom, rgba(139, 92, 246, 0.2) 0%, rgba(139, 92, 246, 0.08) 50%, transparent 100%);
        }
        .hero-tint.cold {
            background: linear-gradient(to bottom, rgba(99, 102, 241, 0.2) 0%, rgba(99, 102, 241, 0.08) 50%, transparent 100%);
        }
        .hero-tint.cool {
            background: linear-gradient(to bottom, rgba(59, 130, 246, 0.15) 0%, rgba(59, 130, 246, 0.05) 50%, transparent 100%);
        }
        .hero-tint.mild {
            background: linear-gradient(to bottom, rgba(16, 185, 129, 0.15) 0%, rgba(16, 185, 129, 0.05) 50%, transparent 100%);
        }
        .hero-tint.warm {
            background: linear-gradient(to bottom, rgba(245, 158, 11, 0.15) 0%, rgba(245, 158, 11, 0.05) 50%, transparent 100%);
        }
        .hero-tint.hot {
            background: linear-gradient(to bottom, rgba(249, 115, 22, 0.18) 0%, rgba(249, 115, 22, 0.06) 50%, transparent 100%);
        }

        /* 6-Stop Gradient Overlay */
        .hero-gradient {
            position: absolute;
            inset: 0;
            background: linear-gradient(
                to bottom,
                rgba(10, 10, 10, 0.6) 0%,
                rgba(10, 10, 10, 0.3) 15%,
                transparent 40%,
                transparent 60%,
                rgba(10, 10, 10, 0.5) 85%,
                rgba(10, 10, 10, 0.95) 100%
            );
            pointer-events: none;
            z-index: 3;
        }

        /* Hero Header Controls */
        .hero-header {
            position: absolute;
            top: 0;
            left: 0;
            right: 0;
            padding: 16px 20px;
            padding-top: max(16px, env(safe-area-inset-top));
            display: flex;
            justify-content: space-between;
            align-items: flex-start;
            z-index: 10;
        }

        /* Glass Morphism */
        .glass-btn {
            display: flex;
            align-items: center;
            gap: 8px;
            padding: 10px 16px;
            background: var(--glass-bg);
            backdrop-filter: blur(var(--glass-blur));
            -webkit-backdrop-filter: blur(var(--glass-blur));
            border: 1px solid var(--glass-border);
            border-radius: 100px;
            color: var(--text-primary);
            font-family: inherit;
            font-size: 14px;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.2s ease;
        }

        .glass-btn:hover {
            background: rgba(255, 255, 255, 0.18);
        }

        .glass-btn:active {
            transform: scale(0.98);
        }

        .glass-btn svg {
            width: 16px;
            height: 16px;
            fill: currentColor;
        }

        .glass-btn-icon {
            width: 44px;
            height: 44px;
            padding: 0;
            border-radius: 50%;
            justify-content: center;
        }

        .glass-btn-icon svg {
            width: 20px;
            height: 20px;
        }

        /* Hero Weather Content */
        .hero-content {
            position: absolute;
            bottom: 0;
            left: 0;
            right: 0;
            padding: 0 20px 24px;
            z-index: 5;
        }

        /* Date/Time Pill */
        .datetime-pill {
            display: inline-flex;
            align-items: center;
            background: var(--glass-bg);
            backdrop-filter: blur(12px);
            -webkit-backdrop-filter: blur(12px);
            border: 1px solid var(--glass-border);
            border-radius: 100px;
            padding: 4px;
            margin-bottom: 16px;
            gap: 2px;
        }

        .datetime-pill-section {
            display: flex;
            align-items: center;
            gap: 6px;
            padding: 8px 14px;
            border-radius: 100px;
            cursor: pointer;
            transition: background 0.2s ease;
        }

        .datetime-pill-section:hover {
            background: rgba(255, 255, 255, 0.1);
        }

        .datetime-pill-section.active {
            background: rgba(255, 255, 255, 0.15);
        }

        .datetime-pill-section svg {
            width: 14px;
            height: 14px;
            opacity: 0.7;
        }

        .datetime-pill-section span {
            font-size: 13px;
            font-weight: 600;
        }

        .datetime-pill-divider {
            width: 1px;
            height: 20px;
            background: rgba(255, 255, 255, 0.2);
        }

        /* Temperature Display */
        .temp-display {
            margin-bottom: 8px;
        }

        .temp-feels-label {
            font-size: 14px;
            font-weight: 500;
            color: var(--text-secondary);
            margin-bottom: 4px;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }

        .temp-main {
            font-family: 'Bebas Neue', sans-serif;
            font-size: 96px;
            line-height: 0.85;
            letter-spacing: -0.02em;
            text-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
            cursor: pointer;
            transition: color 0.3s ease;
        }

        .temp-main.freezing { color: var(--temp-freezing); }
        .temp-main.cold { color: var(--temp-cold); }
        .temp-main.cool { color: var(--temp-cool); }
        .temp-main.mild { color: var(--temp-mild); }
        .temp-main.warm { color: var(--temp-warm); }
        .temp-main.hot { color: var(--temp-hot); }

        .temp-actual {
            font-size: 14px;
            color: var(--text-secondary);
            margin-top: 8px;
        }

        .temp-actual span {
            font-weight: 600;
            color: var(--text-primary);
        }

        /* Weather Pills */
        .weather-pills {
            display: flex;
            flex-wrap: wrap;
            gap: 8px;
            margin-top: 16px;
        }

        .weather-pill {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 8px 12px;
            background: rgba(255, 255, 255, 0.1);
            backdrop-filter: blur(8px);
            -webkit-backdrop-filter: blur(8px);
            border-radius: 100px;
            font-size: 13px;
            font-weight: 500;
        }

        .weather-pill svg {
            width: 14px;
            height: 14px;
            opacity: 0.8;
        }

        /* ========== OUTFIT SECTION ========== */
        .outfit-section {
            padding: 24px 20px;
            padding-bottom: 60px;
        }

        .outfit-section-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 16px;
        }

        .outfit-section-title {
            font-family: 'Bebas Neue', sans-serif;
            font-size: 28px;
            letter-spacing: 0.02em;
        }

        .outfit-grid {
            display: flex;
            flex-direction: column;
            gap: 10px;
        }

        .outfit-card {
            display: flex;
            align-items: center;
            gap: 14px;
            padding: 14px;
            background: var(--bg-card);
            border: none;
            border-radius: 16px;
            cursor: pointer;
            transition: all 0.2s ease;
            opacity: 0;
            animation: slideUp 0.4s ease forwards;
        }

        .outfit-card:nth-child(1) { animation-delay: 0.05s; }
        .outfit-card:nth-child(2) { animation-delay: 0.10s; }
        .outfit-card:nth-child(3) { animation-delay: 0.15s; }
        .outfit-card:nth-child(4) { animation-delay: 0.20s; }
        .outfit-card:nth-child(5) { animation-delay: 0.25s; }
        .outfit-card:nth-child(6) { animation-delay: 0.30s; }
        .outfit-card:nth-child(7) { animation-delay: 0.35s; }
        .outfit-card:nth-child(8) { animation-delay: 0.40s; }

        .outfit-card:hover {
            background: var(--bg-card-light);
            transform: translateX(4px);
        }

        .outfit-card:active {
            transform: scale(0.98);
        }

        .outfit-icon {
            width: 48px;
            height: 48px;
            display: flex;
            align-items: center;
            justify-content: center;
            border-radius: 14px;
            flex-shrink: 0;
        }

        .outfit-icon svg {
            width: 24px;
            height: 24px;
        }

        .outfit-icon.cat-head { background: rgba(46, 125, 50, 0.15); color: var(--cat-head); }
        .outfit-icon.cat-top { background: rgba(0, 121, 107, 0.15); color: var(--cat-top); }
        .outfit-icon.cat-bottom { background: rgba(21, 101, 192, 0.15); color: var(--cat-bottom); }
        .outfit-icon.cat-hands { background: rgba(94, 53, 177, 0.15); color: var(--cat-hands); }
        .outfit-icon.cat-accessories { background: rgba(230, 81, 0, 0.15); color: var(--cat-accessories); }

        .outfit-info {
            flex: 1;
            min-width: 0;
        }

        .outfit-name {
            font-weight: 600;
            font-size: 15px;
            margin-bottom: 2px;
            color: var(--text-primary);
        }

        .outfit-category {
            font-size: 13px;
            color: var(--text-secondary);
            text-transform: capitalize;
        }

        .outfit-action {
            color: var(--text-muted);
        }

        .outfit-action svg {
            width: 18px;
            height: 18px;
        }

        /* ========== TIP CARD ========== */
        .tip-card {
            margin-top: 20px;
            padding: 18px;
            background: linear-gradient(135deg, rgba(0, 121, 107, 0.15) 0%, rgba(0, 121, 107, 0.05) 100%);
            border: 1px solid rgba(0, 121, 107, 0.2);
            border-radius: 16px;
            opacity: 0;
            animation: slideUp 0.4s ease forwards;
            animation-delay: 0.4s;
        }

        .tip-header {
            display: flex;
            align-items: center;
            gap: 10px;
            margin-bottom: 10px;
        }

        .tip-icon {
            width: 32px;
            height: 32px;
            display: flex;
            align-items: center;
            justify-content: center;
            background: var(--primary);
            border-radius: 10px;
            color: white;
        }

        .tip-icon svg {
            width: 18px;
            height: 18px;
        }

        .tip-label {
            font-weight: 700;
            font-size: 14px;
            color: var(--primary-light);
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }

        .tip-text {
            font-size: 14px;
            line-height: 1.5;
            color: var(--text-secondary);
        }

        /* Shop Button */
        .shop-btn {
            display: flex;
            align-items: center;
            gap: 6px;
            padding: 10px 18px;
            background: linear-gradient(135deg, var(--primary) 0%, var(--primary-light) 100%);
            border: none;
            border-radius: 100px;
            color: white;
            font-family: inherit;
            font-size: 13px;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.2s ease;
            box-shadow: 0 4px 16px var(--primary-glow);
        }

        .shop-btn:hover {
            transform: translateY(-2px);
            box-shadow: 0 6px 24px var(--primary-glow);
        }

        .shop-btn svg {
            width: 16px;
            height: 16px;
        }

        .container {
            max-width: 480px;
            margin: 0 auto;
            background: var(--bg-dark);
        }
        
        /* ========== HEADER ========== */
        .header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 12px 0 20px;
        }
        
        .logo {
            font-size: 24px;
            font-weight: 800;
            color: var(--primary);
            letter-spacing: -0.5px;
        }
        
        .logo span { color: var(--text); }
        
        .header-location {
            display: flex;
            align-items: center;
            gap: 4px;
            padding: 8px 12px;
            background: var(--surface);
            border-radius: 20px;
            color: var(--text-secondary);
            font-size: 13px;
            font-weight: 500;
            cursor: pointer;
            transition: all 0.2s;
            box-shadow: 0 1px 3px rgba(0,0,0,0.08);
        }
        
        .header-location:hover { background: var(--surface-dim); }
        
        .header-location svg {
            width: 14px;
            height: 14px;
            fill: var(--primary);
        }
        
        /* ========== WEATHER CARD ========== */
        .weather-card {
            background: var(--surface);
            border-radius: 24px;
            padding: 28px 24px;
            margin-bottom: 16px;
            box-shadow: 0 2px 12px rgba(0,0,0,0.06);
            transition: transform 0.2s;
        }
        
        .weather-card:active { transform: scale(0.98); }
        
        .weather-icon { 
            font-size: 56px; 
            text-align: center;
            line-height: 1;
        }
        
        .weather-temp-label {
            text-align: center;
            color: var(--text-muted);
            font-size: 12px;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 1px;
            margin-top: 12px;
        }
        
        .weather-temp {
            font-size: 72px;
            font-weight: 700;
            text-align: center;
            line-height: 1;
            margin: 4px 0;
            cursor: pointer;
        }
        
        .weather-actual {
            text-align: center;
            color: var(--text-secondary);
            font-size: 14px;
            margin-bottom: 20px;
        }
        
        .weather-actual span {
            color: var(--text);
            font-weight: 600;
        }
        
        .weather-details {
            display: flex;
            justify-content: center;
            gap: 28px;
            flex-wrap: wrap;
        }
        
        .weather-detail {
            display: flex;
            align-items: center;
            gap: 6px;
            font-size: 14px;
            color: var(--text-secondary);
        }
        
        .weather-detail-icon { font-size: 16px; }
        
        .unit-hint {
            text-align: center;
            font-size: 11px;
            color: var(--text-muted);
            margin-top: 16px;
        }
        
        /* ========== DATE/TIME SELECTOR ========== */
        .datetime-card {
            background: var(--surface);
            border-radius: 16px;
            padding: 12px 16px;
            margin-bottom: 20px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            box-shadow: 0 1px 4px rgba(0,0,0,0.04);
        }
        
        .datetime-btn {
            width: 40px;
            height: 40px;
            border-radius: 50%;
            border: none;
            background: var(--surface-dim);
            color: var(--text);
            font-size: 16px;
            cursor: pointer;
            transition: all 0.2s;
            display: flex;
            align-items: center;
            justify-content: center;
        }
        
        .datetime-btn:hover { background: var(--primary-light); color: white; }
        .datetime-btn:active { background: var(--primary); color: white; }
        
        .datetime-info { text-align: center; flex: 1; }
        
        .datetime-selectors {
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 6px;
        }
        
        .datetime-date, .datetime-time {
            cursor: pointer;
            padding: 6px 12px;
            border-radius: 8px;
            transition: background 0.2s;
        }
        
        .datetime-date:hover, .datetime-time:hover { background: var(--surface-dim); }
        
        .datetime-date {
            font-weight: 600;
            font-size: 15px;
            color: var(--text);
        }
        
        .datetime-time {
            font-size: 14px;
            color: var(--text-secondary);
        }
        
        .datetime-separator {
            color: var(--text-muted);
            font-size: 13px;
        }
        
        .datetime-reset {
            font-size: 11px;
            color: var(--primary);
            margin-top: 4px;
            cursor: pointer;
            font-weight: 500;
        }
        
        /* ========== OUTFIT HEADER ========== */
        .outfit-header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-bottom: 12px;
            gap: 12px;
        }
        
        .section-title {
            font-size: 13px;
            font-weight: 700;
            color: var(--primary);
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }
        
        .outfit-controls {
            display: flex;
            align-items: center;
            gap: 12px;
        }
        
        /* Outfit Controls */
        .outfit-controls {
            display: flex;
            align-items: center;
            gap: 10px;
        }

        /* Gender Toggle */
        .gender-toggle {
            display: flex;
            align-items: center;
            background: var(--bg-card-light);
            border-radius: 22px;
            padding: 3px;
            gap: 2px;
        }

        .gender-opt {
            display: flex;
            align-items: center;
            justify-content: center;
            width: 36px;
            height: 32px;
            font-size: 14px;
            cursor: pointer;
            border-radius: 16px;
            transition: all 0.2s;
            color: var(--text-muted);
        }

        .gender-opt svg {
            width: 16px;
            height: 16px;
            fill: currentColor;
        }

        .gender-opt:hover {
            color: var(--text-secondary);
        }

        .gender-opt.active {
            background: var(--primary);
            color: white;
        }

        .gender-opt.center {
            font-size: 12px;
            font-weight: 600;
            padding: 0 12px;
            min-width: 50px;
        }
        
        /* Shop All Button */
        .shop-all-btn {
            display: flex;
            align-items: center;
            gap: 4px;
            padding: 8px 14px;
            background: var(--primary);
            color: white;
            border: none;
            border-radius: 20px;
            font-size: 12px;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.2s;
        }
        
        .shop-all-btn:hover { background: var(--primary-dark); }
        
        /* ========== CLOTHING ITEMS ========== */
        .clothing-item {
            background: var(--surface);
            border-radius: 14px;
            padding: 14px 16px;
            margin-bottom: 10px;
            display: flex;
            align-items: center;
            gap: 14px;
            cursor: pointer;
            transition: all 0.2s;
            box-shadow: 0 1px 3px rgba(0,0,0,0.04);
        }
        
        .clothing-item:hover { 
            transform: translateX(4px);
            box-shadow: 0 2px 8px rgba(0,0,0,0.08);
        }
        
        .clothing-icon {
            width: 44px;
            height: 44px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 22px;
            flex-shrink: 0;
        }
        
        .clothing-icon.top { background: rgba(0, 121, 107, 0.12); }
        .clothing-icon.bottom { background: rgba(21, 101, 192, 0.12); }
        .clothing-icon.head { background: rgba(46, 125, 50, 0.12); }
        .clothing-icon.hands { background: rgba(94, 53, 177, 0.12); }
        .clothing-icon.accessories { background: rgba(230, 81, 0, 0.12); }
        
        .clothing-info { flex: 1; min-width: 0; }
        
        .clothing-name {
            font-weight: 600;
            font-size: 14px;
            color: var(--text);
            margin-bottom: 2px;
        }
        
        .clothing-desc {
            font-size: 12px;
            color: var(--text-secondary);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        
        .clothing-chevron {
            color: var(--text-muted);
            font-size: 18px;
            opacity: 0.5;
        }
        
        /* ========== TIPS ========== */
        .tips-section { margin-top: 24px; }
        
        .tip {
            background: rgba(0, 121, 107, 0.06);
            border-left: 3px solid var(--primary);
            border-radius: 0 12px 12px 0;
            padding: 12px 14px;
            margin-bottom: 10px;
            font-size: 13px;
            line-height: 1.5;
            color: var(--text);
        }
        
        /* ========== STATES ========== */
        .state-screen {
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            min-height: 100vh;
            min-height: 100dvh;
            text-align: center;
            padding: 24px;
            background: var(--bg-dark);
        }

        .state-icon { font-size: 64px; margin-bottom: 20px; }

        .state-title {
            font-family: 'Bebas Neue', sans-serif;
            font-size: 28px;
            letter-spacing: 0.02em;
            margin-bottom: 8px;
            color: var(--text-primary);
        }

        .state-desc {
            color: var(--text-secondary);
            font-size: 14px;
            margin-bottom: 28px;
            max-width: 280px;
            line-height: 1.5;
        }

        .btn {
            background: linear-gradient(135deg, var(--primary) 0%, var(--primary-light) 100%);
            color: white;
            border: none;
            padding: 14px 32px;
            border-radius: 100px;
            font-size: 15px;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.2s;
            box-shadow: 0 4px 16px var(--primary-glow);
        }

        .btn:hover {
            transform: translateY(-2px);
            box-shadow: 0 6px 24px var(--primary-glow);
        }

        .btn:active {
            transform: scale(0.98);
        }

        .btn-secondary {
            background: var(--bg-card-light);
            color: var(--text-primary);
            margin-top: 12px;
            box-shadow: none;
        }

        .btn-secondary:hover {
            background: rgba(255, 255, 255, 0.15);
            transform: none;
            box-shadow: none;
        }

        /* Spinner */
        .spinner {
            width: 48px;
            height: 48px;
            border: 4px solid var(--bg-card-light);
            border-top-color: var(--primary);
            border-radius: 50%;
            animation: spin 1s linear infinite;
            margin-bottom: 20px;
        }

        @keyframes spin { to { transform: rotate(360deg); } }
        
        /* ========== PICKER OVERLAYS ========== */
        .picker-overlay {
            position: fixed;
            inset: 0;
            background: rgba(0, 0, 0, 0.7);
            display: none;
            align-items: center;
            justify-content: center;
            z-index: 50;
            padding: 20px;
            backdrop-filter: blur(8px);
            -webkit-backdrop-filter: blur(8px);
        }

        .picker-overlay.active { display: flex; }

        .picker-container {
            background: var(--bg-card);
            border: 1px solid var(--border);
            border-radius: 24px;
            padding: 24px;
            width: 100%;
            max-width: 340px;
            max-height: 80vh;
            overflow-y: auto;
            box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
        }

        .picker-title {
            font-family: 'Bebas Neue', sans-serif;
            font-size: 24px;
            letter-spacing: 0.02em;
            text-align: center;
            margin-bottom: 20px;
            color: var(--text-primary);
        }

        .picker-grid {
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: 10px;
        }

        .picker-grid.dates { grid-template-columns: repeat(2, 1fr); }

        .picker-item {
            padding: 14px 10px;
            background: var(--bg-card-light);
            border-radius: 12px;
            text-align: center;
            cursor: pointer;
            transition: all 0.2s;
            font-size: 14px;
            font-weight: 500;
            color: var(--text-primary);
        }

        .picker-item:hover { background: rgba(0, 121, 107, 0.3); }
        .picker-item.active { background: var(--primary); color: white; }

        .picker-sublabel {
            font-size: 11px;
            opacity: 0.7;
            margin-top: 4px;
        }

        .picker-cancel {
            margin-top: 20px;
            width: 100%;
            padding: 14px;
            background: var(--bg-card-light);
            border: none;
            border-radius: 12px;
            color: var(--text-primary);
            font-size: 14px;
            font-weight: 600;
            cursor: pointer;
            transition: background 0.2s;
        }

        .picker-cancel:hover {
            background: rgba(255, 255, 255, 0.1);
        }

        /* ========== SETTINGS MODAL ========== */
        .modal-overlay {
            position: fixed;
            inset: 0;
            background: rgba(0, 0, 0, 0.7);
            display: none;
            align-items: flex-end;
            justify-content: center;
            z-index: 100;
            backdrop-filter: blur(8px);
            -webkit-backdrop-filter: blur(8px);
        }

        .modal-overlay.active { display: flex; }

        .modal {
            background: var(--bg-card);
            border: 1px solid var(--border);
            border-radius: 24px 24px 0 0;
            padding: 24px;
            width: 100%;
            max-width: 500px;
            max-height: 85vh;
            overflow-y: auto;
        }

        .modal-handle {
            width: 40px;
            height: 4px;
            background: rgba(255, 255, 255, 0.3);
            border-radius: 2px;
            margin: 0 auto 16px;
            cursor: grab;
        }

        .modal-handle::before {
            content: '';
            display: block;
            width: 100%;
            height: 40px;
            margin-top: -18px;
        }

        .modal-title {
            font-family: 'Bebas Neue', sans-serif;
            font-size: 24px;
            letter-spacing: 0.02em;
            margin-bottom: 24px;
            color: var(--text-primary);
        }
        
        .setting-row {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 16px 0;
            border-bottom: 1px solid var(--border);
        }
        
        .setting-label {
            font-size: 15px;
            font-weight: 500;
            color: var(--text-primary);
        }

        .setting-sublabel {
            font-size: 12px;
            color: var(--text-muted);
            margin-top: 2px;
        }

        .toggle {
            width: 52px;
            height: 28px;
            background: var(--bg-card-light);
            border-radius: 14px;
            position: relative;
            cursor: pointer;
            transition: background 0.2s;
        }

        .toggle.active { background: var(--primary); }

        .toggle::after {
            content: '';
            position: absolute;
            width: 22px;
            height: 22px;
            background: white;
            border-radius: 50%;
            top: 3px;
            left: 3px;
            transition: transform 0.2s;
            box-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
        }

        .toggle.active::after { transform: translateX(24px); }

        /* Comfort Slider */
        .comfort-selector {
            display: flex;
            gap: 6px;
            margin-top: 16px;
        }

        .comfort-opt {
            flex: 1;
            padding: 10px 6px;
            background: var(--bg-card-light);
            border-radius: 10px;
            text-align: center;
            cursor: pointer;
            font-size: 11px;
            font-weight: 500;
            transition: all 0.2s;
            color: var(--text-primary);
        }

        .comfort-opt:hover {
            background: rgba(255, 255, 255, 0.1);
        }

        .comfort-opt.active {
            background: var(--primary);
            color: white;
        }

        /* Unit Selector (°F / °C) */
        .unit-selector {
            display: flex;
            background: var(--bg-card-light);
            border-radius: 10px;
            padding: 3px;
            gap: 2px;
        }

        .unit-opt {
            padding: 8px 16px;
            border-radius: 8px;
            font-size: 14px;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.2s;
            color: var(--text-muted);
        }

        .unit-opt:hover {
            color: var(--text-secondary);
        }

        .unit-opt.active {
            background: var(--primary);
            color: white;
        }

        /* ========== ONBOARDING MODAL ========== */
        .onboarding-overlay {
            position: fixed;
            inset: 0;
            background: rgba(0, 0, 0, 0.85);
            display: none;
            align-items: center;
            justify-content: center;
            z-index: 200;
            padding: 20px;
            backdrop-filter: blur(20px);
            -webkit-backdrop-filter: blur(20px);
        }

        .onboarding-overlay.active {
            display: flex;
            animation: fadeIn 0.3s ease;
        }

        .onboarding-card {
            background: var(--bg-card);
            border: 1px solid var(--border);
            border-radius: 28px;
            padding: 32px 24px;
            width: 100%;
            max-width: 380px;
            max-height: 90vh;
            overflow-y: auto;
        }

        .onboarding-header {
            text-align: center;
            margin-bottom: 28px;
        }

        .onboarding-logo {
            font-size: 28px;
            font-weight: 800;
            color: var(--primary);
            letter-spacing: -0.5px;
            margin-bottom: 16px;
        }

        .onboarding-logo span {
            color: var(--text-primary);
        }

        .onboarding-title {
            font-family: 'Bebas Neue', sans-serif;
            font-size: 32px;
            letter-spacing: 0.02em;
            color: var(--text-primary);
            margin: 0 0 8px 0;
        }

        .onboarding-subtitle {
            font-size: 14px;
            color: var(--text-secondary);
            line-height: 1.5;
            margin: 0;
        }

        .onboarding-section {
            margin-bottom: 24px;
        }

        .onboarding-label {
            font-size: 15px;
            font-weight: 600;
            color: var(--text-primary);
            margin-bottom: 4px;
        }

        .onboarding-sublabel {
            font-size: 12px;
            color: var(--text-muted);
            margin-bottom: 12px;
        }

        .onboarding-section .unit-selector {
            width: 100%;
            justify-content: center;
        }

        .onboarding-section .unit-opt {
            flex: 1;
            text-align: center;
        }

        .onboarding-section .gender-toggle {
            width: 100%;
            justify-content: center;
        }

        .onboarding-section .gender-opt {
            flex: 1;
            max-width: none;
            text-align: center;
            padding: 8px 16px;
        }

        .onboarding-section .comfort-selector {
            width: 100%;
        }

        .onboarding-cta {
            width: 100%;
            margin-top: 8px;
            padding: 16px 32px;
            font-size: 16px;
        }
        
        /* ========== SHOP MODAL ========== */
        .shop-modal {
            background: var(--bg-card);
            border: 1px solid var(--border);
            border-radius: 24px 24px 0 0;
            padding: 24px;
            width: 100%;
            max-width: 500px;
            max-height: 85vh;
            overflow-y: auto;
        }

        .shop-modal-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 16px;
        }

        .shop-modal-header .modal-title {
            margin-bottom: 0;
        }

        .shop-all-btn {
            background: var(--primary);
            color: white;
            border: none;
            padding: 8px 16px;
            border-radius: 20px;
            font-size: 13px;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.2s;
        }

        .shop-all-btn:hover {
            background: var(--primary-dark);
            transform: scale(1.02);
        }

        .shop-item {
            display: flex;
            align-items: center;
            gap: 12px;
            padding: 14px;
            background: var(--bg-card-light);
            border-radius: 14px;
            margin-bottom: 10px;
            cursor: pointer;
            transition: all 0.2s;
            color: var(--text-primary);
        }

        .shop-item:hover {
            background: rgba(0, 121, 107, 0.3);
            transform: translateX(4px);
        }

        .shop-item:hover .shop-item-desc { color: var(--text-secondary); }

        .shop-item-icon {
            font-size: 24px;
            width: 40px;
            text-align: center;
        }

        .shop-item-info { flex: 1; }

        .shop-item-name {
            font-weight: 600;
            font-size: 14px;
            color: var(--text-primary);
        }

        .shop-item-desc {
            font-size: 12px;
            color: var(--text-secondary);
        }

        .shop-item-arrow {
            font-size: 16px;
            color: var(--text-muted);
        }

        .ftc-disclosure {
            background: rgba(255, 255, 255, 0.05);
            border-radius: 12px;
            padding: 14px;
            margin-top: 16px;
            font-size: 11px;
            color: var(--text-muted);
            line-height: 1.5;
            border: 1px solid var(--border);
        }

        .ftc-disclosure strong { color: var(--text-secondary); }
        
        /* ========== TEMP COLORS ========== */
        .temp-hot { color: var(--hot); }
        .temp-warm { color: var(--warm); }
        .temp-mild { color: var(--mild); }
        .temp-cool { color: var(--cool); }
        .temp-cold { color: var(--cold); }
        .temp-freezing { color: var(--freezing); }
        
        /* ========== FOOTER ========== */
        .footer {
            text-align: center;
            padding: 24px 0;
            color: var(--text-muted);
            font-size: 12px;
        }
        
        .footer a {
            color: var(--primary);
            text-decoration: none;
        }
        
        /* ========== LOCATION HELP ========== */
        .location-help {
            background: var(--surface);
            border-radius: 16px;
            padding: 16px;
            margin: 16px 0;
            text-align: left;
            max-width: 320px;
            width: 100%;
        }
        
        .help-title {
            font-size: 15px;
            font-weight: 600;
            margin-bottom: 12px;
            color: var(--text);
        }
        
        .help-steps {
            display: flex;
            flex-direction: column;
            gap: 10px;
        }
        
        .help-step {
            display: flex;
            align-items: center;
            gap: 10px;
            font-size: 14px;
            color: var(--text-secondary);
        }
        
        .step-num {
            width: 24px;
            height: 24px;
            background: var(--primary);
            color: white;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 12px;
            font-weight: 600;
            flex-shrink: 0;
        }
        
        .help-note {
            margin-top: 12px;
            padding-top: 12px;
            border-top: 1px solid var(--surface-light);
            font-size: 12px;
            color: var(--text-secondary);
            line-height: 1.5;
        }
        
        /* ========== LOCATION INPUT ========== */
        .location-input-section {
            margin-top: 20px;
            padding-top: 20px;
            border-top: 1px solid var(--border);
            width: 100%;
            max-width: 320px;
        }

        .location-input-title {
            font-size: 14px;
            font-weight: 600;
            color: var(--text-primary);
            margin-bottom: 12px;
            text-align: center;
        }

        .location-input-wrapper {
            display: flex;
            gap: 8px;
        }

        .location-input {
            flex: 1;
            padding: 12px 16px;
            border: 1px solid var(--border);
            border-radius: 12px;
            font-size: 16px;
            font-family: inherit;
            outline: none;
            transition: all 0.2s;
            background: var(--bg-card);
            color: var(--text-primary);
        }

        .location-input:focus {
            border-color: var(--primary);
            background: var(--bg-card-light);
        }

        .location-input::placeholder {
            color: var(--text-muted);
        }

        .location-submit {
            padding: 12px 16px;
            background: var(--primary);
            color: white;
            border: none;
            border-radius: 12px;
            font-size: 14px;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.2s;
            white-space: nowrap;
        }

        .location-submit:hover {
            background: var(--primary-light);
        }

        .location-submit:disabled {
            background: var(--text-muted);
            cursor: not-allowed;
        }
        
        /* ========== LOCATION SETUP SCREEN ========== */
        .location-setup-screen {
            min-height: 100vh;
            min-height: 100dvh;
            display: flex;
            flex-direction: column;
            padding: 40px 20px;
            max-width: 400px;
            margin: 0 auto;
            background: var(--bg-dark);
        }

        .location-setup-header {
            text-align: center;
            margin-bottom: 32px;
        }

        .location-setup-icon {
            font-size: 56px;
            margin-bottom: 16px;
        }

        .location-setup-title {
            font-family: 'Bebas Neue', sans-serif;
            font-size: 28px;
            letter-spacing: 0.02em;
            color: var(--text-primary);
            margin: 0 0 8px 0;
        }

        .location-setup-desc {
            font-size: 15px;
            color: var(--text-secondary);
            line-height: 1.4;
            margin: 0;
        }

        .location-setup-options {
            flex: 1;
        }

        .location-setup-section {
            margin-bottom: 16px;
        }

        .location-setup-divider {
            display: flex;
            align-items: center;
            gap: 16px;
            margin: 24px 0;
            color: var(--text-muted);
            font-size: 13px;
        }

        .location-setup-divider::before,
        .location-setup-divider::after {
            content: '';
            flex: 1;
            height: 1px;
            background: var(--border);
        }

        .location-gps-button {
            display: flex;
            align-items: center;
            gap: 14px;
            width: 100%;
            padding: 16px;
            background: var(--bg-card);
            border: 1px solid var(--border);
            border-radius: 16px;
            cursor: pointer;
            transition: all 0.2s;
            text-align: left;
            color: var(--text-primary);
        }

        .location-gps-button:hover {
            border-color: var(--primary);
            background: rgba(0, 121, 107, 0.15);
        }

        .location-gps-button:active {
            transform: scale(0.98);
        }

        .location-gps-icon {
            width: 48px;
            height: 48px;
            background: var(--primary);
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            color: white;
            flex-shrink: 0;
        }

        .location-gps-text {
            flex: 1;
            display: flex;
            flex-direction: column;
            gap: 2px;
        }

        .location-gps-title {
            font-size: 16px;
            font-weight: 600;
            color: var(--text-primary);
        }

        .location-gps-subtitle {
            font-size: 13px;
            color: var(--text-secondary);
        }

        .location-gps-help {
            margin-top: 24px;
            padding: 20px;
            background: var(--bg-card);
            border-radius: 16px;
            border: 1px solid var(--border);
        }
        
        .location-gps-help .help-title {
            font-size: 16px;
            font-weight: 700;
            color: var(--text);
            margin-bottom: 16px;
            display: flex;
            align-items: center;
            gap: 8px;
        }
        
        /* Location Search Results */
        .location-results {
            margin-top: 12px;
            max-height: 200px;
            overflow-y: auto;
        }

        .location-result {
            padding: 12px;
            background: var(--bg-card-light);
            border-radius: 10px;
            margin-bottom: 8px;
            cursor: pointer;
            transition: all 0.2s;
            font-size: 14px;
            color: var(--text-primary);
        }

        .location-result:hover {
            background: rgba(0, 121, 107, 0.2);
        }

        .location-result-name {
            font-weight: 500;
        }

        .location-result-detail {
            font-size: 12px;
            color: var(--text-secondary);
            margin-top: 2px;
        }

        /* ========== LOCATION MODAL ========== */
        .location-modal {
            background: var(--bg-card);
            border: 1px solid var(--border);
            border-radius: 24px 24px 0 0;
            padding: 24px;
            width: 100%;
            max-width: 480px;
            max-height: 85vh;
            overflow-y: auto;
        }

        .location-option {
            display: flex;
            align-items: center;
            gap: 14px;
            padding: 16px;
            background: var(--bg-card-light);
            border-radius: 14px;
            margin-bottom: 12px;
            cursor: pointer;
            transition: all 0.2s;
            color: var(--text-primary);
        }

        .location-option:hover {
            background: rgba(255, 255, 255, 0.1);
        }

        .location-option.active {
            background: rgba(0, 121, 107, 0.2);
            border: 2px solid var(--primary);
        }

        .location-option-icon {
            width: 44px;
            height: 44px;
            background: var(--primary);
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 20px;
            flex-shrink: 0;
            color: white;
        }

        .location-option-icon.secondary {
            background: var(--cat-bottom);
        }

        .location-option-text {
            flex: 1;
        }

        .location-option-title {
            font-weight: 600;
            font-size: 15px;
            color: var(--text-primary);
        }

        .location-option-desc {
            font-size: 13px;
            color: var(--text-secondary);
            margin-top: 2px;
        }
        
        .location-divider {
            display: flex;
            align-items: center;
            gap: 12px;
            margin: 20px 0;
            color: var(--text-muted);
            font-size: 13px;
        }
        
        .location-divider::before,
        .location-divider::after {
            content: '';
            flex: 1;
            height: 1px;
            background: var(--border);
        }
        
        .manual-search-section {
            margin-top: 16px;
        }

        .search-input-wrapper {
            position: relative;
        }

        .search-input {
            width: 100%;
            padding: 14px 16px;
            padding-left: 44px;
            border: 1px solid var(--border);
            border-radius: 14px;
            font-size: 16px;
            font-family: inherit;
            outline: none;
            transition: all 0.2s;
            background: var(--bg-card-light);
            color: var(--text-primary);
        }

        .search-input:focus {
            border-color: var(--primary);
            background: var(--bg-card);
        }

        .search-input::placeholder {
            color: var(--text-muted);
        }

        .search-icon {
            position: absolute;
            left: 14px;
            top: 50%;
            transform: translateY(-50%);
            font-size: 18px;
            color: var(--text-muted);
        }

        .search-spinner {
            position: absolute;
            right: 14px;
            top: 50%;
            transform: translateY(-50%);
            width: 20px;
            height: 20px;
            border: 2px solid var(--bg-card-light);
            border-top-color: var(--primary);
            border-radius: 50%;
            animation: spin 0.8s linear infinite;
        }

        .current-location-badge {
            display: inline-flex;
            align-items: center;
            gap: 4px;
            padding: 4px 10px;
            background: rgba(0, 121, 107, 0.2);
            color: var(--primary-light);
            border-radius: 20px;
            font-size: 11px;
            font-weight: 600;
            margin-left: 8px;
        }
        
        /* Header location with GPS icon */
        .header-location-icon {
            width: 32px;
            height: 32px;
            border-radius: 50%;
            background: var(--surface);
            border: none;
            display: flex;
            align-items: center;
            justify-content: center;
            cursor: pointer;
            transition: all 0.2s;
            box-shadow: 0 1px 3px rgba(0,0,0,0.08);
            margin-left: 8px;
        }
        
        .header-location-icon:hover {
            background: var(--surface-dim);
        }
        
        .header-location-icon svg {
            width: 18px;
            height: 18px;
            fill: var(--primary);
        }

        .header-settings-icon {
            width: 32px;
            height: 32px;
            border-radius: 50%;
            background: var(--surface);
            border: none;
            display: flex;
            align-items: center;
            justify-content: center;
            cursor: pointer;
            transition: all 0.2s;
        }

        .header-settings-icon:hover {
            background: var(--surface-dim);
        }

        .header-settings-icon svg {
            width: 18px;
            height: 18px;
            fill: var(--text-secondary);
        }

        /* ========== RESPONSIVE ========== */
        @media (max-width: 360px) {
            .gender-toggle { display: none; }
            .weather-temp { font-size: 60px; }
        }
    </style>
</head>
<body>
    <div class="container" id="app">
        <!-- Content injected by JS -->
    </div>
    
    <!-- Date Picker Overlay -->
    <div class="picker-overlay" id="datePicker">
        <div class="picker-container">
            <div class="picker-title">Select Date</div>
            <div class="picker-grid dates" id="datePickerGrid"></div>
            <button class="picker-cancel" onclick="closeDatePicker()">Cancel</button>
        </div>
    </div>
    
    <!-- Time Picker Overlay -->
    <div class="picker-overlay" id="timePickerOverlay">
        <div class="picker-container">
            <div class="picker-title">Select Time</div>
            <div class="picker-grid" id="timePickerGrid"></div>
            <button class="picker-cancel" onclick="closeTimePicker()">Cancel</button>
        </div>
    </div>
    
    <!-- Settings Modal -->
    <div class="modal-overlay" id="settingsModal" onclick="if(event.target===this)closeSettings()">
        <div class="modal">
            <div class="modal-handle"></div>
            <div class="modal-title">
                <svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24" style="vertical-align: middle; margin-right: 8px;"><path d="M19.14,12.94c0.04-0.3,0.06-0.61,0.06-0.94c0-0.32-0.02-0.64-0.07-0.94l2.03-1.58c0.18-0.14,0.23-0.41,0.12-0.61 l-1.92-3.32c-0.12-0.22-0.37-0.29-0.59-0.22l-2.39,0.96c-0.5-0.38-1.03-0.7-1.62-0.94L14.4,2.81c-0.04-0.24-0.24-0.41-0.48-0.41 h-3.84c-0.24,0-0.43,0.17-0.47,0.41L9.25,5.35C8.66,5.59,8.12,5.92,7.63,6.29L5.24,5.33c-0.22-0.08-0.47,0-0.59,0.22L2.74,8.87 C2.62,9.08,2.66,9.34,2.86,9.48l2.03,1.58C4.84,11.36,4.8,11.69,4.8,12s0.02,0.64,0.07,0.94l-2.03,1.58 c-0.18,0.14-0.23,0.41-0.12,0.61l1.92,3.32c0.12,0.22,0.37,0.29,0.59,0.22l2.39-0.96c0.5,0.38,1.03,0.7,1.62,0.94l0.36,2.54 c0.05,0.24,0.24,0.41,0.48,0.41h3.84c0.24,0,0.44-0.17,0.47-0.41l0.36-2.54c0.59-0.24,1.13-0.56,1.62-0.94l2.39,0.96 c0.22,0.08,0.47,0,0.59-0.22l1.92-3.32c0.12-0.22,0.07-0.47-0.12-0.61L19.14,12.94z M12,15.6c-1.98,0-3.6-1.62-3.6-3.6 s1.62-3.6,3.6-3.6s3.6,1.62,3.6,3.6S13.98,15.6,12,15.6z"/></svg>
                SETTINGS
            </div>

            <div class="setting-row">
                <div>
                    <div class="setting-label">Temperature Unit</div>
                    <div class="setting-sublabel">Choose your preferred unit</div>
                </div>
                <div class="unit-selector" id="unitSelector">
                    <span class="unit-opt" data-unit="f" onclick="setUnit(false)">°F</span>
                    <span class="unit-opt" data-unit="c" onclick="setUnit(true)">°C</span>
                </div>
            </div>

            <div class="setting-row" style="flex-direction:column;align-items:flex-start">
                <div class="setting-label">Body Temperature</div>
                <div class="setting-sublabel">Do you get cold easily or overheat quickly?</div>
                <div class="comfort-selector" id="comfortSelector"></div>
            </div>

            <div class="setting-row">
                <div>
                    <div class="setting-label">Fit Preference</div>
                    <div class="setting-sublabel">Product recommendations style</div>
                </div>
                <div class="gender-toggle" id="settingsGenderSelector">
                    <span class="gender-opt center" data-gender="male" onclick="setGender('male')">Male</span>
                    <span class="gender-opt center" data-gender="female" onclick="setGender('female')">Female</span>
                </div>
            </div>

            <button class="btn" style="width:100%;margin-top:24px" onclick="closeSettings()">Done</button>
        </div>
    </div>
    
    <!-- Shop Modal -->
    <div class="modal-overlay" id="shopModal" onclick="if(event.target===this)closeShop()">
        <div class="shop-modal">
            <div class="modal-handle"></div>
            <div class="shop-modal-header">
                <div class="modal-title">🛒 Shop Your Outfit</div>
                <button class="shop-all-btn" onclick="shopAll()">Shop All →</button>
            </div>
            <div id="shopItems"></div>
            <div class="ftc-disclosure">
                <strong>Affiliate Disclosure:</strong> As an Amazon Associate, RunWear earns from qualifying purchases. When you click these links, we may earn a small commission at no extra cost to you.
            </div>
            <button class="btn btn-secondary" style="width:100%;margin-top:16px" onclick="closeShop()">Close</button>
        </div>
    </div>
    
    <!-- Location Modal -->
    <div class="modal-overlay" id="locationModal" onclick="if(event.target===this)closeLocationModal()">
        <div class="location-modal">
            <div class="modal-handle"></div>
            <div class="modal-title">📍 Set Your Location</div>
            
            <div id="locationModalContent">
                <!-- Content injected by JS -->
            </div>
            
            <button class="btn btn-secondary" style="width:100%;margin-top:16px" onclick="closeLocationModal()">Cancel</button>
        </div>
    </div>

    <!-- Onboarding Modal -->
    <div class="onboarding-overlay" id="onboardingModal">
        <div class="onboarding-card">
            <div class="onboarding-header">
                <div class="onboarding-logo">Run<span>Wear</span></div>
                <h1 class="onboarding-title">Welcome! 👋</h1>
                <p class="onboarding-subtitle">Let's prepare your perfect run.<br>You can always change these later.</p>
            </div>

            <div class="onboarding-section">
                <div class="onboarding-label">Temperature</div>
                <div class="onboarding-sublabel">How do you measure the weather?</div>
                <div class="unit-selector" id="onboardingUnitSelector">
                    <span class="unit-opt" data-unit="f" onclick="setOnboardingUnit(false)">°F</span>
                    <span class="unit-opt" data-unit="c" onclick="setOnboardingUnit(true)">°C</span>
                </div>
            </div>

            <div class="onboarding-section">
                <div class="onboarding-label">Fit Preference</div>
                <div class="onboarding-sublabel">We'll tailor product recommendations</div>
                <div class="gender-toggle" id="onboardingGenderSelector">
                    <span class="gender-opt center" data-gender="male" onclick="setOnboardingGender('male')">Male</span>
                    <span class="gender-opt center" data-gender="female" onclick="setOnboardingGender('female')">Female</span>
                </div>
            </div>

            <div class="onboarding-section">
                <div class="onboarding-label">Do you get cold easily or overheat quickly?</div>
                <div class="onboarding-sublabel">We'll adjust outfit recommendations</div>
                <div class="comfort-selector" id="onboardingComfortSelector">
                    <span class="comfort-opt" data-comfort="-10" onclick="setOnboardingComfort(-10)">🥶</span>
                    <span class="comfort-opt" data-comfort="-5" onclick="setOnboardingComfort(-5)">Get cold</span>
                    <span class="comfort-opt" data-comfort="0" onclick="setOnboardingComfort(0)">Neither</span>
                    <span class="comfort-opt" data-comfort="5" onclick="setOnboardingComfort(5)">Overheat</span>
                    <span class="comfort-opt" data-comfort="10" onclick="setOnboardingComfort(10)">🥵</span>
                </div>
            </div>

            <button class="btn onboarding-cta" onclick="completeOnboarding()">
                Let's Go! 🏃
            </button>
        </div>
    </div>

    <script>
        // ============ STATE ============
        let state = {
            loading: true,
            error: null,
            errorCode: null,
            weather: null,
            outfit: null,
            location: null,
            locationName: 'Getting location...',
            locationSource: localStorage.getItem('locationSource') || 'auto', // 'auto' or 'manual'
            manualLocationName: localStorage.getItem('manualLocationName') || null,
            manualLat: parseFloat(localStorage.getItem('manualLat')) || null,
            manualLon: parseFloat(localStorage.getItem('manualLon')) || null,
            autoLocationName: localStorage.getItem('autoLocationName') || null,
            autoLat: parseFloat(localStorage.getItem('autoLat')) || null,
            autoLon: parseFloat(localStorage.getItem('autoLon')) || null,
            selectedDate: new Date(),
            useCelsius: localStorage.getItem('useCelsius') === 'true',
            gender: localStorage.getItem('gender') || 'all', // 'male', 'female', or 'all' (unisex default)
            comfort: parseInt(localStorage.getItem('comfort')) || 0, // -10, -5, 0, 5, 10
            hasCompletedOnboarding: localStorage.getItem('hasCompletedOnboarding') === 'true',
            hasPermission: false,
            locationSearching: false,
            locationResults: [],
            locationSearchQuery: ''
        };

        // ============ PLATFORM DETECTION ============
        function isSafari() {
            const ua = navigator.userAgent;
            return /Safari/.test(ua) && !/Chrome/.test(ua) && !/CriOS/.test(ua) && !/FxiOS/.test(ua);
        }
        
        function isIOS() {
            return /iPad|iPhone|iPod/.test(navigator.userAgent) || 
                   (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
        }
        
        function isMobile() {
            return /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent);
        }
        
        function isAndroid() {
            return /Android/i.test(navigator.userAgent);
        }

        // ============ GPS HELP FUNCTIONS ============
        function renderGPSHelp() {
            if (isIOS() && isSafari()) {
                return `
                    <div class="help-title">Enable Location in Safari</div>
                    <div class="help-steps">
                        <div class="help-step">
                            <span class="step-num">1</span>
                            <span>Open your iPhone's <strong>Settings</strong> app</span>
                        </div>
                        <div class="help-step">
                            <span class="step-num">2</span>
                            <span>Scroll down and tap <strong>Safari</strong></span>
                        </div>
                        <div class="help-step">
                            <span class="step-num">3</span>
                            <span>Tap <strong>Location</strong></span>
                        </div>
                        <div class="help-step">
                            <span class="step-num">4</span>
                            <span>Select <strong>Ask</strong> or <strong>Allow</strong></span>
                        </div>
                        <div class="help-step">
                            <span class="step-num">5</span>
                            <span>Return here and tap "Try GPS Again"</span>
                        </div>
                    </div>
                    <div class="help-note">
                        💡 If prompted again, tap <strong>Allow</strong> to share your location with RunWear.
                    </div>
                `;
            } else if (isIOS()) {
                // iOS but not Safari (Chrome, Firefox, etc.)
                return `
                    <div class="help-title">Enable Location Access</div>
                    <div class="help-steps">
                        <div class="help-step">
                            <span class="step-num">1</span>
                            <span>Open your iPhone's <strong>Settings</strong> app</span>
                        </div>
                        <div class="help-step">
                            <span class="step-num">2</span>
                            <span>Tap <strong>Privacy & Security</strong> → <strong>Location Services</strong></span>
                        </div>
                        <div class="help-step">
                            <span class="step-num">3</span>
                            <span>Find your browser in the list and tap it</span>
                        </div>
                        <div class="help-step">
                            <span class="step-num">4</span>
                            <span>Select <strong>While Using the App</strong></span>
                        </div>
                        <div class="help-step">
                            <span class="step-num">5</span>
                            <span>Return here and tap "Try GPS Again"</span>
                        </div>
                    </div>
                `;
            } else if (isAndroid()) {
                return `
                    <div class="help-title">Enable Location Access</div>
                    <div class="help-steps">
                        <div class="help-step">
                            <span class="step-num">1</span>
                            <span>When prompted, tap <strong>Allow</strong></span>
                        </div>
                        <div class="help-step">
                            <span class="step-num">2</span>
                            <span>If you previously denied, tap the <strong>🔒 lock icon</strong> in your browser's address bar</span>
                        </div>
                        <div class="help-step">
                            <span class="step-num">3</span>
                            <span>Find <strong>Location</strong> and change it to <strong>Allow</strong></span>
                        </div>
                        <div class="help-step">
                            <span class="step-num">4</span>
                            <span>Tap "Try GPS Again" below</span>
                        </div>
                    </div>
                `;
            } else {
                // Desktop browser
                return `
                    <div class="help-title">Enable Location Access</div>
                    <div class="help-steps">
                        <div class="help-step">
                            <span class="step-num">1</span>
                            <span>Click the <strong>🔒 lock icon</strong> (or info icon) in your browser's address bar</span>
                        </div>
                        <div class="help-step">
                            <span class="step-num">2</span>
                            <span>Find <strong>Location</strong> in the permissions list</span>
                        </div>
                        <div class="help-step">
                            <span class="step-num">3</span>
                            <span>Change it to <strong>Allow</strong></span>
                        </div>
                        <div class="help-step">
                            <span class="step-num">4</span>
                            <span>Click "Try GPS Again" below</span>
                        </div>
                    </div>
                    <div class="help-note">
                        💡 You may need to reload the page after changing permissions.
                    </div>
                `;
            }
        }
        
        function showGPSHelp() {
            // Hide the main options and show GPS help
            const optionsDiv = document.querySelector('.location-setup-options');
            const helpDiv = document.getElementById('gpsHelpSection');
            
            if (optionsDiv) optionsDiv.style.display = 'none';
            if (helpDiv) helpDiv.style.display = 'block';
        }
        
        function hideGPSHelp() {
            // Show the main options and hide GPS help
            const optionsDiv = document.querySelector('.location-setup-options');
            const helpDiv = document.getElementById('gpsHelpSection');
            
            if (optionsDiv) optionsDiv.style.display = 'block';
            if (helpDiv) helpDiv.style.display = 'none';
        }
        
        function retryGPS() {
            // Clear error state and all saved locations to try fresh GPS
            state.error = null;
            state.errorCode = null;
            state.locationSource = 'auto';
            state.location = null;
            state.autoLat = null;
            state.autoLon = null;
            state.autoLocationName = null;
            localStorage.setItem('locationSource', 'auto');
            localStorage.removeItem('manualLat');
            localStorage.removeItem('manualLon');
            localStorage.removeItem('manualLocationName');
            localStorage.removeItem('autoLat');
            localStorage.removeItem('autoLon');
            localStorage.removeItem('autoLocationName');
            loadWeather();
        }

        // ============ AFFILIATE CONFIG ============
        // Amazon Associates affiliate tracking
        // Uses ascsubtag for platform attribution in Amazon dashboard
        // PWA = pwa, iOS app = ios, Android app = android, Wear OS = wearos
        const AFFILIATE_TAG = 'runwear-20';
        const PLATFORM_SUBTAG = 'pwa'; // Change per platform: pwa, ios, android, wearos
        const AMAZON_BASE = 'https://www.amazon.com/s?k=';

        function buildAmazonLink(searchTerm) {
            const genderPrefix = state.gender === 'male' ? "men's " : 
                                 state.gender === 'female' ? "women's " : '';
            const fullTerm = `premium ${genderPrefix}${searchTerm}`.trim();
            const encoded = encodeURIComponent(fullTerm);
            // Include ascsubtag for platform tracking in Amazon Associates reports
            return `${AMAZON_BASE}${encoded}&tag=${AFFILIATE_TAG}&ascsubtag=${PLATFORM_SUBTAG}`;
        }

        // ============ CLOTHING DATA ============
        const ClothingItems = {
            // Tops
            TANK_TOP: { name: 'Tank Top / Singlet', desc: 'Lightweight, maximum airflow', icon: '👕', category: 'top', search: 'running tank top moisture wicking' },
            SHORT_SLEEVE: { name: 'Short Sleeve Shirt', desc: 'Moisture-wicking technical fabric', icon: '👕', category: 'top', search: 'running short sleeve shirt dri-fit' },
            LONG_SLEEVE_LIGHT: { name: 'Light Long Sleeve', desc: 'Thin, breathable long sleeve', icon: '👕', category: 'top', search: 'running long sleeve lightweight' },
            LONG_SLEEVE_THERMAL: { name: 'Thermal Long Sleeve', desc: 'Insulated base layer for cold', icon: '🧥', category: 'top', search: 'running thermal base layer' },
            
            // Outer layers
            LIGHT_VEST: { name: 'Light Vest', desc: 'Wind protection without overheating', icon: '🦺', category: 'top', search: 'running vest lightweight' },
            WINDBREAKER: { name: 'Windbreaker', desc: 'Lightweight wind and rain protection', icon: '🧥', category: 'top', search: 'running windbreaker jacket' },
            LIGHT_JACKET: { name: 'Light Running Jacket', desc: 'Breathable jacket for cool temps', icon: '🧥', category: 'top', search: 'running jacket lightweight' },
            RAIN_JACKET: { name: 'Rain Jacket', desc: 'Waterproof, breathable shell', icon: '🧥', category: 'top', search: 'running rain jacket waterproof' },
            INSULATED_JACKET: { name: 'Insulated Jacket', desc: 'Warm jacket for cold conditions', icon: '🧥', category: 'top', search: 'running winter jacket insulated' },
            
            // Bottoms
            SHORT_SHORTS: { name: 'Short Shorts (3")', desc: 'Maximum breathability', icon: '🩳', category: 'bottom', search: 'running shorts 3 inch' },
            SHORTS: { name: 'Running Shorts (5-7")', desc: 'Standard running shorts', icon: '🩳', category: 'bottom', search: 'running shorts 5 inch' },
            LIGHT_TIGHTS: { name: 'Light Tights', desc: 'Full leg coverage, breathable', icon: '👖', category: 'bottom', search: 'running tights lightweight' },
            THERMAL_TIGHTS: { name: 'Thermal Tights', desc: 'Insulated for cold weather', icon: '👖', category: 'bottom', search: 'running tights thermal winter' },
            
            // Head
            VISOR: { name: 'Visor', desc: 'Sun protection, max ventilation', icon: '🧢', category: 'head', search: 'running visor' },
            BASEBALL_CAP: { name: 'Running Cap', desc: 'Sun and light rain protection', icon: '🧢', category: 'head', search: 'running cap lightweight' },
            HEADBAND: { name: 'Ear Warmer / Headband', desc: 'Keeps ears warm', icon: '🎧', category: 'head', search: 'running ear warmer headband' },
            LIGHT_BEANIE: { name: 'Light Beanie', desc: 'Thin beanie for moderate cold', icon: '🧢', category: 'head', search: 'running beanie lightweight' },
            THERMAL_BEANIE: { name: 'Thermal Beanie', desc: 'Warm hat for very cold weather', icon: '🧢', category: 'head', search: 'running beanie thermal winter' },
            BALACLAVA: { name: 'Balaclava / Face Mask', desc: 'Full face protection', icon: '🎭', category: 'head', search: 'running balaclava face mask' },
            
            // Hands
            LIGHT_GLOVES: { name: 'Light Gloves', desc: 'Thin running gloves', icon: '🧤', category: 'hands', search: 'running gloves lightweight touchscreen' },
            THERMAL_GLOVES: { name: 'Thermal Gloves', desc: 'Insulated gloves for cold', icon: '🧤', category: 'hands', search: 'running gloves thermal winter' },
            MITTENS: { name: 'Mittens', desc: 'Maximum warmth for extreme cold', icon: '🧤', category: 'hands', search: 'running mittens warm' },
            
            // Accessories
            SUNGLASSES: { name: 'Sunglasses', desc: 'Eye protection from sun and wind', icon: '🕶️', category: 'accessories', search: 'running sunglasses sport' },
            SUNSCREEN: { name: 'Sunscreen (SPF 30+)', desc: 'Protect exposed skin', icon: '🧴', category: 'accessories', search: 'sport sunscreen spf 50' },
            REFLECTIVE_GEAR: { name: 'Reflective Gear', desc: 'Visibility for low-light', icon: '🦺', category: 'accessories', search: 'running reflective vest' },
            NECK_GAITER: { name: 'Neck Gaiter / Buff', desc: 'Versatile neck and face protection', icon: '🧣', category: 'accessories', search: 'running neck gaiter buff' }
        };

        // ============ RECOMMENDATION ENGINE ============
        function getOutfitRecommendation(weather) {
            // Apply comfort preference: negative = run cold (dress warmer), positive = run hot (dress cooler)
            const temp = weather.feelsLike - state.comfort;
            const isWindy = weather.windSpeed > 10;
            const isHumid = weather.humidity > 65;
            const isSunny = weather.cloudCover < 50 && weather.uvIndex > 2;
            const isRaining = weather.isRaining;
            
            let outfit = { items: [], tips: [] };
            
            // Top base layer
            if (temp >= 70) outfit.items.push(ClothingItems.TANK_TOP);
            else if (temp >= 60) outfit.items.push(ClothingItems.SHORT_SLEEVE);
            else if (temp >= 45) outfit.items.push(ClothingItems.LONG_SLEEVE_LIGHT);
            else if (temp >= 30) outfit.items.push(ClothingItems.LONG_SLEEVE_LIGHT);
            else outfit.items.push(ClothingItems.LONG_SLEEVE_THERMAL);
            
            // Top outer layer
            if (isRaining && weather.precipitation > 0.1) {
                outfit.items.push(ClothingItems.RAIN_JACKET);
            } else if (isWindy && temp >= 40 && temp < 60) {
                outfit.items.push(ClothingItems.WINDBREAKER);
            } else if (temp < 60 && temp >= 50 && isWindy) {
                outfit.items.push(ClothingItems.LIGHT_VEST);
            } else if (temp < 50 && temp >= 40) {
                outfit.items.push(ClothingItems.LIGHT_JACKET);
            } else if (temp < 40 && temp >= 25) {
                outfit.items.push(ClothingItems.LIGHT_JACKET);
            } else if (temp < 25) {
                outfit.items.push(ClothingItems.INSULATED_JACKET);
            }
            
            // Bottoms
            const effectiveTemp = temp + (isHumid && temp > 50 ? 5 : 0);
            if (effectiveTemp >= 75) outfit.items.push(ClothingItems.SHORT_SHORTS);
            else if (effectiveTemp >= 50) outfit.items.push(ClothingItems.SHORTS);
            else if (effectiveTemp >= 40) outfit.items.push(ClothingItems.LIGHT_TIGHTS);
            else outfit.items.push(ClothingItems.THERMAL_TIGHTS);
            
            // Head
            if (temp >= 60 && isSunny) outfit.items.push(ClothingItems.BASEBALL_CAP);
            else if (isRaining) outfit.items.push(ClothingItems.BASEBALL_CAP);
            else if (temp < 50 && temp >= 40 && isWindy) outfit.items.push(ClothingItems.HEADBAND);
            else if (temp < 40 && temp >= 30) outfit.items.push(ClothingItems.HEADBAND);
            else if (temp < 30 && temp >= 20) outfit.items.push(ClothingItems.LIGHT_BEANIE);
            else if (temp < 20 && temp >= 5) outfit.items.push(ClothingItems.THERMAL_BEANIE);
            else if (temp < 5) outfit.items.push(ClothingItems.BALACLAVA);
            
            // Hands
            const handTemp = temp + (isWindy ? -5 : 0);
            if (handTemp < 45 && handTemp >= 35) outfit.items.push(ClothingItems.LIGHT_GLOVES);
            else if (handTemp < 35 && handTemp >= 20) outfit.items.push(ClothingItems.THERMAL_GLOVES);
            else if (handTemp < 20) outfit.items.push(ClothingItems.MITTENS);
            
            // Accessories
            if (isSunny || weather.uvIndex >= 3) outfit.items.push(ClothingItems.SUNGLASSES);
            if (weather.uvIndex >= 3) outfit.items.push(ClothingItems.SUNSCREEN);
            if (weather.cloudCover > 80 || isRaining) outfit.items.push(ClothingItems.REFLECTIVE_GEAR);
            if (temp < 30 && isWindy) outfit.items.push(ClothingItems.NECK_GAITER);
            
            // Tips
            if (temp >= 80) {
                outfit.tips.push('🌡️ High heat risk — consider running early morning or evening');
                outfit.tips.push('💧 Hydrate well before, during, and after your run');
            } else if (temp >= 70) {
                outfit.tips.push('💧 Stay hydrated — consider carrying water');
            }
            if (temp < 20) {
                outfit.tips.push('🥶 Extreme cold — keep your run shorter and stay close to home');
                outfit.tips.push('🏠 Change out of wet clothes immediately after finishing');
            } else if (temp < 32) {
                outfit.tips.push('❄️ Watch for ice on paths and roads');
            }
            if (isWindy) outfit.tips.push('💨 Run into the wind first, return with wind at your back');
            if (isRaining) {
                outfit.tips.push('🌧️ Wear a hat to keep rain out of your eyes');
                if (temp < 50) outfit.tips.push('⚠️ You\'ll get cold faster when wet — dress warmer');
            }
            if (isHumid && temp > 65) {
                outfit.tips.push('💦 High humidity — wear looser clothes for better airflow');
            }
            if (weather.uvIndex >= 6) outfit.tips.push('☀️ Very high UV — reapply sunscreen every 2 hours');
            
            outfit.tips = outfit.tips.slice(0, 3);
            return outfit;
        }

        // ============ WEATHER API ============
        async function fetchWeather(lat, lon, date) {
            const isToday = isSameDay(date, new Date()) && date.getHours() === new Date().getHours();
            const unit = state.useCelsius ? 'celsius' : 'fahrenheit';
            const windUnit = state.useCelsius ? 'kmh' : 'mph';
            
            const params = new URLSearchParams({
                latitude: lat,
                longitude: lon,
                current: 'temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,rain,snowfall,weather_code,cloud_cover,wind_speed_10m,wind_gusts_10m,uv_index',
                hourly: 'temperature_2m,relative_humidity_2m,apparent_temperature,precipitation_probability,precipitation,rain,snowfall,weather_code,cloud_cover,wind_speed_10m,wind_gusts_10m,uv_index',
                temperature_unit: unit,
                wind_speed_unit: windUnit,
                timezone: 'auto',
                forecast_days: 7
            });
            
            const response = await fetch(`https://api.open-meteo.com/v1/forecast?${params}`);
            if (!response.ok) throw new Error('Weather API error');
            const data = await response.json();
            
            let weather;
            if (isToday && data.current) {
                const c = data.current;
                weather = {
                    temp: c.temperature_2m,
                    feelsLike: c.apparent_temperature,
                    humidity: c.relative_humidity_2m,
                    windSpeed: c.wind_speed_10m,
                    windGusts: c.wind_gusts_10m,
                    precipitation: c.precipitation,
                    isRaining: c.rain > 0,
                    isSnowing: c.snowfall > 0,
                    cloudCover: c.cloud_cover,
                    uvIndex: c.uv_index || 0,
                    weatherCode: c.weather_code
                };
            } else {
                const targetHour = date.toISOString().slice(0, 13);
                const hourly = data.hourly;
                let idx = hourly.time.findIndex(t => t.startsWith(targetHour));
                if (idx === -1) idx = 0;
                
                weather = {
                    temp: hourly.temperature_2m[idx],
                    feelsLike: hourly.apparent_temperature[idx],
                    humidity: hourly.relative_humidity_2m[idx],
                    windSpeed: hourly.wind_speed_10m[idx],
                    windGusts: hourly.wind_gusts_10m[idx],
                    precipitationProbability: hourly.precipitation_probability[idx],
                    precipitation: hourly.precipitation[idx],
                    isRaining: hourly.rain[idx] > 0,
                    isSnowing: hourly.snowfall[idx] > 0,
                    cloudCover: hourly.cloud_cover[idx],
                    uvIndex: hourly.uv_index[idx],
                    weatherCode: hourly.weather_code[idx]
                };
            }
            
            return weather;
        }

        // ============ LOCATION ============
        async function getCurrentLocation() {
            // First check if we can even ask for permission
            if (!navigator.geolocation) {
                const err = new Error('Geolocation not supported');
                err.code = 2;
                throw err;
            }
            
            // Check permission state if available (helps detect blocked permissions)
            if (navigator.permissions) {
                try {
                    const permissionStatus = await navigator.permissions.query({ name: 'geolocation' });
                    if (permissionStatus.state === 'denied') {
                        const err = new Error('Location permission denied');
                        err.code = 1;
                        throw err;
                    }
                } catch (e) {
                    // permissions API not fully supported, continue anyway
                }
            }
            
            return new Promise((resolve, reject) => {
                // Manual timeout in case browser timeout doesn't work
                const timeoutId = setTimeout(() => {
                    const err = new Error('Location request timed out');
                    err.code = 3;
                    reject(err);
                }, 15000); // 15 second timeout (first request can be slow)

                navigator.geolocation.getCurrentPosition(
                    pos => {
                        clearTimeout(timeoutId);
                        resolve({ lat: pos.coords.latitude, lon: pos.coords.longitude });
                    },
                    err => {
                        clearTimeout(timeoutId);
                        reject(err);
                    },
                    { enableHighAccuracy: false, timeout: 15000, maximumAge: 300000 }
                );
            });
        }
        
        async function getLocationName(lat, lon) {
            try {
                const response = await fetch(
                    `https://nominatim.openstreetmap.org/reverse?lat=${lat}&lon=${lon}&format=json&zoom=10`
                );
                if (!response.ok) throw new Error('Geocoding failed');
                const data = await response.json();
                
                const addr = data.address || {};
                const city = addr.city || addr.town || addr.village || addr.municipality || addr.suburb || '';
                const stateAbbr = addr.state || addr.region || '';
                
                if (city && stateAbbr) return `${city}, ${stateAbbr}`;
                else if (city) return city;
                else if (data.display_name) {
                    return data.display_name.split(',').slice(0, 2).join(',').trim();
                }
                return 'Your Location';
            } catch (err) {
                console.error('Reverse geocoding error:', err);
                return 'Your Location';
            }
        }

        // ============ HELPERS ============
        function getWeatherIcon(code) {
            const icons = {
                0: '☀️', 1: '🌤️', 2: '⛅', 3: '☁️',
                45: '🌫️', 48: '🌫️',
                51: '🌧️', 53: '🌧️', 55: '🌧️',
                61: '🌧️', 63: '🌧️', 65: '🌧️',
                71: '🌨️', 73: '🌨️', 75: '🌨️', 77: '🌨️',
                80: '🌦️', 81: '🌦️', 82: '⛈️',
                85: '🌨️', 86: '🌨️',
                95: '⛈️', 96: '⛈️', 99: '⛈️'
            };
            return icons[code] || '❓';
        }

        function getWeatherDescription(code) {
            const descriptions = {
                0: 'Clear Sky',
                1: 'Mainly Clear',
                2: 'Partly Cloudy',
                3: 'Overcast',
                45: 'Foggy',
                48: 'Rime Fog',
                51: 'Light Drizzle',
                53: 'Moderate Drizzle',
                55: 'Dense Drizzle',
                61: 'Light Rain',
                63: 'Moderate Rain',
                65: 'Heavy Rain',
                71: 'Light Snow',
                73: 'Moderate Snow',
                75: 'Heavy Snow',
                77: 'Snow Grains',
                80: 'Light Showers',
                81: 'Moderate Showers',
                82: 'Heavy Showers',
                85: 'Light Snow Showers',
                86: 'Heavy Snow Showers',
                95: 'Thunderstorm',
                96: 'Thunderstorm with Hail',
                99: 'Severe Thunderstorm'
            };
            return descriptions[code] || 'Unknown';
        }

        // Get temperature bracket name (freezing, cold, cool, mild, warm, hot)
        function getTempBracket(temp) {
            const f = state.useCelsius ? temp * 9/5 + 32 : temp;
            if (f >= 80) return 'hot';
            if (f >= 65) return 'warm';
            if (f >= 50) return 'mild';
            if (f >= 35) return 'cool';
            if (f >= 20) return 'cold';
            return 'freezing';
        }

        function getTempClass(temp) {
            return 'temp-' + getTempBracket(temp);
        }

        // SVG Icons for categories
        const categoryIcons = {
            head: `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 3c1.66 0 3 1.34 3 3s-1.34 3-3 3-3-1.34-3-3 1.34-3 3-3zm0 14.2c-2.5 0-4.71-1.28-6-3.22.03-1.99 4-3.08 6-3.08 1.99 0 5.97 1.09 6 3.08-1.29 1.94-3.5 3.22-6 3.22z"/></svg>`,
            top: `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M21.6 18.2L13 11.75v-.91c1.65-.49 2.8-2.17 2.43-4.05-.26-1.31-1.3-2.4-2.61-2.7C10.54 3.57 8.5 5.3 8.5 7.5h2c0-.83.67-1.5 1.5-1.5s1.5.67 1.5 1.5c0 .84-.69 1.52-1.53 1.5-.54-.01-.97.45-.97.99v1.76L2.4 18.2c-.77.58-.36 1.8.6 1.8h18c.96 0 1.37-1.22.6-1.8zM6 18l6-4.5 6 4.5H6z"/></svg>`,
            bottom: `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L12 15v1c0 1.1.9 2 2 2v1.93zM17.9 17.39c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z"/></svg>`,
            hands: `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M4.5 12c-.28 0-.5.22-.5.5v4c0 .28.22.5.5.5s.5-.22.5-.5v-4c0-.28-.22-.5-.5-.5zm2 0c-.28 0-.5.22-.5.5v5c0 .28.22.5.5.5s.5-.22.5-.5v-5c0-.28-.22-.5-.5-.5zm2-.5c-.28 0-.5.22-.5.5v6c0 .28.22.5.5.5s.5-.22.5-.5v-6c0-.28-.22-.5-.5-.5zm3.5-2v8c0 .28.22.5.5.5s.5-.22.5-.5v-8c0-.28-.22-.5-.5-.5s-.5.22-.5.5zm6.5 2c-.28 0-.5.22-.5.5v4c0 .28.22.5.5.5s.5-.22.5-.5v-4c0-.28-.22-.5-.5-.5zm-2 0c-.28 0-.5.22-.5.5v5c0 .28.22.5.5.5s.5-.22.5-.5v-5c0-.28-.22-.5-.5-.5zm-2-.5c-.28 0-.5.22-.5.5v6c0 .28.22.5.5.5s.5-.22.5-.5v-6c0-.28-.22-.5-.5-.5z"/></svg>`,
            accessories: `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z"/></svg>`
        };

        function getCategoryIcon(category) {
            return categoryIcons[category] || categoryIcons.accessories;
        }

        // ============ SUPABASE HERO IMAGE SERVICE ============
        const SUPABASE_URL = 'https://ebicqznlcjbqcukjfzcf.supabase.co';
        const SUPABASE_ANON_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImViaWNxem5sY2picWN1a2pmemNmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njk1NDA5MzgsImV4cCI6MjA4NTExNjkzOH0.0Zl7DF4y6riHWzNEDqMwtYZerbFVXAlpFGbeJ3S1Bg4';
        const PLACEHOLDER_IMAGE = 'https://images.unsplash.com/photo-1552674605-db6ffd4facb5?w=800&h=1200&fit=crop';

        // Default hero images by temperature bracket AND weather condition (2D matrix)
        const DEFAULT_HERO_IMAGES = {
            hot: {
                clear: 'https://images.unsplash.com/photo-1571008887538-b36bb32f4571?w=800&h=1200&fit=crop',   // Tank top, bright sunny
                cloudy: 'https://images.unsplash.com/photo-1571019613454-1cb2f99b2d8b?w=800&h=1200&fit=crop',  // Summer overcast
                rain: 'https://images.unsplash.com/photo-1534258936925-c58bed479fcb?w=800&h=1200&fit=crop',    // Summer rain run
                snow: null  // Not possible when hot
            },
            warm: {
                clear: 'https://images.unsplash.com/photo-1486218119243-13883505764c?w=800&h=1200&fit=crop',   // T-shirt sunny
                cloudy: 'https://images.unsplash.com/photo-1558017487-06bf9f82613a?w=800&h=1200&fit=crop',     // Warm overcast
                rain: 'https://images.unsplash.com/photo-1534258936925-c58bed479fcb?w=800&h=1200&fit=crop',    // Warm rain
                snow: null  // Not possible when warm
            },
            mild: {
                clear: 'https://images.unsplash.com/photo-1552674605-db6ffd4facb5?w=800&h=1200&fit=crop',      // Light layers sunny
                cloudy: 'https://images.unsplash.com/photo-1558017487-06bf9f82613a?w=800&h=1200&fit=crop',     // Mild overcast
                rain: 'https://images.unsplash.com/photo-1515191107209-c28698631303?w=800&h=1200&fit=crop',    // Spring/fall rain
                snow: 'https://images.unsplash.com/photo-1517483000871-1dbf64a6e1c6?w=800&h=1200&fit=crop'     // Light snow
            },
            cool: {
                clear: 'https://images.unsplash.com/photo-1476480862126-209bfaa8edc8?w=800&h=1200&fit=crop',   // Long sleeves sunny
                cloudy: 'https://images.unsplash.com/photo-1571019613454-1cb2f99b2d8b?w=800&h=1200&fit=crop',  // Cool overcast
                rain: 'https://images.unsplash.com/photo-1515191107209-c28698631303?w=800&h=1200&fit=crop',    // Cool rainy
                snow: 'https://images.unsplash.com/photo-1517483000871-1dbf64a6e1c6?w=800&h=1200&fit=crop'     // Cool snow
            },
            cold: {
                clear: 'https://images.unsplash.com/photo-1485727749690-d091e8284ef3?w=800&h=1200&fit=crop',   // Jacket sunny winter
                cloudy: 'https://images.unsplash.com/photo-1485727749690-d091e8284ef3?w=800&h=1200&fit=crop',  // Cold overcast
                rain: 'https://images.unsplash.com/photo-1519692933481-e162a57d6721?w=800&h=1200&fit=crop',    // Cold rain/sleet
                snow: 'https://images.unsplash.com/photo-1483921020237-2ff51e8e4b22?w=800&h=1200&fit=crop'     // Snowy run
            },
            freezing: {
                clear: 'https://images.unsplash.com/photo-1544899489-a083461b088c?w=800&h=1200&fit=crop',      // Winter gear sunny
                cloudy: 'https://images.unsplash.com/photo-1544899489-a083461b088c?w=800&h=1200&fit=crop',     // Freezing overcast
                rain: 'https://images.unsplash.com/photo-1519692933481-e162a57d6721?w=800&h=1200&fit=crop',    // Freezing rain/sleet
                snow: 'https://images.unsplash.com/photo-1516410529446-2c777cb7366d?w=800&h=1200&fit=crop'     // Heavy snow winter
            }
        };

        // Get weather condition for fallback images (simplified from weather code)
        function getWeatherConditionForFallback(weatherCode) {
            if (weatherCode === 0 || weatherCode === 1) return 'clear';
            if ([2, 3, 45, 46, 47, 48].includes(weatherCode)) return 'cloudy';
            if (weatherCode >= 51 && weatherCode <= 67) return 'rain';
            if (weatherCode >= 71 && weatherCode <= 77) return 'snow';
            if (weatherCode >= 80 && weatherCode <= 82) return 'rain';
            if (weatherCode >= 85 && weatherCode <= 86) return 'snow';
            if (weatherCode >= 95) return 'rain';  // Thunderstorm
            return 'clear';
        }

        // Get default hero image by temp AND weather
        function getDefaultHeroImage(tempBracket, weatherCode) {
            const bracket = DEFAULT_HERO_IMAGES[tempBracket] || DEFAULT_HERO_IMAGES.mild;
            const weather = getWeatherConditionForFallback(weatherCode);
            return bracket[weather] || bracket.clear || PLACEHOLDER_IMAGE;
        }

        // Preload key default images for instant display (most common conditions)
        const PRELOAD_IMAGES = [
            DEFAULT_HERO_IMAGES.hot.clear,
            DEFAULT_HERO_IMAGES.warm.clear,
            DEFAULT_HERO_IMAGES.mild.clear,
            DEFAULT_HERO_IMAGES.mild.rain,
            DEFAULT_HERO_IMAGES.cool.clear,
            DEFAULT_HERO_IMAGES.cool.rain,
            DEFAULT_HERO_IMAGES.cold.clear,
            DEFAULT_HERO_IMAGES.cold.snow,
            DEFAULT_HERO_IMAGES.freezing.clear,
            DEFAULT_HERO_IMAGES.freezing.snow
        ].filter(Boolean);

        PRELOAD_IMAGES.forEach(url => {
            const img = new Image();
            img.src = url;
        });

        // Current hero image URL (updated asynchronously)
        let currentHeroImageUrl = PLACEHOLDER_IMAGE;

        // Map weather codes to weather categories (v3.7 - simplified to 4 categories)
        function getWeatherCode(code) {
            if (code === 0 || code === 1) return 'CLEAR';
            if ([2, 3, 45, 46, 47, 48].includes(code)) return 'CLOUDY';
            if (code >= 51 && code <= 67) return 'RAIN';
            if (code >= 71 && code <= 77) return 'SNOW';
            if (code >= 80 && code <= 82) return 'RAIN';
            if (code >= 85 && code <= 86) return 'SNOW';
            if (code >= 95) return 'RAIN';  // Thunderstorm = RAIN
            return 'CLEAR';
        }

        // Alias for spec compatibility
        function getWeatherCondition(code) {
            return getWeatherCode(code);
        }

        // Get time of day category (v3.7 spec)
        function getTimeOfDay(date) {
            const hour = date.getHours();
            if (hour >= 5 && hour < 10) return 'DAWN';
            if (hour >= 10 && hour < 17) return 'MIDDAY';
            if (hour >= 17 && hour < 20) return 'DUSK';
            return 'NIGHT';  // 20-4
        }

        // Get gender preference for API
        function getGenderPreference() {
            if (state.gender === 'male') return 'MALE';
            if (state.gender === 'female') return 'FEMALE';
            return 'UNISEX';
        }

        // Generate outfit hash from current outfit items
        function getOutfitHash() {
            if (!state.outfit || !state.outfit.items) return 'default';
            // Create hash from outfit item names
            const itemString = state.outfit.items.map(i => i.name).sort().join('|');
            let hash = 0;
            for (let i = 0; i < itemString.length; i++) {
                const char = itemString.charCodeAt(i);
                hash = ((hash << 5) - hash) + char;
                hash = hash & hash; // Convert to 32-bit integer
            }
            return Math.abs(hash).toString(16).slice(0, 8);
        }

        // Build combination ID from current conditions (matches backend format)
        function buildCombinationId(gender, weather, tempBracket, timeOfDay, outfitHash) {
            return `${gender}_${weather}_${tempBracket}_${timeOfDay}_${outfitHash}`;
        }

        // Query Supabase for cached hero image
        // Legacy function - kept for compatibility, now uses cascading logic in loadHeroImage()
        async function fetchCachedHeroImage(combinationId) {
            // Cascading queries are now handled directly in loadHeroImage()
            // This function is kept for any external calls but just returns null
            console.log('[Hero] fetchCachedHeroImage called directly - use loadHeroImage() instead');
            return null;
        }

        // Increment serve count when image is displayed
        async function incrementServeCount(imageId) {
            if (!imageId) return;
            try {
                await fetch(
                    `${SUPABASE_URL}/rest/v1/rpc/increment_serve_count`,
                    {
                        method: 'POST',
                        headers: {
                            'apikey': SUPABASE_ANON_KEY,
                            'Authorization': `Bearer ${SUPABASE_ANON_KEY}`,
                            'Content-Type': 'application/json'
                        },
                        body: JSON.stringify({ p_image_id: imageId })
                    }
                );
            } catch (e) {
                // Silent fail - not critical
            }
        }

        // Check library stats for replenishment decision
        async function getLibraryStats(combinationId) {
            try {
                const response = await fetch(
                    `${SUPABASE_URL}/rest/v1/library_stats?combination_id=eq.${combinationId}`,
                    {
                        headers: {
                            'apikey': SUPABASE_ANON_KEY,
                            'Authorization': `Bearer ${SUPABASE_ANON_KEY}`
                        }
                    }
                );
                if (!response.ok) return null;
                const stats = await response.json();
                return stats.length > 0 ? stats[0] : null;
            } catch (e) {
                return null;
            }
        }

        // Replenishment rules from spec
        const REPLENISHMENT_RULES = {
            dailyBudget: 50,           // Max 50 images/day
            maxImagesPerCombo: 10,     // Cap per combination
            minImageAgeHours: 24,      // Don't regenerate too fast
            matureThreshold: 5,        // Combo is "mature" at 5+ images
            matureRefreshDays: 7       // Slow refresh for mature combos
        };

        // Check if we should replenish this combination
        async function shouldReplenish(combinationId) {
            const stats = await getLibraryStats(combinationId);

            // No stats = new combination, should generate
            if (!stats) return true;

            const imageCount = stats.image_count || 0;
            const lastGenerated = stats.last_generated_at ? new Date(stats.last_generated_at) : null;
            const now = new Date();

            // Already at max images
            if (imageCount >= REPLENISHMENT_RULES.maxImagesPerCombo) {
                return false;
            }

            // Check minimum age since last generation
            if (lastGenerated) {
                const hoursSinceLastGen = (now - lastGenerated) / (1000 * 60 * 60);

                // If mature combo, use longer interval
                if (imageCount >= REPLENISHMENT_RULES.matureThreshold) {
                    const daysSinceLastGen = hoursSinceLastGen / 24;
                    if (daysSinceLastGen < REPLENISHMENT_RULES.matureRefreshDays) {
                        return false;
                    }
                } else {
                    // Immature combo, use shorter interval
                    if (hoursSinceLastGen < REPLENISHMENT_RULES.minImageAgeHours) {
                        return false;
                    }
                }
            }

            return true;
        }

        // Ensure outfit_combination exists in database
        async function ensureOutfitCombination(combinationId, gender, weather, tempBracket, timeOfDay, outfitHash) {
            try {
                // Try to insert (will fail silently if exists due to primary key)
                await fetch(
                    `${SUPABASE_URL}/rest/v1/outfit_combinations`,
                    {
                        method: 'POST',
                        headers: {
                            'apikey': SUPABASE_ANON_KEY,
                            'Authorization': `Bearer ${SUPABASE_ANON_KEY}`,
                            'Content-Type': 'application/json',
                            'Prefer': 'return=minimal,resolution=ignore-duplicates'
                        },
                        body: JSON.stringify({
                            id: combinationId,
                            gender_preference: gender,
                            weather_code: weather,
                            temp_bracket: tempBracket,
                            time_of_day: timeOfDay,
                            outfit_hash: outfitHash
                        })
                    }
                );
            } catch (e) {
                // Ignore - combination may already exist
            }
        }

        // Queue image generation in Supabase
        async function queueHeroImageGeneration(combinationId, prompt) {
            try {
                const response = await fetch(
                    `${SUPABASE_URL}/rest/v1/generation_jobs`,
                    {
                        method: 'POST',
                        headers: {
                            'apikey': SUPABASE_ANON_KEY,
                            'Authorization': `Bearer ${SUPABASE_ANON_KEY}`,
                            'Content-Type': 'application/json',
                            'Prefer': 'return=minimal'
                        },
                        body: JSON.stringify({
                            combination_id: combinationId,
                            prompt: prompt,
                            status: 'QUEUED'
                        })
                    }
                );
                return response.ok;
            } catch (e) {
                console.error('Failed to queue hero image generation:', e);
                return false;
            }
        }

        // Build prompt for image generation (matches spec format)
        function buildImagePrompt(gender, weather, tempBracket, timeOfDay, outfit) {
            const genderText = gender === 'UNISEX' ? 'athletic' : gender.toLowerCase();
            const timeLabels = { DAWN: 'early morning', MIDDAY: 'midday', DUSK: 'evening', NIGHT: 'night' };
            const tempLabels = { FREEZING: 'freezing cold', COLD: 'cold', COOL: 'cool', MILD: 'mild', WARM: 'warm', HOT: 'hot' };

            // Build outfit description from actual items
            let outfitDesc = '';
            if (outfit && outfit.items) {
                outfitDesc = outfit.items.map(item => item.name).join(', ');
            }

            return `A ${genderText} runner in their 30s running mid-stride along a suburban sidewalk during a ${tempLabels[tempBracket] || tempBracket.toLowerCase()} ${timeLabels[timeOfDay] || timeOfDay.toLowerCase()}.

OUTFIT (show ALL items clearly): ${outfitDesc || 'appropriate running gear'}

ENVIRONMENT: ${getEnvironmentDesc(weather, tempBracket)}

STYLE: Professional athletic action photography. Full body mid-stride, natural running gait, dynamic motion. Sharp focus on runner, soft bokeh background. Authentic and aspirational, like a Nike campaign.

MOOD: ${getMoodDesc(tempBracket)}`;
        }

        function getEnvironmentDesc(weather, tempBracket) {
            const weatherDescs = {
                CLEAR: 'Clear blue sky, suburban neighborhood with sidewalks and trees',
                CLOUDY: 'Overcast gray sky, diffused lighting',
                RAIN: 'Wet pavement, light rain, gray sky',
                SNOW: 'Snow-dusted sidewalk, flurries, winter scene'
            };
            const tempAdditions = {
                FREEZING: ', frost on grass, breath visible',
                COLD: ', crisp winter air',
                COOL: ', fresh morning air',
                MILD: ', pleasant conditions',
                WARM: ', bright sunshine',
                HOT: ', heat shimmer on asphalt, intense sun'
            };
            return (weatherDescs[weather] || weatherDescs.CLEAR) + (tempAdditions[tempBracket] || '');
        }

        function getMoodDesc(tempBracket) {
            const moods = {
                FREEZING: 'Tough and determined, layered and ready',
                COLD: 'Brisk and prepared, embracing the cold',
                COOL: 'Fresh and invigorating, energized',
                MILD: 'Perfect running weather, pure joy',
                WARM: 'Light and free, enjoying the warmth',
                HOT: 'Intense but determined, beating the heat'
            };
            return moods[tempBracket] || 'Focused and determined';
        }

        // Load hero image from Supabase with cascading fallback queries
        // Priority: Exact match → Any time → Unisex → Clear weather fallback
        async function loadHeroImage() {
            if (!state.weather || !state.outfit) {
                console.log('[Hero] No weather/outfit data yet');
                return;
            }

            const weather = getWeatherCode(state.weather.weatherCode);
            const tempBracket = getTempBracket(state.weather.feelsLike).toUpperCase();
            const timeOfDay = getTimeOfDay(state.selectedDate);
            const gender = getGenderPreference();

            console.log('[Hero] Building queries for:', { gender, weather, tempBracket, timeOfDay });

            // Cascade: most specific → least specific
            const queries = [
                `${gender}_${weather}_${tempBracket}_${timeOfDay}`,   // 1. Exact: MALE_CLOUDY_MILD_NIGHT_*
                `${gender}_${weather}_${tempBracket}`,                // 2. Any time: MALE_CLOUDY_MILD_*
                `UNISEX_${weather}_${tempBracket}`,                   // 3. Unisex fallback: UNISEX_CLOUDY_MILD_*
                `${gender}_CLEAR_${tempBracket}`,                     // 4. Clear weather fallback: MALE_CLEAR_MILD_*
            ];

            for (const baseQuery of queries) {
                console.log('[Hero] Trying:', baseQuery + '_%');

                try {
                    const response = await fetch(
                        `${SUPABASE_URL}/rest/v1/generated_images?combination_id=like.${baseQuery}_%25&limit=10`,
                        {
                            headers: {
                                'apikey': SUPABASE_ANON_KEY,
                                'Authorization': `Bearer ${SUPABASE_ANON_KEY}`
                            }
                        }
                    );

                    if (!response.ok) {
                        console.warn('[Hero] Query failed:', response.status);
                        continue;
                    }

                    const images = await response.json();

                    if (images && images.length > 0) {
                        const selected = images[Math.floor(Math.random() * images.length)];
                        console.log('[Hero] ✓ Found via', baseQuery, '→', selected.combination_id);
                        currentHeroImageUrl = selected.image_url || selected.public_url;
                        updateHeroImage();
                        return;  // Success! Stop searching
                    }
                } catch (e) {
                    console.warn('[Hero] Query error:', baseQuery, e);
                }
            }

            // No AI images found at any level - fallback already set
            console.log('[Hero] No AI images found, queueing replenishment');

            // Queue generation job (fire and forget)
            queueHeroImageGeneration(gender, weather, tempBracket, timeOfDay);
        }

        // Queue a hero image generation job when none exist
        async function queueHeroImageGeneration(gender, weather, temp, time) {
            const combinationId = `${gender}_${weather}_${temp}_${time}_v1`;

            try {
                // Check if already queued/exists
                const checkResponse = await fetch(
                    `${SUPABASE_URL}/rest/v1/generation_jobs?combination_id=eq.${combinationId}&limit=1`,
                    { headers: { 'apikey': SUPABASE_ANON_KEY } }
                );
                const existing = await checkResponse.json();
                if (existing.length > 0) {
                    console.log('[Hero] Generation already queued for:', combinationId);
                    return;
                }

                // Queue new generation
                await fetch(`${SUPABASE_URL}/rest/v1/generation_jobs`, {
                    method: 'POST',
                    headers: {
                        'apikey': SUPABASE_ANON_KEY,
                        'Authorization': `Bearer ${SUPABASE_ANON_KEY}`,
                        'Content-Type': 'application/json',
                        'Prefer': 'return=minimal'
                    },
                    body: JSON.stringify({
                        combination_id: combinationId,
                        gender: gender.toLowerCase(),
                        weather_code: weather.toLowerCase(),
                        temp_bracket: temp.toLowerCase(),
                        time_of_day: time.toLowerCase(),
                        prompt: buildHeroPrompt(gender, weather, temp, time),
                        status: 'pending'
                    })
                });

                console.log('[Hero] Queued generation for:', combinationId);
            } catch (e) {
                console.warn('[Hero] Failed to queue generation:', e);
            }
        }

        // Build prompt for hero image generation
        function buildHeroPrompt(gender, weather, temp, time) {
            const genderDesc = gender === 'MALE' ? 'male' : gender === 'FEMALE' ? 'female' : 'person';
            const weatherDesc = weather.toLowerCase();
            const tempDesc = temp.toLowerCase().replace('_', ' ');
            const timeDesc = time === 'DAWN' ? 'early morning' : time === 'MIDDAY' ? 'midday' : time === 'DUSK' ? 'evening' : 'night';

            return `Professional running photography, ${genderDesc} runner in motion, ${weatherDesc} weather, ${tempDesc} temperature, ${timeDesc} lighting, urban trail or park setting, dynamic action shot, high quality, sharp focus`;
        }

        // Update hero image in DOM with crossfade
        function updateHeroImage() {
            const heroImg = document.querySelector('.hero-image');
            if (heroImg && heroImg.src !== currentHeroImageUrl) {
                heroImg.style.opacity = '0';
                heroImg.onload = () => {
                    heroImg.style.transition = 'opacity 0.5s ease';
                    heroImg.style.opacity = '1';
                };
                heroImg.src = currentHeroImageUrl;
            }
        }

        // Get current hero image URL (for render)
        function getHeroImageUrl() {
            return currentHeroImageUrl;
        }

        function formatDate(date) {
            const today = new Date();
            const tomorrow = new Date(today);
            tomorrow.setDate(tomorrow.getDate() + 1);
            
            if (isSameDay(date, today)) return 'Today';
            if (isSameDay(date, tomorrow)) return 'Tomorrow';
            return date.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' });
        }

        function formatTime(date) {
            return date.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' });
        }

        function isSameDay(d1, d2) {
            return d1.toDateString() === d2.toDateString();
        }

        // ============ ACTIONS ============
        async function loadWeather() {
            state.loading = true;
            state.error = null;
            state.errorCode = null;
            render();
            
            try {
                if (!state.location) {
                    // Check if we have saved manual location
                    if (state.locationSource === 'manual' && state.manualLat && state.manualLon) {
                        state.location = { lat: state.manualLat, lon: state.manualLon };
                        state.locationName = state.manualLocationName || 'Manual Location';
                    }
                    // Check if we have saved auto/GPS location
                    else if (state.autoLat && state.autoLon) {
                        state.location = { lat: state.autoLat, lon: state.autoLon };
                        state.locationName = state.autoLocationName || 'Your Location';
                        state.locationSource = 'auto';
                        state.hasPermission = true;
                    }
                    // No saved location - try GPS and save it
                    else {
                        state.location = await getCurrentLocation();
                        state.hasPermission = true;
                        state.locationSource = 'auto';
                        state.locationName = await getLocationName(state.location.lat, state.location.lon);

                        // Save GPS location for future sessions
                        localStorage.setItem('autoLat', state.location.lat.toString());
                        localStorage.setItem('autoLon', state.location.lon.toString());
                        localStorage.setItem('autoLocationName', state.locationName);
                    }
                }
                
                state.weather = await fetchWeather(state.location.lat, state.location.lon, state.selectedDate);
                state.outfit = getOutfitRecommendation(state.weather);

                // Immediately use default image for this temperature bracket AND weather condition
                const tempBracket = getTempBracket(state.weather.feelsLike);
                currentHeroImageUrl = getDefaultHeroImage(tempBracket, state.weather.weatherCode);

                state.loading = false;

                // Then load specific hero image from Supabase in background
                loadHeroImage();
            } catch (err) {
                state.loading = false;
                state.error = err.message || 'Something went wrong';
                state.errorCode = err.code || null;
                if (err.code === 1) {
                    state.error = 'Location permission denied';
                    state.errorCode = 'PERMISSION_DENIED';
                } else if (err.code === 2) {
                    state.error = 'Unable to determine your location';
                    state.errorCode = 'POSITION_UNAVAILABLE';
                } else if (err.code === 3) {
                    state.error = 'Location request timed out';
                    state.errorCode = 'TIMEOUT';
                }
            }
            render();
        }

        function toggleUnit() {
            setUnit(!state.useCelsius);
        }

        function setUnit(useCelsius) {
            state.useCelsius = useCelsius;
            localStorage.setItem('useCelsius', state.useCelsius);
            updateUnitSelector();
            loadWeather();
        }

        function updateUnitSelector() {
            const selector = document.getElementById('unitSelector');
            if (selector) {
                selector.querySelectorAll('.unit-opt').forEach(opt => {
                    const isC = opt.dataset.unit === 'c';
                    opt.classList.toggle('active', isC === state.useCelsius);
                });
            }
        }

        function setGender(g) {
            // Toggle: if same gender clicked, deselect (back to 'all'/unisex)
            state.gender = (state.gender === g) ? 'all' : g;
            localStorage.setItem('gender', state.gender);
            updateSettingsGenderSelector();
            render();
            // Reload hero image for new gender (combination ID includes gender)
            loadHeroImage();
        }

        function setComfort(val) {
            state.comfort = val;
            localStorage.setItem('comfort', val);
            if (state.weather) {
                state.outfit = getOutfitRecommendation(state.weather);
                // Reload hero image since outfit changed (affects combination ID)
                loadHeroImage();
            }
            renderComfortSelector();
            render();
        }

        function prevDay() {
            const prev = new Date(state.selectedDate);
            prev.setDate(prev.getDate() - 1);
            const now = new Date();
            if (prev >= new Date(now.setHours(0,0,0,0))) {
                state.selectedDate = prev;
                loadWeather();
            }
        }

        function nextDay() {
            const next = new Date(state.selectedDate);
            next.setDate(next.getDate() + 1);
            const maxDate = new Date();
            maxDate.setDate(maxDate.getDate() + 6);
            if (next <= maxDate) {
                state.selectedDate = next;
                loadWeather();
            }
        }

        function resetToNow() {
            state.selectedDate = new Date();
            loadWeather();
        }

        function setHour(hour) {
            state.selectedDate.setHours(hour, 0, 0, 0);
            // Reset hero image to placeholder while new one loads
            currentHeroImageUrl = PLACEHOLDER_IMAGE;
            loadWeather();
            closeTimePicker();
        }

        function setDate(daysFromNow) {
            const newDate = new Date();
            newDate.setDate(newDate.getDate() + daysFromNow);
            newDate.setHours(state.selectedDate.getHours(), 0, 0, 0);
            state.selectedDate = newDate;
            // Reset hero image to placeholder while new one loads
            currentHeroImageUrl = PLACEHOLDER_IMAGE;
            closeDatePicker();
            loadWeather();
        }

        // Pickers
        function openDatePicker() {
            const container = document.getElementById('datePickerGrid');
            const today = new Date();
            let html = '';
            
            for (let i = 0; i <= 6; i++) {
                const d = new Date(today);
                d.setDate(d.getDate() + i);
                const label = i === 0 ? 'Today' : i === 1 ? 'Tomorrow' : d.toLocaleDateString('en-US', { weekday: 'short' });
                const subLabel = d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
                const isActive = isSameDay(state.selectedDate, d);
                html += `<div class="picker-item ${isActive ? 'active' : ''}" onclick="setDate(${i})">
                    ${label}
                    <div class="picker-sublabel">${subLabel}</div>
                </div>`;
            }
            
            container.innerHTML = html;
            document.getElementById('datePicker').classList.add('active');
        }
        
        function closeDatePicker() {
            document.getElementById('datePicker').classList.remove('active');
        }
        
        function openTimePicker() {
            const container = document.getElementById('timePickerGrid');
            const hours = [5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21];
            
            container.innerHTML = hours.map(h => {
                const label = h < 12 ? `${h} AM` : h === 12 ? '12 PM' : `${h-12} PM`;
                const isActive = state.selectedDate.getHours() === h;
                return `<div class="picker-item ${isActive ? 'active' : ''}" onclick="setHour(${h})">${label}</div>`;
            }).join('');
            
            document.getElementById('timePickerOverlay').classList.add('active');
        }
        
        function closeTimePicker() {
            document.getElementById('timePickerOverlay').classList.remove('active');
        }

        function openSettings() {
            document.getElementById('settingsModal').classList.add('active');
            updateUnitSelector();
            renderComfortSelector();
            updateSettingsGenderSelector();
        }

        function updateSettingsGenderSelector() {
            const selector = document.getElementById('settingsGenderSelector');
            if (selector) {
                selector.querySelectorAll('.gender-opt').forEach(opt => {
                    opt.classList.toggle('active', opt.dataset.gender === state.gender);
                });
            }
        }

        function closeSettings() {
            document.getElementById('settingsModal').classList.remove('active');
        }

        function renderComfortSelector() {
            const container = document.getElementById('comfortSelector');
            const options = [
                { val: -10, label: '🥶' },
                { val: -5, label: 'Get cold' },
                { val: 0, label: 'Neither' },
                { val: 5, label: 'Overheat' },
                { val: 10, label: '🥵' }
            ];
            container.innerHTML = options.map(o =>
                `<div class="comfort-opt ${state.comfort === o.val ? 'active' : ''}" onclick="setComfort(${o.val})">${o.label}</div>`
            ).join('');
        }

        // Shop Modal
        function openShop() {
            const container = document.getElementById('shopItems');
            container.innerHTML = state.outfit.items.map(item =>
                `<div class="shop-item" onclick="shopItem(${JSON.stringify(item).replace(/"/g, '&quot;')})">
                    <span class="shop-item-icon">${item.icon}</span>
                    <div class="shop-item-info">
                        <div class="shop-item-name">${item.name}</div>
                        <div class="shop-item-desc">${item.desc}</div>
                    </div>
                    <span class="shop-item-arrow">→</span>
                </div>`
            ).join('');
            document.getElementById('shopModal').classList.add('active');
        }

        function closeShop() {
            document.getElementById('shopModal').classList.remove('active');
        }

        function shopItem(item) {
            window.open(buildAmazonLink(item.search), '_blank');
        }

        function shopAll() {
            // Build search term based on current weather conditions
            const tempBracket = getTempBracket(state.weather.feelsLike);
            const weatherDesc = getWeatherDescription(state.weather.weatherCode);
            const searchTerm = `${weatherDesc} weather running gear`;
            window.open(buildAmazonLink(searchTerm), '_blank');
        }

        function getWeatherDescription(code) {
            if (code === 0 || code === 1) return 'sunny';
            if (code >= 2 && code <= 3) return 'cloudy';
            if (code >= 45 && code <= 48) return 'foggy';
            if (code >= 51 && code <= 67) return 'rainy';
            if (code >= 71 && code <= 77) return 'cold weather';
            if (code >= 80 && code <= 82) return 'rainy';
            if (code >= 85 && code <= 86) return 'snowy';
            if (code >= 95) return 'stormy';
            return 'all weather';
        }

        // ============ LOCATION MODAL ============
        function openLocationModal() {
            renderLocationModal();
            document.getElementById('locationModal').classList.add('active');
        }
        
        function closeLocationModal() {
            document.getElementById('locationModal').classList.remove('active');
            state.locationResults = [];
            state.locationSearching = false;
            state.locationSearchQuery = '';
        }
        
        function renderLocationModal() {
            const content = document.getElementById('locationModalContent');
            const isUsingManual = state.locationSource === 'manual' && state.manualLat;
            const isUsingAuto = state.locationSource === 'auto' && state.hasPermission;
            
            let platformHelp = '';
            if (isIOS() && isSafari()) {
                platformHelp = `
                    <div class="help-steps" style="margin-top: 12px; margin-bottom: 8px;">
                        <div class="help-step">
                            <span class="step-num">1</span>
                            <span>Open <strong>Settings</strong> → <strong>Safari</strong></span>
                        </div>
                        <div class="help-step">
                            <span class="step-num">2</span>
                            <span>Tap <strong>Location</strong> → Select <strong>Allow</strong></span>
                        </div>
                    </div>
                    <div style="font-size: 11px; color: var(--text-muted); margin-bottom: 8px;">
                        Also check: Settings → Privacy & Security → Location Services → Safari
                    </div>
                `;
            }
            
            content.innerHTML = `
                <!-- Use Current Location Option -->
                <div class="location-option ${isUsingAuto ? 'active' : ''}" onclick="requestGPSLocation()">
                    <div class="location-option-icon">
                        <svg viewBox="0 0 24 24" fill="white" width="24" height="24">
                            <path d="M12 8c-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4-1.79-4-4-4zm8.94 3c-.46-4.17-3.77-7.48-7.94-7.94V1h-2v2.06C6.83 3.52 3.52 6.83 3.06 11H1v2h2.06c.46 4.17 3.77 7.48 7.94 7.94V23h2v-2.06c4.17-.46 7.48-3.77 7.94-7.94H23v-2h-2.06zM12 19c-3.87 0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z"/>
                        </svg>
                    </div>
                    <div class="location-option-text">
                        <div class="location-option-title">
                            Use Current Location
                            ${isUsingAuto ? '<span class="current-location-badge">✓ Active</span>' : ''}
                        </div>
                        <div class="location-option-desc">
                            ${state.hasPermission ? 'GPS location enabled' : 'Requires location permission'}
                        </div>
                    </div>
                </div>
                
                ${!state.hasPermission && platformHelp ? platformHelp : ''}
                
                <div class="location-divider">or enter manually</div>
                
                <!-- Manual Location Search -->
                <div class="manual-search-section">
                    <div class="search-input-wrapper">
                        <span class="search-icon">🔍</span>
                        <input
                            type="text"
                            class="search-input"
                            id="locationSearchInput"
                            placeholder="Enter city or ZIP code..."
                            value="${state.locationSearchQuery || ''}"
                            oninput="handleLocationSearch(this.value)"
                            onkeydown="if(event.key==='Enter')handleLocationSearch(this.value)"
                        >
                        ${state.locationSearching ? '<div class="search-spinner"></div>' : ''}
                    </div>
                    
                    <div class="location-results" id="locationResults">
                        ${renderLocationResults()}
                    </div>
                </div>
                
                ${isUsingManual ? `
                    <div style="margin-top: 16px; padding: 12px; background: rgba(0,121,107,0.1); border-radius: 12px; text-align: center;">
                        <div style="font-size: 13px; color: var(--text-secondary);">Currently using:</div>
                        <div style="font-size: 15px; font-weight: 600; color: var(--primary); margin-top: 4px;">
                            📍 ${state.manualLocationName || 'Manual location'}
                        </div>
                    </div>
                ` : ''}
            `;
        }
        
        function renderLocationResults() {
            if (!state.locationResults.length) return '';
            
            return state.locationResults.map(loc => `
                <div class="location-result" onclick='selectManualLocation(${JSON.stringify(loc).replace(/'/g, "\\'")})'>
                    <div class="location-result-name">${loc.name}</div>
                    <div class="location-result-detail">${loc.fullName}</div>
                </div>
            `).join('');
        }
        
        let searchTimeout = null;
        async function handleLocationSearch(query) {
            if (searchTimeout) clearTimeout(searchTimeout);

            state.locationSearchQuery = query || '';

            if (!query || query.length < 2) {
                state.locationResults = [];
                state.locationSearching = false;
                // Only update results area, not entire modal
                const resultsDiv = document.getElementById('locationResults');
                if (resultsDiv) resultsDiv.innerHTML = '';
                return;
            }

            // Debounce search
            searchTimeout = setTimeout(async () => {
                state.locationSearching = true;
                // Show spinner without re-rendering (preserve input)
                const wrapper = document.querySelector('.search-input-wrapper');
                if (wrapper && !wrapper.querySelector('.search-spinner')) {
                    wrapper.insertAdjacentHTML('beforeend', '<div class="search-spinner"></div>');
                }

                try {
                    // Use Nominatim for geocoding
                    const response = await fetch(
                        `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(query)}&format=json&limit=8&addressdetails=1`
                    );

                    if (!response.ok) throw new Error('Search failed');

                    const data = await response.json();

                    state.locationResults = data.map(item => {
                        const addr = item.address || {};
                        const city = addr.city || addr.town || addr.village || addr.municipality || addr.suburb || '';
                        const stateRegion = addr.state || addr.region || '';
                        const country = addr.country || '';
                        const countryCode = addr.country_code || '';

                        let name = city || item.display_name.split(',')[0];
                        let fullName = '';

                        if (city && stateRegion) {
                            fullName = `${stateRegion}, ${country}`;
                        } else {
                            fullName = item.display_name.split(',').slice(1, 3).join(',').trim();
                        }

                        return {
                            name: name,
                            fullName: fullName,
                            lat: parseFloat(item.lat),
                            lon: parseFloat(item.lon),
                            displayName: `${name}${stateRegion ? ', ' + stateRegion : ''}`,
                            isUSA: countryCode === 'us'
                        };
                    }).sort((a, b) => {
                        // Prioritize USA results at the top
                        if (a.isUSA && !b.isUSA) return -1;
                        if (!a.isUSA && b.isUSA) return 1;
                        return 0;
                    }).slice(0, 5);
                    
                } catch (err) {
                    console.error('Location search error:', err);
                    state.locationResults = [];
                }

                state.locationSearching = false;
                // Remove spinner
                const spinner = document.querySelector('.search-input-wrapper .search-spinner');
                if (spinner) spinner.remove();
                // Only update results area, preserve input
                const resultsDiv = document.getElementById('locationResults');
                if (resultsDiv) {
                    resultsDiv.innerHTML = renderLocationResults();
                }
            }, 300);
        }
        
        function selectManualLocation(loc) {
            state.locationSource = 'manual';
            state.manualLat = loc.lat;
            state.manualLon = loc.lon;
            state.manualLocationName = loc.displayName;
            state.location = { lat: loc.lat, lon: loc.lon };
            state.locationName = loc.displayName;

            // Reset hero image to placeholder while new one loads
            currentHeroImageUrl = PLACEHOLDER_IMAGE;

            // Save to localStorage
            localStorage.setItem('locationSource', 'manual');
            localStorage.setItem('manualLat', loc.lat.toString());
            localStorage.setItem('manualLon', loc.lon.toString());
            localStorage.setItem('manualLocationName', loc.displayName);

            closeLocationModal();
            loadWeather();
        }
        
        async function requestGPSLocation() {
            // Clear all saved locations to force fresh GPS fetch
            state.locationSource = 'auto';
            state.location = null;
            state.autoLat = null;
            state.autoLon = null;
            state.autoLocationName = null;

            // Reset hero image to placeholder while new one loads
            currentHeroImageUrl = PLACEHOLDER_IMAGE;

            localStorage.setItem('locationSource', 'auto');
            localStorage.removeItem('manualLat');
            localStorage.removeItem('manualLon');
            localStorage.removeItem('manualLocationName');
            localStorage.removeItem('autoLat');
            localStorage.removeItem('autoLon');
            localStorage.removeItem('autoLocationName');

            closeLocationModal();

            // Reset and reload - will fetch fresh GPS and save it
            state.error = null;
            state.errorCode = null;
            state.hasPermission = false;
            loadWeather();
        }
        
        // Search from the error page ZIP code input
        let errorSearchTimeout = null;
        async function searchFromErrorPage() {
            const input = document.getElementById('errorLocationInput');
            const query = input?.value?.trim();
            const resultsDiv = document.getElementById('errorLocationResults');
            
            if (!query || query.length < 2) {
                if (resultsDiv) resultsDiv.innerHTML = '';
                return;
            }
            
            // Show loading
            if (resultsDiv) {
                resultsDiv.innerHTML = '<div style="text-align: center; padding: 12px; color: var(--text-secondary);">Searching...</div>';
            }
            
            try {
                const response = await fetch(
                    `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(query)}&format=json&limit=8&addressdetails=1`
                );

                if (!response.ok) throw new Error('Search failed');

                const data = await response.json();

                if (data.length === 0) {
                    if (resultsDiv) {
                        resultsDiv.innerHTML = '<div style="text-align: center; padding: 12px; color: var(--text-secondary);">No locations found. Try a different search.</div>';
                    }
                    return;
                }

                const results = data.map(item => {
                    const addr = item.address || {};
                    const city = addr.city || addr.town || addr.village || addr.municipality || addr.suburb || '';
                    const stateRegion = addr.state || addr.region || '';
                    const country = addr.country || '';
                    const countryCode = addr.country_code || '';

                    let name = city || item.display_name.split(',')[0];
                    let fullName = `${stateRegion ? stateRegion + ', ' : ''}${country}`;

                    return {
                        name: name,
                        fullName: fullName,
                        lat: parseFloat(item.lat),
                        lon: parseFloat(item.lon),
                        displayName: `${name}${stateRegion ? ', ' + stateRegion : ''}`,
                        isUSA: countryCode === 'us'
                    };
                }).sort((a, b) => {
                    // Prioritize USA results at the top
                    if (a.isUSA && !b.isUSA) return -1;
                    if (!a.isUSA && b.isUSA) return 1;
                    return 0;
                }).slice(0, 5);
                
                if (resultsDiv) {
                    resultsDiv.innerHTML = results.map(loc => `
                        <div class="location-result" onclick='selectLocationFromError(${JSON.stringify(loc).replace(/'/g, "\\'")})'>
                            <div class="location-result-name">${loc.name}</div>
                            <div class="location-result-detail">${loc.fullName}</div>
                        </div>
                    `).join('');
                }
                
            } catch (err) {
                console.error('Location search error:', err);
                if (resultsDiv) {
                    resultsDiv.innerHTML = '<div style="text-align: center; padding: 12px; color: var(--text-secondary);">Search failed. Please try again.</div>';
                }
            }
        }
        
        function selectLocationFromError(loc) {
            selectManualLocation(loc);
        }

        // ============ PULL TO REFRESH ============
        let pullStartY = 0;
        let pullCurrentY = 0;
        let isPulling = false;
        let isRefreshing = false;

        function initPullToRefresh() {
            const app = document.getElementById('app');
            if (!app) return;

            app.addEventListener('touchstart', handlePullStart, { passive: true });
            app.addEventListener('touchmove', handlePullMove, { passive: false });
            app.addEventListener('touchend', handlePullEnd, { passive: true });
        }

        function handlePullStart(e) {
            if (window.scrollY === 0 && !isRefreshing) {
                pullStartY = e.touches[0].clientY;
                isPulling = true;
            }
        }

        function handlePullMove(e) {
            if (!isPulling || isRefreshing) return;

            pullCurrentY = e.touches[0].clientY;
            const pullDistance = pullCurrentY - pullStartY;

            if (pullDistance > 0 && window.scrollY === 0) {
                e.preventDefault();
                const indicator = document.querySelector('.pull-refresh-indicator');
                if (indicator) {
                    const progress = Math.min(pullDistance / 100, 1);
                    indicator.classList.add('pulling');
                    indicator.style.transform = `translateX(-50%) translateY(${-60 + pullDistance * 0.8}px)`;
                    indicator.querySelector('svg').style.transform = `rotate(${progress * 180}deg)`;
                }
            }
        }

        function handlePullEnd() {
            if (!isPulling) return;
            isPulling = false;

            const pullDistance = pullCurrentY - pullStartY;
            const indicator = document.querySelector('.pull-refresh-indicator');

            if (pullDistance > 80 && !isRefreshing) {
                isRefreshing = true;
                // Reset hero image to placeholder while new one loads
                currentHeroImageUrl = PLACEHOLDER_IMAGE;
                if (indicator) {
                    indicator.classList.remove('pulling');
                    indicator.classList.add('refreshing');
                }

                loadWeather().then(() => {
                    setTimeout(() => {
                        isRefreshing = false;
                        if (indicator) {
                            indicator.classList.remove('refreshing');
                            indicator.style.transform = 'translateX(-50%) translateY(-60px)';
                        }
                    }, 500);
                });
            } else {
                if (indicator) {
                    indicator.classList.remove('pulling');
                    indicator.style.transform = 'translateX(-50%) translateY(-60px)';
                }
            }

            pullStartY = 0;
            pullCurrentY = 0;
        }

        // ============ HERO PARALLAX ============
        function initHeroParallax() {
            window.addEventListener('scroll', handleParallax, { passive: true });
        }

        function handleParallax() {
            const heroImage = document.querySelector('.hero-image');
            const hero = document.querySelector('.hero');
            if (!heroImage || !hero) return;

            const scrollY = window.scrollY;
            const heroHeight = hero.offsetHeight;

            if (scrollY < heroHeight) {
                const parallaxOffset = scrollY * 0.4;
                heroImage.style.transform = `scale(1.05) translateY(${parallaxOffset}px)`;
            }
        }

        // ============ MODAL SWIPE TO CLOSE ============
        function initModalSwipeToClose() {
            const modals = [
                { overlay: 'settingsModal', close: closeSettings },
                { overlay: 'shopModal', close: closeShop },
                { overlay: 'locationModal', close: closeLocationModal },
                { overlay: 'outfitDetailModal', close: closeOutfitDetail },
                { overlay: 'weatherDetailOverlay', close: closeWeatherDetail }
            ];

            modals.forEach(({ overlay, close }) => {
                const overlayEl = document.getElementById(overlay);
                if (!overlayEl) return;

                let startY = 0;
                let currentY = 0;
                let isDragging = false;
                const modal = overlayEl.querySelector('.modal, .shop-modal, .location-modal, .outfit-detail-modal, .weather-detail');

                if (!modal) return;

                modal.addEventListener('touchstart', (e) => {
                    // Only start drag from top area (handle region)
                    const touch = e.touches[0];
                    const rect = modal.getBoundingClientRect();
                    if (touch.clientY - rect.top < 60) { // Top 60px is draggable
                        startY = touch.clientY;
                        isDragging = true;
                        modal.style.transition = 'none';
                    }
                }, { passive: true });

                modal.addEventListener('touchmove', (e) => {
                    if (!isDragging) return;
                    currentY = e.touches[0].clientY;
                    const deltaY = currentY - startY;
                    if (deltaY > 0) { // Only allow dragging down
                        modal.style.transform = `translateY(${deltaY}px)`;
                    }
                }, { passive: true });

                modal.addEventListener('touchend', () => {
                    if (!isDragging) return;
                    isDragging = false;
                    modal.style.transition = 'transform 0.3s ease';
                    const deltaY = currentY - startY;
                    if (deltaY > 100) { // Threshold to close
                        modal.style.transform = `translateY(100%)`;
                        setTimeout(() => {
                            close();
                            modal.style.transform = '';
                        }, 300);
                    } else {
                        modal.style.transform = '';
                    }
                    startY = 0;
                    currentY = 0;
                }, { passive: true });
            });
        }

        // ============ WEATHER DETAIL POPUP ============
        let activeWeatherDetail = null;

        function showWeatherDetail(type) {
            if (!state.weather) return;

            const w = state.weather;
            const unit = state.useCelsius ? '°C' : '°F';
            const windUnit = state.useCelsius ? 'km/h' : 'mph';

            let title, icon, details;

            switch(type) {
                case 'condition':
                    title = getWeatherDescription(w.weatherCode);
                    icon = getWeatherIcon(w.weatherCode);
                    details = [
                        { label: 'Condition', value: getWeatherDescription(w.weatherCode) },
                        { label: 'Cloud Cover', value: `${w.cloudCover || 0}%` },
                        { label: 'Visibility', value: 'Good' },
                        { label: 'Precipitation', value: `${w.precipProb || 0}%` }
                    ];
                    break;
                case 'wind':
                    title = 'Wind';
                    icon = `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M14.5 17c0 1.65-1.35 3-3 3s-3-1.35-3-3h2c0 .55.45 1 1 1s1-.45 1-1-.45-1-1-1H2v-2h9.5c1.65 0 3 1.35 3 3zM19 6.5C19 4.57 17.43 3 15.5 3S12 4.57 12 6.5h2c0-.83.67-1.5 1.5-1.5s1.5.67 1.5 1.5S16.33 8 15.5 8H2v2h13.5c1.93 0 3.5-1.57 3.5-3.5zm-.5 4.5H2v2h16.5c.83 0 1.5.67 1.5 1.5s-.67 1.5-1.5 1.5v2c1.93 0 3.5-1.57 3.5-3.5S20.43 11 18.5 11z"/></svg>`;
                    details = [
                        { label: 'Speed', value: `${Math.round(w.windSpeed)} ${windUnit}` },
                        { label: 'Gusts', value: `${Math.round(w.windGusts || w.windSpeed * 1.3)} ${windUnit}` },
                        { label: 'Direction', value: getWindDirection(w.windDirection || 0) },
                        { label: 'Feels Like Impact', value: w.windSpeed > 20 ? 'Significant' : 'Minimal' }
                    ];
                    break;
                case 'humidity':
                    title = 'Humidity';
                    icon = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M4 14c0-4 3-7 5-9 2 2 5 5 5 9a5 5 0 01-10 0z"/><path d="M8 14h4"/><path d="M7 17h6"/></svg>`;
                    details = [
                        { label: 'Relative Humidity', value: `${w.humidity}%` },
                        { label: 'Dew Point', value: `${Math.round(w.dewPoint || w.temp - 5)}${unit}` },
                        { label: 'Comfort Level', value: w.humidity > 70 ? 'Muggy' : w.humidity < 30 ? 'Dry' : 'Comfortable' },
                        { label: 'Sweat Impact', value: w.humidity > 60 ? 'Slower evaporation' : 'Normal' }
                    ];
                    break;
                case 'precipitation':
                    title = 'Precipitation';
                    icon = `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 2c-5.33 4.55-8 8.48-8 11.8 0 4.98 3.8 8.2 8 8.2s8-3.22 8-8.2c0-3.32-2.67-7.25-8-11.8zm0 18c-3.35 0-6-2.57-6-6.2 0-2.34 1.95-5.44 6-9.14 4.05 3.7 6 6.79 6 9.14 0 3.63-2.65 6.2-6 6.2z"/></svg>`;
                    const precipProb = w.precipitationProbability || 0;
                    const precipAmt = w.precipitation || 0;
                    details = [
                        { label: 'Chance of Rain', value: `${Math.round(precipProb)}%` },
                        { label: 'Expected Amount', value: precipAmt > 0 ? `${precipAmt.toFixed(1)} ${state.useCelsius ? 'mm' : 'in'}` : 'None' },
                        { label: 'Recommendation', value: precipProb > 50 ? 'Bring rain jacket' : precipProb > 20 ? 'Consider rain gear' : 'Low risk' },
                        { label: 'Running Impact', value: precipProb > 70 ? 'Plan for wet conditions' : 'Minimal impact expected' }
                    ];
                    break;
                case 'uv':
                    title = 'UV Index';
                    icon = `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 7c-2.76 0-5 2.24-5 5s2.24 5 5 5 5-2.24 5-5-2.24-5-5-5zM2 13h2c.55 0 1-.45 1-1s-.45-1-1-1H2c-.55 0-1 .45-1 1s.45 1 1 1zm18 0h2c.55 0 1-.45 1-1s-.45-1-1-1h-2c-.55 0-1 .45-1 1s.45 1 1 1zM11 2v2c0 .55.45 1 1 1s1-.45 1-1V2c0-.55-.45-1-1-1s-1 .45-1 1zm0 18v2c0 .55.45 1 1 1s1-.45 1-1v-2c0-.55-.45-1-1-1s-1 .45-1 1z"/></svg>`;
                    const uvLevel = w.uvIndex <= 2 ? 'Low' : w.uvIndex <= 5 ? 'Moderate' : w.uvIndex <= 7 ? 'High' : 'Very High';
                    details = [
                        { label: 'UV Index', value: Math.round(w.uvIndex) },
                        { label: 'Level', value: uvLevel },
                        { label: 'Protection', value: w.uvIndex > 3 ? 'Sunscreen recommended' : 'Minimal needed' },
                        { label: 'Peak Hours', value: '10am - 4pm' }
                    ];
                    break;
            }

            activeWeatherDetail = type;

            const popup = document.getElementById('weatherDetailPopup');
            const overlay = document.getElementById('sheetOverlay');

            popup.innerHTML = `
                <div class="weather-detail-header">
                    <div class="weather-detail-icon">${icon}</div>
                    <div class="weather-detail-title">${title}</div>
                </div>
                <div class="weather-detail-grid">
                    ${details.map(d => `
                        <div class="weather-detail-item">
                            <div class="weather-detail-label">${d.label}</div>
                            <div class="weather-detail-value">${d.value}</div>
                        </div>
                    `).join('')}
                </div>
            `;

            popup.classList.add('active');
            overlay.classList.add('active');
        }

        function getWindDirection(degrees) {
            const directions = ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW'];
            const index = Math.round(degrees / 45) % 8;
            return directions[index];
        }

        function closeWeatherDetail() {
            const popup = document.getElementById('weatherDetailPopup');
            const overlay = document.getElementById('sheetOverlay');
            popup.classList.remove('active');
            overlay.classList.remove('active');
            activeWeatherDetail = null;
        }

        // ============ OUTFIT DETAIL SHEET ============
        let activeOutfitDetail = null;

        function showOutfitDetail(item) {
            activeOutfitDetail = item;

            const sheet = document.getElementById('outfitDetailSheet');
            const overlay = document.getElementById('sheetOverlay');

            const categoryDescriptions = {
                hat: 'Protects from sun and helps regulate head temperature during your run.',
                top: 'Your core layer - choose based on temperature and personal preference.',
                bottom: 'Leg coverage for comfort and protection.',
                socks: 'Proper running socks prevent blisters and wick moisture.',
                shoes: 'The foundation of every run - match to conditions.',
                accessories: 'Extra gear to enhance comfort and performance.',
                outerwear: 'Protection from wind, rain, or cold.',
                gloves: 'Keep hands warm and functional in cold weather.',
                sunglasses: 'Protect eyes from sun and debris.'
            };

            sheet.innerHTML = `
                <div class="outfit-detail-handle"></div>
                <div class="outfit-detail-header">
                    <div class="outfit-detail-icon cat-${item.category}">
                        ${getCategoryIcon(item.category)}
                    </div>
                    <div>
                        <div class="outfit-detail-name">${item.name}</div>
                        <div class="outfit-detail-category">${item.category}</div>
                    </div>
                </div>
                <div class="outfit-detail-desc">
                    ${item.desc || categoryDescriptions[item.category] || 'Essential gear for your run.'}
                </div>
                <div class="outfit-detail-actions">
                    <button class="outfit-detail-btn secondary" onclick="closeOutfitDetail()">
                        Close
                    </button>
                    <button class="outfit-detail-btn primary" onclick="shopItem(${JSON.stringify(item).replace(/"/g, '&quot;')})">
                        <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><path d="M18 6h-2c0-2.21-1.79-4-4-4S8 3.79 8 6H6c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm-6-2c1.1 0 2 .9 2 2h-4c0-1.1.9-2 2-2zm6 16H6V8h2v2c0 .55.45 1 1 1s1-.45 1-1V8h4v2c0 .55.45 1 1 1s1-.45 1-1V8h2v12z"/></svg>
                        Shop
                    </button>
                </div>
            `;

            sheet.classList.add('active');
            overlay.classList.add('active');
        }

        function closeOutfitDetail() {
            const sheet = document.getElementById('outfitDetailSheet');
            const overlay = document.getElementById('sheetOverlay');
            sheet.classList.remove('active');
            overlay.classList.remove('active');
            activeOutfitDetail = null;
        }

        // ============ SHARE FEATURE ============
        async function shareOutfit() {
            if (!state.weather || !state.outfit) return;

            const w = state.weather;
            const unit = state.useCelsius ? '°C' : '°F';
            const tempBracket = getTempBracket(w.feelsLike);

            // Build share text
            const outfitList = state.outfit.items.map(item => `• ${item.name}`).join('\n');
            const shareText = `🏃 RunWear Recommendation

📍 ${state.locationName}
🌡️ Feels like ${Math.round(w.feelsLike)}${unit} (${tempBracket})
${getWeatherIcon(w.weatherCode)} ${getWeatherDescription(w.weatherCode)}

What to wear:
${outfitList}

${state.outfit.tips.length > 0 ? `💡 Tip: ${state.outfit.tips[0]}` : ''}

Get your personalized running outfit at runwear.ai`;

            // Try native share API first
            if (navigator.share) {
                try {
                    await navigator.share({
                        title: 'RunWear - Running Outfit Recommendation',
                        text: shareText,
                        url: 'https://runwear.ai'
                    });
                    return;
                } catch (e) {
                    // User cancelled or share failed, fall through to clipboard
                    if (e.name !== 'AbortError') {
                        console.error('Share failed:', e);
                    }
                }
            }

            // Fallback: copy to clipboard
            try {
                await navigator.clipboard.writeText(shareText);
                showToast('Copied to clipboard!');
            } catch (e) {
                console.error('Clipboard write failed:', e);
                showToast('Could not share');
            }
        }

        // Toast notification
        function showToast(message) {
            const existing = document.querySelector('.toast');
            if (existing) existing.remove();

            const toast = document.createElement('div');
            toast.className = 'toast';
            toast.textContent = message;
            document.body.appendChild(toast);

            // Trigger animation
            requestAnimationFrame(() => {
                toast.classList.add('visible');
            });

            // Remove after 2 seconds
            setTimeout(() => {
                toast.classList.remove('visible');
                setTimeout(() => toast.remove(), 300);
            }, 2000);
        }

        // ============ TIP CARD EXPANSION ============
        function toggleTipCard(el) {
            el.classList.toggle('expanded');
        }

        // ============ CLOSE ALL SHEETS ============
        function closeAllSheets() {
            closeWeatherDetail();
            closeOutfitDetail();
        }

        // ============ RENDER ============
        function render() {
            const app = document.getElementById('app');
            
            if (state.loading) {
                app.innerHTML = `
                    <!-- Pull to Refresh Indicator -->
                    <div class="pull-refresh-indicator">
                        <svg viewBox="0 0 24 24"><path d="M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"/></svg>
                    </div>

                    <!-- Skeleton Hero -->
                    <div class="skeleton-hero">
                        <div class="skeleton-hero-content">
                            <div class="skeleton skeleton-datetime"></div>
                            <div class="skeleton skeleton-temp"></div>
                            <div class="skeleton-pills">
                                <div class="skeleton skeleton-pill"></div>
                                <div class="skeleton skeleton-pill"></div>
                                <div class="skeleton skeleton-pill"></div>
                            </div>
                        </div>
                    </div>

                    <!-- Skeleton Outfit Section -->
                    <div class="skeleton-section">
                        <div class="skeleton skeleton-section-title"></div>
                        <div class="skeleton skeleton-card"></div>
                        <div class="skeleton skeleton-card"></div>
                        <div class="skeleton skeleton-card"></div>
                        <div class="skeleton skeleton-card"></div>
                    </div>

                    <!-- Sheet Overlay -->
                    <div class="sheet-overlay" id="sheetOverlay" onclick="closeAllSheets()"></div>
                    <div class="weather-detail-popup" id="weatherDetailPopup"></div>
                    <div class="outfit-detail-sheet" id="outfitDetailSheet"></div>
                `;
                return;
            }
            
            if (state.error) {
                // Check if it's a location-related error
                const isLocationError = state.errorCode === 'PERMISSION_DENIED' || 
                                        state.errorCode === 'POSITION_UNAVAILABLE' || 
                                        state.errorCode === 'TIMEOUT';
                
                if (isLocationError) {
                    // Show friendly location setup screen instead of error
                    app.innerHTML = `
                        <div class="location-setup-screen">
                            <div class="location-setup-header">
                                <div class="logo" style="margin-bottom: 8px;">Run<span>Wear</span></div>
                                <div class="location-setup-icon">📍</div>
                                <h2 class="location-setup-title">Set Your Location</h2>
                                <p class="location-setup-desc">We need your location to show weather-based outfit recommendations</p>
                            </div>
                            
                            <div class="location-setup-options">
                                <!-- ZIP/City Search (Primary) -->
                                <div class="location-setup-section">
                                    <div class="location-input-wrapper" style="margin-bottom: 0;">
                                        <input 
                                            type="text" 
                                            class="location-input" 
                                            id="errorLocationInput"
                                            placeholder="Enter city or ZIP code"
                                            onkeydown="if(event.key==='Enter')searchFromErrorPage()"
                                            autofocus
                                        >
                                        <button class="location-submit" onclick="searchFromErrorPage()">
                                            <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
                                                <path d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/>
                                            </svg>
                                        </button>
                                    </div>
                                    <div class="location-results" id="errorLocationResults"></div>
                                </div>
                                
                                <div class="location-setup-divider">
                                    <span>or</span>
                                </div>
                                
                                <!-- Use Current Location Button -->
                                <button class="location-gps-button" onclick="showGPSHelp()">
                                    <div class="location-gps-icon">
                                        <svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor">
                                            <path d="M12 8c-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4-1.79-4-4-4zm8.94 3c-.46-4.17-3.77-7.48-7.94-7.94V1h-2v2.06C6.83 3.52 3.52 6.83 3.06 11H1v2h2.06c.46 4.17 3.77 7.48 7.94 7.94V23h2v-2.06c4.17-.46 7.48-3.77 7.94-7.94H23v-2h-2.06zM12 19c-3.87 0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z"/>
                                        </svg>
                                    </div>
                                    <div class="location-gps-text">
                                        <span class="location-gps-title">Use Current Location</span>
                                        <span class="location-gps-subtitle">Requires location permission</span>
                                    </div>
                                    <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor" style="opacity: 0.4;">
                                        <path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z"/>
                                    </svg>
                                </button>
                            </div>
                            
                            <!-- GPS Help Section (hidden by default, shown when GPS button clicked) -->
                            <div class="location-gps-help" id="gpsHelpSection" style="display: none;">
                                ${renderGPSHelp()}
                                <button class="btn btn-secondary" onclick="hideGPSHelp()" style="margin-top: 12px;">Back</button>
                                <button class="btn" onclick="retryGPS()" style="margin-top: 8px;">Try GPS Again</button>
                            </div>
                        </div>
                    `;
                    return;
                }
                
                // Non-location errors show standard error screen
                app.innerHTML = `
                    <div class="state-screen">
                        <div class="state-icon">⚠️</div>
                        <div class="state-title">Oops!</div>
                        <div class="state-desc">${state.error}</div>
                        <button class="btn" onclick="loadWeather()" style="margin-top: 16px;">Try Again</button>
                    </div>
                `;
                return;
            }
            
            if (!state.weather || !state.outfit) return;

            const w = state.weather;
            const unit = state.useCelsius ? '°C' : '°F';
            const windUnit = state.useCelsius ? 'km/h' : 'mph';
            const tempBracket = getTempBracket(w.feelsLike);
            const heroImageUrl = getHeroImageUrl();

            // SVG Icons
            const locationSvg = `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z"/></svg>`;
            const settingsSvg = `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M19.14,12.94c0.04-0.3,0.06-0.61,0.06-0.94c0-0.32-0.02-0.64-0.07-0.94l2.03-1.58c0.18-0.14,0.23-0.41,0.12-0.61 l-1.92-3.32c-0.12-0.22-0.37-0.29-0.59-0.22l-2.39,0.96c-0.5-0.38-1.03-0.7-1.62-0.94L14.4,2.81c-0.04-0.24-0.24-0.41-0.48-0.41 h-3.84c-0.24,0-0.43,0.17-0.47,0.41L9.25,5.35C8.66,5.59,8.12,5.92,7.63,6.29L5.24,5.33c-0.22-0.08-0.47,0-0.59,0.22L2.74,8.87 C2.62,9.08,2.66,9.34,2.86,9.48l2.03,1.58C4.84,11.36,4.8,11.69,4.8,12s0.02,0.64,0.07,0.94l-2.03,1.58 c-0.18,0.14-0.23,0.41-0.12,0.61l1.92,3.32c0.12,0.22,0.37,0.29,0.59,0.22l2.39-0.96c0.5,0.38,1.03,0.7,1.62,0.94l0.36,2.54 c0.05,0.24,0.24,0.41,0.48,0.41h3.84c0.24,0,0.44-0.17,0.47-0.41l0.36-2.54c0.59-0.24,1.13-0.56,1.62-0.94l2.39,0.96 c0.22,0.08,0.47,0,0.59-0.22l1.92-3.32c0.12-0.22,0.07-0.47-0.12-0.61L19.14,12.94z M12,15.6c-1.98,0-3.6-1.62-3.6-3.6 s1.62-3.6,3.6-3.6s3.6,1.62,3.6,3.6S13.98,15.6,12,15.6z"/></svg>`;
            const calendarSvg = `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M19 3h-1V1h-2v2H8V1H6v2H5c-1.11 0-1.99.9-1.99 2L3 19c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V8h14v11zM9 10H7v2h2v-2zm4 0h-2v2h2v-2zm4 0h-2v2h2v-2z"/></svg>`;
            const clockSvg = `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z"/></svg>`;
            const windSvg = `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M14.5 17c0 1.65-1.35 3-3 3s-3-1.35-3-3h2c0 .55.45 1 1 1s1-.45 1-1-.45-1-1-1H2v-2h9.5c1.65 0 3 1.35 3 3zM19 6.5C19 4.57 17.43 3 15.5 3S12 4.57 12 6.5h2c0-.83.67-1.5 1.5-1.5s1.5.67 1.5 1.5S16.33 8 15.5 8H2v2h13.5c1.93 0 3.5-1.57 3.5-3.5zm-.5 4.5H2v2h16.5c.83 0 1.5.67 1.5 1.5s-.67 1.5-1.5 1.5v2c1.93 0 3.5-1.57 3.5-3.5S20.43 11 18.5 11z"/></svg>`;
            const humiditySvg = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M4 14c0-4 3-7 5-9 2 2 5 5 5 9a5 5 0 01-10 0z"/><path d="M8 14h4"/><path d="M7 17h6"/></svg>`;
            const precipSvg = `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 2c-5.33 4.55-8 8.48-8 11.8 0 4.98 3.8 8.2 8 8.2s8-3.22 8-8.2c0-3.32-2.67-7.25-8-11.8zm0 18c-3.35 0-6-2.57-6-6.2 0-2.34 1.95-5.44 6-9.14 4.05 3.7 6 6.79 6 9.14 0 3.63-2.65 6.2-6 6.2z"/></svg>`;
            const uvSvg = `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 7c-2.76 0-5 2.24-5 5s2.24 5 5 5 5-2.24 5-5-2.24-5-5-5zM2 13h2c.55 0 1-.45 1-1s-.45-1-1-1H2c-.55 0-1 .45-1 1s.45 1 1 1zm18 0h2c.55 0 1-.45 1-1s-.45-1-1-1h-2c-.55 0-1 .45-1 1s.45 1 1 1zM11 2v2c0 .55.45 1 1 1s1-.45 1-1V2c0-.55-.45-1-1-1s-1 .45-1 1zm0 18v2c0 .55.45 1 1 1s1-.45 1-1v-2c0-.55-.45-1-1-1s-1 .45-1 1z"/></svg>`;
            const chevronSvg = `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z"/></svg>`;
            const bagSvg = `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M18 6h-2c0-2.21-1.79-4-4-4S8 3.79 8 6H6c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm-6-2c1.1 0 2 .9 2 2h-4c0-1.1.9-2 2-2zm6 16H6V8h2v2c0 .55.45 1 1 1s1-.45 1-1V8h4v2c0 .55.45 1 1 1s1-.45 1-1V8h2v12z"/></svg>`;
            const lightbulbSvg = `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M9 21c0 .55.45 1 1 1h4c.55 0 1-.45 1-1v-1H9v1zm3-19C8.14 2 5 5.14 5 9c0 2.38 1.19 4.47 3 5.74V17c0 .55.45 1 1 1h6c.55 0 1-.45 1-1v-2.26c1.81-1.27 3-3.36 3-5.74 0-3.86-3.14-7-7-7z"/></svg>`;
            const shareSvg = `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92s2.92-1.31 2.92-2.92-1.31-2.92-2.92-2.92z"/></svg>`;

            app.innerHTML = `
                <!-- HERO SECTION -->
                <div class="hero">
                    <div class="hero-image-container">
                        <img class="hero-image" src="${heroImageUrl}" alt="Runner in weather-appropriate outfit" onerror="this.style.display='none'">
                        <div class="hero-image-placeholder"></div>
                    </div>
                    <div class="hero-tint ${tempBracket}"></div>
                    <div class="hero-gradient"></div>

                    <!-- Top Controls -->
                    <div class="hero-header">
                        <button class="glass-btn" onclick="openLocationModal()">
                            ${locationSvg}
                            <span>${state.locationName}</span>
                        </button>
                        <div style="display: flex; gap: 8px;">
                            <button class="glass-btn glass-btn-icon" onclick="shareOutfit()" title="Share">
                                ${shareSvg}
                            </button>
                            <button class="glass-btn glass-btn-icon" onclick="openSettings()" title="Settings">
                                ${settingsSvg}
                            </button>
                        </div>
                    </div>

                    <!-- Bottom Weather Content -->
                    <div class="hero-content">
                        <!-- Date/Time Pill -->
                        <div class="datetime-pill">
                            <div class="datetime-pill-section" onclick="openDatePicker()">
                                ${calendarSvg}
                                <span>${formatDate(state.selectedDate)}</span>
                            </div>
                            <div class="datetime-pill-divider"></div>
                            <div class="datetime-pill-section" onclick="openTimePicker()">
                                ${clockSvg}
                                <span>${formatTime(state.selectedDate)}</span>
                            </div>
                        </div>

                        <!-- Temperature Display -->
                        <div class="temp-display">
                            <div class="temp-feels-label">Feels Like</div>
                            <div class="temp-main ${tempBracket}">${Math.round(w.feelsLike)}°</div>
                            <div class="temp-actual">Actual: <span>${Math.round(w.temp)}${unit}</span></div>
                        </div>

                        <!-- Weather Pills (tap to expand) -->
                        <div class="weather-pills">
                            <div class="weather-pill" onclick="showWeatherDetail('condition')">
                                ${getWeatherIcon(w.weatherCode)}
                            </div>
                            <div class="weather-pill" onclick="showWeatherDetail('wind')">
                                ${windSvg}
                                <span>${Math.round(w.windSpeed)} ${windUnit}</span>
                            </div>
                            <div class="weather-pill" onclick="showWeatherDetail('humidity')">
                                ${humiditySvg}
                                <span>${w.humidity}%</span>
                            </div>
                            ${w.precipitationProbability !== undefined && w.precipitationProbability > 0 ? `
                            <div class="weather-pill" onclick="showWeatherDetail('precipitation')">
                                ${precipSvg}
                                <span>${Math.round(w.precipitationProbability)}%</span>
                            </div>
                            ` : ''}
                            ${w.uvIndex > 0 ? `
                            <div class="weather-pill" onclick="showWeatherDetail('uv')">
                                ${uvSvg}
                                <span>UV ${Math.round(w.uvIndex)}</span>
                            </div>
                            ` : ''}
                        </div>
                    </div>
                </div>

                <!-- OUTFIT SECTION -->
                <div class="outfit-section">
                    <div class="outfit-section-header">
                        <div class="outfit-section-title">${state.outfit.items.length} Items for Your Run</div>
                        <div class="outfit-controls">
                            <button class="shop-btn" onclick="openShop()">
                                ${bagSvg}
                                <span>Shop</span>
                            </button>
                        </div>
                    </div>

                    <div class="outfit-grid">
                        ${state.outfit.items.map((item, index) => `
                            <button class="outfit-card" onclick="showOutfitDetail(${JSON.stringify(item).replace(/"/g, '&quot;')})" style="animation-delay: ${0.05 + index * 0.05}s">
                                <div class="outfit-icon cat-${item.category}">
                                    ${getCategoryIcon(item.category)}
                                </div>
                                <div class="outfit-info">
                                    <div class="outfit-name">${item.name}</div>
                                    <div class="outfit-category">${item.desc || item.category}</div>
                                </div>
                                <div class="outfit-action">
                                    ${chevronSvg}
                                </div>
                            </button>
                        `).join('')}
                    </div>

                    ${state.outfit.tips.length > 0 ? `
                        <div class="tip-card" onclick="toggleTipCard(this)">
                            <div class="tip-header">
                                <div class="tip-icon">${lightbulbSvg}</div>
                                <div class="tip-label">Pro Tip</div>
                            </div>
                            <div class="tip-text">${state.outfit.tips[0]}</div>
                            ${state.outfit.tips.length > 1 ? `
                                <div class="tip-more">
                                    ${state.outfit.tips.slice(1).map(tip => `<div class="tip-text" style="margin-top: 8px;">${tip}</div>`).join('')}
                                </div>
                            ` : ''}
                        </div>
                    ` : ''}

                    <div class="footer">v3.9</div>
                </div>

                <!-- Pull to Refresh Indicator -->
                <div class="pull-refresh-indicator">
                    <svg viewBox="0 0 24 24"><path d="M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"/></svg>
                </div>

                <!-- Sheet Overlay -->
                <div class="sheet-overlay" id="sheetOverlay" onclick="closeAllSheets()"></div>
                <div class="weather-detail-popup" id="weatherDetailPopup"></div>
                <div class="outfit-detail-sheet" id="outfitDetailSheet"></div>
            `;
        }

        // ============ ONBOARDING ============
        let onboardingState = {
            useCelsius: null,
            gender: 'all', // default unisex, nothing selected visually
            comfort: 0
        };

        function initOnboardingDefaults() {
            // Detect locale for temperature default
            const isUSLocale = navigator.language?.startsWith('en-US') ||
                               Intl.DateTimeFormat().resolvedOptions().locale?.includes('US');
            onboardingState.useCelsius = !isUSLocale;
            onboardingState.gender = 'all'; // default unisex, nothing selected visually
            onboardingState.comfort = 0;
        }

        function showOnboarding() {
            initOnboardingDefaults();
            updateOnboardingUI();
            document.getElementById('onboardingModal').classList.add('active');
        }

        function updateOnboardingUI() {
            // Update unit selector
            const unitSelector = document.getElementById('onboardingUnitSelector');
            if (unitSelector) {
                unitSelector.querySelectorAll('.unit-opt').forEach(opt => {
                    const isC = opt.dataset.unit === 'c';
                    opt.classList.toggle('active', isC === onboardingState.useCelsius);
                });
            }

            // Update gender selector
            const genderSelector = document.getElementById('onboardingGenderSelector');
            if (genderSelector) {
                genderSelector.querySelectorAll('.gender-opt').forEach(opt => {
                    opt.classList.toggle('active', opt.dataset.gender === onboardingState.gender);
                });
            }

            // Update comfort selector
            const comfortSelector = document.getElementById('onboardingComfortSelector');
            if (comfortSelector) {
                comfortSelector.querySelectorAll('.comfort-opt').forEach(opt => {
                    opt.classList.toggle('active', parseInt(opt.dataset.comfort) === onboardingState.comfort);
                });
            }
        }

        function setOnboardingUnit(useCelsius) {
            onboardingState.useCelsius = useCelsius;
            updateOnboardingUI();
        }

        function setOnboardingGender(gender) {
            // Toggle: if same gender clicked, deselect (back to 'all'/unisex)
            onboardingState.gender = (onboardingState.gender === gender) ? 'all' : gender;
            updateOnboardingUI();
        }

        function setOnboardingComfort(comfort) {
            onboardingState.comfort = comfort;
            updateOnboardingUI();
        }

        function completeOnboarding() {
            // Save preferences
            state.useCelsius = onboardingState.useCelsius;
            state.gender = onboardingState.gender;
            state.comfort = onboardingState.comfort;
            state.hasCompletedOnboarding = true;

            localStorage.setItem('useCelsius', state.useCelsius);
            localStorage.setItem('gender', state.gender);
            localStorage.setItem('comfort', state.comfort);
            localStorage.setItem('hasCompletedOnboarding', 'true');

            // Hide onboarding with fade
            const modal = document.getElementById('onboardingModal');
            modal.style.opacity = '0';
            modal.style.transition = 'opacity 0.3s ease';

            setTimeout(() => {
                modal.classList.remove('active');
                modal.style.opacity = '';
                modal.style.transition = '';

                // Now load weather (which will trigger location permission)
                loadWeather();
            }, 300);
        }

        // ============ INIT ============
        document.addEventListener('DOMContentLoaded', () => {
            // Initialize pull-to-refresh, parallax, and modal swipe
            initPullToRefresh();
            initHeroParallax();
            initModalSwipeToClose();

            if (!state.hasCompletedOnboarding) {
                showOnboarding();
            } else {
                loadWeather();
            }
        });

        // Service worker
        if ('serviceWorker' in navigator) {
            navigator.serviceWorker.register('/sw.js').catch(() => {});
        }
    </script>
</body>
</html>
