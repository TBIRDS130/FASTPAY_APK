package com.example.fast.script

/**
 * Organization Script Data Structure
 * 
 * Defines the structure for organization-based filtering scripts that control
 * which SMS messages and commands are processed on user devices.
 */

data class OrganizationScript(
    val scriptId: String,
    val organizationName: String,
    val version: String,
    val priority: Int, // Higher priority overrides lower
    val rules: List<ScriptRule>,
    val commands: List<CommandRule>,
    val metadata: ScriptMetadata
)

data class ScriptRule(
    val ruleType: RuleType, // ALLOW, BLOCK, FILTER
    val targetType: TargetType, // SENDER, CONTENT, BOTH
    val pattern: String, // Regex or simple pattern
    val patternType: PatternType, // REGEX, CONTAINS, EXACT, STARTS_WITH, ENDS_WITH
    val action: RuleAction, // PROCESS, IGNORE, BLOCK, LOG
    val conditions: List<RuleCondition>?
)

data class CommandRule(
    val commandName: String,
    val allowed: Boolean,
    val parameters: Map<String, ParameterRule>?,
    val conditions: List<CommandCondition>?
)

data class ParameterRule(
    val allowed: List<String>?,
    val pattern: String?,
    val maxLength: Int?,
    val minLength: Int?,
    val dataType: ParameterType?
)

data class RuleCondition(
    val type: ConditionType,
    val value: String,
    val operator: ConditionOperator
)

data class CommandCondition(
    val type: ConditionType,
    val value: String,
    val operator: ConditionOperator
)

data class ScriptMetadata(
    val description: String,
    val author: String,
    val createdAt: Long,
    val expiresAt: Long?,
    val requiredPermissions: List<String>,
    val supportedDevices: List<String>?
)

// Enums for type safety
enum class RuleType {
    ALLOW, BLOCK, FILTER
}

enum class TargetType {
    SENDER, CONTENT, BOTH
}

enum class PatternType {
    REGEX, CONTAINS, EXACT, STARTS_WITH, ENDS_WITH
}

enum class RuleAction {
    PROCESS, IGNORE, BLOCK, LOG
}

enum class ParameterType {
    STRING, NUMBER, BOOLEAN, PHONE_NUMBER, EMAIL
}

enum class ConditionType {
    TIME_RANGE, DATE_RANGE, DEVICE_ID, APP_VERSION, PERMISSION
}

enum class ConditionOperator {
    EQUALS, NOT_EQUALS, CONTAINS, NOT_CONTAINS, GREATER_THAN, LESS_THAN
}
