/**
 * Foreground service package
 *
 * Contains:
 * - PersistentForegroundService: Main foreground service
 * - handler/: Service command handlers (to be extracted)
 *   - FirebaseCommandHandler: Firebase command processing
 *   - SmsFilterHandler: SMS filtering/blocking
 *   - HeartbeatHandler: Device heartbeat
 *   - WorkflowHandler: Workflow execution
 *   - MessageForwardHandler: Message forwarding
 * - manager/: Service state managers
 *   - ServiceStateManager: Service lifecycle management
 *
 * Architecture:
 * The PersistentForegroundService delegates to specialized handlers
 * for each concern, keeping the main service class focused on
 * lifecycle management.
 */
package com.example.fast.service.foreground
