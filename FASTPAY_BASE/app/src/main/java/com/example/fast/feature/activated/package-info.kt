/**
 * Activated (Dashboard) feature package
 *
 * Contains:
 * - ActivatedActivity: Post-activation main screen
 * - ActivatedViewModel: Dashboard state management
 * - manager/: Dashboard-specific managers
 *   - ActivatedButtonManager: Button interactions
 *   - ActivatedFirebaseManager: Firebase listeners
 *   - ActivatedServiceManager: Service lifecycle
 *   - ActivatedStatusManager: Status display
 *   - ActivatedUIManager: UI updates
 *   - LogoAnimationManager: Logo animations
 *
 * Flow:
 * 1. Display device status
 * 2. Show bank/status information
 * 3. Handle service start/stop
 * 4. Navigate to other features
 */
package com.example.fast.feature.activated
