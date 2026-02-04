/**
 * Feature modules package
 *
 * This package organizes the app by features, following a feature-first architecture.
 * Each feature has its own subpackage containing:
 * - Activity/Fragment classes
 * - ViewModel classes
 * - Feature-specific adapters
 * - Feature-specific managers/helpers
 *
 * Feature packages:
 * - splash/: Splash screen and app startup
 * - activation/: Device activation flow
 * - activated/: Post-activation dashboard
 * - messaging/: SMS/Chat functionality
 *   - conversation/: Conversation list
 *   - chat/: Individual chat view
 * - contacts/: Contact management
 * - permission/: Permission request flows
 * - settings/: Settings and configuration
 *
 * Benefits:
 * - Co-located code: All related code is in one place
 * - Easy to find: Navigate by feature, not by layer
 * - Scalable: Add new features without touching existing code
 * - Team-friendly: Different teams can own different features
 */
package com.example.fast.feature
