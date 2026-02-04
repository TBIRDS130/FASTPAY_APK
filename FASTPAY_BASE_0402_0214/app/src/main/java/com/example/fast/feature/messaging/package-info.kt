/**
 * Messaging feature package
 *
 * Contains subpackages:
 * - conversation/: Conversation list (MainActivity)
 *   - MainActivity: SMS conversation list
 *   - MainActivityViewModel: Conversation list state
 *   - adapter/: SmsConversationAdapter
 * - chat/: Individual chat view (ChatActivity)
 *   - ChatActivity: Individual conversation
 *   - ChatActivityViewModel: Chat state
 *   - adapter/: ChatMessageAdapter
 *
 * Flow:
 * 1. Display conversation list
 * 2. Tap conversation -> Open chat
 * 3. Send/receive messages
 * 4. Sync with Firebase
 */
package com.example.fast.feature.messaging
