<!DOCTYPE html>
<!-- RunWear PWA v2.6 - Fixed FTP path to correct public_html -->
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
    <link rel="apple-touch-icon" sizes="120x120" href="icon-120.png">
    <link rel="apple-touch-icon" sizes="76x76" href="icon-76.png">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet">
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        
        :root {
            /* Primary: Deep Teal (WCAG AA compliant) */
            --primary: #00796B;
            --primary-dark: #004D40;
            --primary-light: #48A999;
            
            /* Category Colors */
            --cat-top: #00796B;
            --cat-bottom: #1565C0;
            --cat-head: #2E7D32;
            --cat-hands: #5E35B1;
            --cat-accessories: #E65100;
            
            /* Neutrals */
            --bg: #FAFAFA;
            --surface: #FFFFFF;
            --surface-dim: #F1F5F9;
            --text: #1E293B;
            --text-secondary: #475569;
            --text-muted: #64748B;
            --border: #E2E8F0;
            
            /* Temperature Colors */
            --hot: #FF5722;
            --warm: #FF9800;
            --mild: #4CAF50;
            --cool: #2196F3;
            --cold: #3F51B5;
            --freezing: #9C27B0;
        }
        
        body {
            font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: var(--bg);
            color: var(--text);
            min-height: 100vh;
            min-height: 100dvh;
            overflow-x: hidden;
            -webkit-font-smoothing: antialiased;
        }
        
        .container {
            max-width: 480px;
            margin: 0 auto;
            padding: 16px;
            padding-bottom: 100px;
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
        
        /* Gender Toggle */
        .gender-toggle {
            display: flex;
            align-items: center;
            background: var(--surface-dim);
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
            font-size: 16px;
            cursor: pointer;
            border-radius: 16px;
            transition: all 0.2s;
            opacity: 0.5;
        }

        .gender-opt.active {
            background: var(--primary);
            opacity: 1;
            transform: scale(1.05);
        }

        .gender-opt.center {
            font-size: 12px;
            color: var(--text-secondary);
        }

        .gender-opt.center.active {
            color: white;
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
            min-height: 70vh;
            text-align: center;
            padding: 24px;
        }
        
        .state-icon { font-size: 64px; margin-bottom: 20px; }
        
        .state-title {
            font-size: 22px;
            font-weight: 700;
            margin-bottom: 8px;
            color: var(--text);
        }
        
        .state-desc {
            color: var(--text-secondary);
            font-size: 14px;
            margin-bottom: 28px;
            max-width: 280px;
            line-height: 1.5;
        }
        
        .btn {
            background: var(--primary);
            color: white;
            border: none;
            padding: 14px 32px;
            border-radius: 12px;
            font-size: 15px;
            font-weight: 600;
            cursor: pointer;
            transition: background 0.2s;
        }
        
        .btn:hover { background: var(--primary-dark); }
        
        .btn-secondary {
            background: var(--surface-dim);
            color: var(--text);
            margin-top: 12px;
        }
        
        .btn-secondary:hover { background: var(--border); }
        
        /* Spinner */
        .spinner {
            width: 48px;
            height: 48px;
            border: 4px solid var(--surface-dim);
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
            background: rgba(0,0,0,0.5);
            display: none;
            align-items: center;
            justify-content: center;
            z-index: 50;
            padding: 20px;
            backdrop-filter: blur(4px);
        }
        
        .picker-overlay.active { display: flex; }
        
        .picker-container {
            background: var(--surface);
            border-radius: 24px;
            padding: 24px;
            width: 100%;
            max-width: 340px;
            max-height: 80vh;
            overflow-y: auto;
            box-shadow: 0 20px 60px rgba(0,0,0,0.3);
        }
        
        .picker-title {
            font-size: 18px;
            font-weight: 700;
            text-align: center;
            margin-bottom: 20px;
            color: var(--text);
        }
        
        .picker-grid {
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: 10px;
        }
        
        .picker-grid.dates { grid-template-columns: repeat(2, 1fr); }
        
        .picker-item {
            padding: 14px 10px;
            background: var(--surface-dim);
            border-radius: 12px;
            text-align: center;
            cursor: pointer;
            transition: all 0.2s;
            font-size: 14px;
            font-weight: 500;
        }
        
        .picker-item:hover { background: var(--primary-light); color: white; }
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
            background: var(--surface-dim);
            border: none;
            border-radius: 12px;
            color: var(--text);
            font-size: 14px;
            font-weight: 500;
            cursor: pointer;
        }
        
        /* ========== SETTINGS MODAL ========== */
        .modal-overlay {
            position: fixed;
            inset: 0;
            background: rgba(0,0,0,0.5);
            display: none;
            align-items: flex-end;
            justify-content: center;
            z-index: 100;
            backdrop-filter: blur(4px);
        }
        
        .modal-overlay.active { display: flex; }
        
        .modal {
            background: var(--surface);
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
            background: var(--border);
            border-radius: 2px;
            margin: 0 auto 20px;
        }
        
        .modal-title {
            font-size: 20px;
            font-weight: 700;
            margin-bottom: 24px;
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
        }
        
        .setting-sublabel {
            font-size: 12px;
            color: var(--text-muted);
            margin-top: 2px;
        }
        
        .toggle {
            width: 52px;
            height: 28px;
            background: var(--surface-dim);
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
            box-shadow: 0 1px 3px rgba(0,0,0,0.2);
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
            background: var(--surface-dim);
            border-radius: 10px;
            text-align: center;
            cursor: pointer;
            font-size: 11px;
            font-weight: 500;
            transition: all 0.2s;
        }
        
        .comfort-opt.active {
            background: var(--primary);
            color: white;
        }
        
        /* ========== SHOP MODAL ========== */
        .shop-modal {
            background: var(--surface);
            border-radius: 24px 24px 0 0;
            padding: 24px;
            width: 100%;
            max-width: 500px;
            max-height: 85vh;
            overflow-y: auto;
        }
        
        .shop-item {
            display: flex;
            align-items: center;
            gap: 12px;
            padding: 12px;
            background: var(--surface-dim);
            border-radius: 12px;
            margin-bottom: 10px;
            cursor: pointer;
            transition: all 0.2s;
        }
        
        .shop-item:hover { 
            background: var(--primary-light); 
            color: white;
        }
        
        .shop-item:hover .shop-item-desc { color: rgba(255,255,255,0.8); }
        
        .shop-item-icon {
            font-size: 24px;
            width: 40px;
            text-align: center;
        }
        
        .shop-item-info { flex: 1; }
        
        .shop-item-name {
            font-weight: 600;
            font-size: 14px;
        }
        
        .shop-item-desc {
            font-size: 12px;
            color: var(--text-secondary);
        }
        
        .shop-item-arrow {
            font-size: 16px;
            opacity: 0.5;
        }
        
        .ftc-disclosure {
            background: var(--surface-dim);
            border-radius: 10px;
            padding: 12px;
            margin-top: 16px;
            font-size: 11px;
            color: var(--text-muted);
            line-height: 1.5;
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
            color: var(--text);
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
            border: 2px solid var(--border);
            border-radius: 12px;
            font-size: 16px;
            font-family: inherit;
            outline: none;
            transition: border-color 0.2s;
        }
        
        .location-input:focus {
            border-color: var(--primary);
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
            transition: background 0.2s;
            white-space: nowrap;
        }
        
        .location-submit:hover {
            background: var(--primary-dark);
        }
        
        .location-submit:disabled {
            background: var(--text-muted);
            cursor: not-allowed;
        }
        
        /* ========== LOCATION SETUP SCREEN ========== */
        .location-setup-screen {
            min-height: 100vh;
            display: flex;
            flex-direction: column;
            padding: 40px 20px;
            max-width: 400px;
            margin: 0 auto;
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
            font-size: 24px;
            font-weight: 700;
            color: var(--text);
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
            background: var(--surface);
            border: 2px solid var(--border);
            border-radius: 16px;
            cursor: pointer;
            transition: all 0.2s;
            text-align: left;
        }
        
        .location-gps-button:hover {
            border-color: var(--primary);
            background: rgba(0, 121, 107, 0.05);
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
            color: var(--text);
        }
        
        .location-gps-subtitle {
            font-size: 13px;
            color: var(--text-secondary);
        }
        
        .location-gps-help {
            margin-top: 24px;
            padding: 20px;
            background: var(--surface);
            border-radius: 16px;
            border: 2px solid var(--border);
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
            background: var(--surface-dim);
            border-radius: 10px;
            margin-bottom: 8px;
            cursor: pointer;
            transition: background 0.2s;
            font-size: 14px;
            color: var(--text);
        }
        
        .location-result:hover {
            background: var(--border);
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
            background: var(--surface);
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
            background: var(--surface-dim);
            border-radius: 14px;
            margin-bottom: 12px;
            cursor: pointer;
            transition: all 0.2s;
        }
        
        .location-option:hover {
            background: var(--border);
        }
        
        .location-option.active {
            background: rgba(0, 121, 107, 0.1);
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
            color: var(--text);
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
            border: 2px solid var(--border);
            border-radius: 14px;
            font-size: 16px;
            font-family: inherit;
            outline: none;
            transition: border-color 0.2s;
        }
        
        .search-input:focus {
            border-color: var(--primary);
        }
        
        .search-icon {
            position: absolute;
            left: 14px;
            top: 50%;
            transform: translateY(-50%);
            font-size: 18px;
        }
        
        .search-spinner {
            position: absolute;
            right: 14px;
            top: 50%;
            transform: translateY(-50%);
            width: 20px;
            height: 20px;
            border: 2px solid var(--border);
            border-top-color: var(--primary);
            border-radius: 50%;
            animation: spin 0.8s linear infinite;
        }
        
        .current-location-badge {
            display: inline-flex;
            align-items: center;
            gap: 4px;
            padding: 4px 10px;
            background: rgba(0, 121, 107, 0.1);
            color: var(--primary);
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
            <div class="modal-title">⚙️ Settings</div>
            
            <div class="setting-row">
                <div>
                    <div class="setting-label">Temperature Unit</div>
                    <div class="setting-sublabel">Tap temperature to toggle</div>
                </div>
                <div class="toggle" id="unitToggle" onclick="toggleUnit()"></div>
            </div>
            
            <div class="setting-row" style="flex-direction:column;align-items:flex-start">
                <div class="setting-label">Comfort Preference</div>
                <div class="setting-sublabel">Do you tend to run hot or cold?</div>
                <div class="comfort-selector" id="comfortSelector"></div>
            </div>
            
            <button class="btn" style="width:100%;margin-top:24px" onclick="closeSettings()">Done</button>
        </div>
    </div>
    
    <!-- Shop Modal -->
    <div class="modal-overlay" id="shopModal" onclick="if(event.target===this)closeShop()">
        <div class="shop-modal">
            <div class="modal-handle"></div>
            <div class="modal-title">🛒 Shop Your Outfit</div>
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
            selectedDate: new Date(),
            useCelsius: localStorage.getItem('useCelsius') === 'true',
            gender: localStorage.getItem('gender') || 'all', // 'male', 'female', 'all'
            comfort: parseInt(localStorage.getItem('comfort')) || 0, // -10, -5, 0, 5, 10
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
            // Clear error state and try GPS again
            state.error = null;
            state.errorCode = null;
            state.locationSource = 'auto';
            state.location = null;
            localStorage.setItem('locationSource', 'auto');
            localStorage.removeItem('manualLat');
            localStorage.removeItem('manualLon');
            localStorage.removeItem('manualLocationName');
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

        function getTempClass(temp) {
            const f = state.useCelsius ? temp * 9/5 + 32 : temp;
            if (f >= 80) return 'temp-hot';
            if (f >= 65) return 'temp-warm';
            if (f >= 50) return 'temp-mild';
            if (f >= 35) return 'temp-cool';
            if (f >= 20) return 'temp-cold';
            return 'temp-freezing';
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
                    } else {
                        // Try GPS location
                        state.location = await getCurrentLocation();
                        state.hasPermission = true;
                        state.locationSource = 'auto';
                        state.locationName = await getLocationName(state.location.lat, state.location.lon);
                    }
                }
                
                state.weather = await fetchWeather(state.location.lat, state.location.lon, state.selectedDate);
                state.outfit = getOutfitRecommendation(state.weather);
                state.loading = false;
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
            state.useCelsius = !state.useCelsius;
            localStorage.setItem('useCelsius', state.useCelsius);
            document.getElementById('unitToggle')?.classList.toggle('active', state.useCelsius);
            loadWeather();
        }

        function setGender(g) {
            state.gender = g;
            localStorage.setItem('gender', g);
            render();
        }

        function setComfort(val) {
            state.comfort = val;
            localStorage.setItem('comfort', val);
            if (state.weather) {
                state.outfit = getOutfitRecommendation(state.weather);
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
            loadWeather();
            closeTimePicker();
        }
        
        function setDate(daysFromNow) {
            const newDate = new Date();
            newDate.setDate(newDate.getDate() + daysFromNow);
            newDate.setHours(state.selectedDate.getHours(), 0, 0, 0);
            state.selectedDate = newDate;
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
            document.getElementById('unitToggle').classList.toggle('active', state.useCelsius);
            renderComfortSelector();
        }

        function closeSettings() {
            document.getElementById('settingsModal').classList.remove('active');
        }

        function renderComfortSelector() {
            const container = document.getElementById('comfortSelector');
            const options = [
                { val: -10, label: '🥶' },
                { val: -5, label: 'Cold' },
                { val: 0, label: 'Normal' },
                { val: 5, label: 'Warm' },
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
            
            // Save to localStorage
            localStorage.setItem('locationSource', 'manual');
            localStorage.setItem('manualLat', loc.lat.toString());
            localStorage.setItem('manualLon', loc.lon.toString());
            localStorage.setItem('manualLocationName', loc.displayName);
            
            closeLocationModal();
            loadWeather();
        }
        
        async function requestGPSLocation() {
            // Clear manual location
            state.locationSource = 'auto';
            state.location = null;
            localStorage.setItem('locationSource', 'auto');
            localStorage.removeItem('manualLat');
            localStorage.removeItem('manualLon');
            localStorage.removeItem('manualLocationName');
            
            closeLocationModal();
            
            // Reset and reload
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

        // ============ RENDER ============
        function render() {
            const app = document.getElementById('app');
            
            if (state.loading) {
                app.innerHTML = `
                    <div class="state-screen">
                        <div class="spinner"></div>
                        <div class="state-title">Getting your outfit...</div>
                        <div class="state-desc">Fetching weather data for your location</div>
                    </div>
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
            const locationIcon = state.locationSource === 'auto' 
                ? `<svg viewBox="0 0 24 24"><path d="M12 8c-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4-1.79-4-4-4zm8.94 3c-.46-4.17-3.77-7.48-7.94-7.94V1h-2v2.06C6.83 3.52 3.52 6.83 3.06 11H1v2h2.06c.46 4.17 3.77 7.48 7.94 7.94V23h2v-2.06c4.17-.46 7.48-3.77 7.94-7.94H23v-2h-2.06zM12 19c-3.87 0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z"/></svg>`
                : `<svg viewBox="0 0 24 24"><path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z"/></svg>`;
            
            app.innerHTML = `
                <div class="header">
                    <div class="logo">Run<span>Wear</span></div>
                    <div style="display: flex; align-items: center; gap: 4px;">
                        <div class="header-location" onclick="openLocationModal()">
                            ${locationIcon}
                            <span>${state.locationName}</span>
                        </div>
                        <button class="header-location-icon" onclick="openLocationModal()" title="Change location">
                            <svg viewBox="0 0 24 24"><path d="M12 8c-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4-1.79-4-4-4zm8.94 3c-.46-4.17-3.77-7.48-7.94-7.94V1h-2v2.06C6.83 3.52 3.52 6.83 3.06 11H1v2h2.06c.46 4.17 3.77 7.48 7.94 7.94V23h2v-2.06c4.17-.46 7.48-3.77 7.94-7.94H23v-2h-2.06zM12 19c-3.87 0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z"/></svg>
                        </button>
                    </div>
                </div>
                
                <div class="weather-card">
                    <div class="weather-icon">${getWeatherIcon(w.weatherCode)}</div>
                    <div class="weather-temp-label">Feels Like</div>
                    <div class="weather-temp ${getTempClass(w.feelsLike)}" onclick="toggleUnit()">${Math.round(w.feelsLike)}${unit}</div>
                    <div class="weather-actual">Actual: <span>${Math.round(w.temp)}${unit}</span></div>
                    <div class="weather-details">
                        <div class="weather-detail">
                            <span class="weather-detail-icon">💨</span>
                            <span>${Math.round(w.windSpeed)} ${windUnit}</span>
                        </div>
                        <div class="weather-detail">
                            <span class="weather-detail-icon">💧</span>
                            <span>${w.humidity}%</span>
                        </div>
                        ${w.uvIndex > 0 ? `
                        <div class="weather-detail">
                            <span class="weather-detail-icon">☀️</span>
                            <span>UV ${Math.round(w.uvIndex)}</span>
                        </div>
                        ` : ''}
                    </div>
                    <div class="unit-hint">Tap temperature to switch ${state.useCelsius ? '°F' : '°C'}</div>
                </div>
                
                <div class="datetime-card">
                    <button class="datetime-btn" onclick="prevDay()">◀</button>
                    <div class="datetime-info">
                        <div class="datetime-selectors">
                            <span class="datetime-date" onclick="openDatePicker()">${formatDate(state.selectedDate)}</span>
                            <span class="datetime-separator">@</span>
                            <span class="datetime-time" onclick="openTimePicker()">${formatTime(state.selectedDate)}</span>
                        </div>
                        ${!isSameDay(state.selectedDate, new Date()) ? '<div class="datetime-reset" onclick="resetToNow()">Reset to now</div>' : ''}
                    </div>
                    <button class="datetime-btn" onclick="nextDay()">▶</button>
                </div>
                
                <div class="outfit-header">
                    <div class="section-title">Your Outfit</div>
                    <div class="outfit-controls">
                        <div class="gender-toggle">
                            <span class="gender-opt ${state.gender === 'male' ? 'active' : ''}" onclick="setGender('male')">🚹</span>
                            <span class="gender-opt center ${state.gender === 'all' ? 'active' : ''}" onclick="setGender('all')">○</span>
                            <span class="gender-opt ${state.gender === 'female' ? 'active' : ''}" onclick="setGender('female')">🚺</span>
                        </div>
                        <button class="shop-all-btn" onclick="openShop()">🛒 Shop</button>
                    </div>
                </div>
                
                ${state.outfit.items.map(item => `
                    <div class="clothing-item" onclick="shopItem(${JSON.stringify(item).replace(/"/g, '&quot;')})">
                        <div class="clothing-icon ${item.category}">${item.icon}</div>
                        <div class="clothing-info">
                            <div class="clothing-name">${item.name}</div>
                            <div class="clothing-desc">${item.desc}</div>
                        </div>
                        <span class="clothing-chevron">›</span>
                    </div>
                `).join('')}
                
                ${state.outfit.tips.length > 0 ? `
                    <div class="tips-section">
                        <div class="section-title" style="margin-bottom:12px">💡 Tips</div>
                        ${state.outfit.tips.map(tip => `<div class="tip">${tip}</div>`).join('')}
                    </div>
                ` : ''}
                
                <div class="footer">
                    <div style="margin-bottom:8px">
                        <button class="btn btn-secondary" onclick="loadWeather()">↻ Refresh</button>
                    </div>
                    Weather data from <a href="https://open-meteo.com/" target="_blank">Open-Meteo</a>
                </div>
            `;
        }

        // ============ INIT ============
        document.addEventListener('DOMContentLoaded', () => {
            loadWeather();
        });

        // Service worker
        if ('serviceWorker' in navigator) {
            navigator.serviceWorker.register('/sw.js').catch(() => {});
        }
    </script>
</body>
</html>
