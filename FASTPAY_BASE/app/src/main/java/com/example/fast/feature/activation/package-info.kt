/**
 * Activation feature package
 *
 * Contains:
 * - ActivationActivity: Device activation with code entry
 * - ActivationViewModel: Activation business logic (to be added)
 * - manager/: Activation-specific managers
 *   - ActivationCodeManager: Code input handling
 *   - ActivationUIManager: UI state management
 *   - ActivationAnimationManager: Animation coordination
 * - component/: Activation-specific components
 *   - KeypadView: Custom keypad component
 *
 * Flow:
 * 1. Display activation code input
 * 2. Validate code with Firebase
 * 3. Initialize device in Firebase
 * 4. Navigate to ActivatedActivity on success
 */
package com.example.fast.feature.activation
