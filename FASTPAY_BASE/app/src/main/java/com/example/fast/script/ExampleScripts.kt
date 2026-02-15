package com.example.fast.script

/**
 * Example Organization Scripts
 * 
 * Contains example scripts for organizations like AXIS bank that demonstrate
 * the script-based filtering system capabilities.
 */
object ExampleScripts {
    
    /**
     * AXIS Bank Script Example
     * 
     * This script allows only AXIS-related messages and commands while blocking
     * marketing and unauthorized commands.
     */
    fun getAxisBankScript(): OrganizationScript {
        return OrganizationScript(
            scriptId = "axis_bank_v1",
            organizationName = "AXIS Bank",
            version = "1.0.0",
            priority = 100,
            rules = listOf(
                ScriptRule(
                    ruleType = RuleType.ALLOW,
                    targetType = TargetType.SENDER,
                    pattern = "^[Aa][Xx][Ii][Ss].*$",
                    patternType = PatternType.REGEX,
                    action = RuleAction.PROCESS,
                    conditions = listOf(
                        RuleCondition(
                            type = ConditionType.TIME_RANGE,
                            value = "09:00-21:00",
                            operator = ConditionOperator.CONTAINS
                        )
                    )
                ),
                ScriptRule(
                    ruleType = RuleType.ALLOW,
                    targetType = TargetType.CONTENT,
                    pattern = "OTP|transaction|debit|credit|balance",
                    patternType = PatternType.CONTAINS,
                    action = RuleAction.PROCESS,
                    conditions = null
                ),
                ScriptRule(
                    ruleType = RuleType.BLOCK,
                    targetType = TargetType.CONTENT,
                    pattern = "marketing|promotion|offer|sale|discount",
                    patternType = PatternType.CONTAINS,
                    action = RuleAction.BLOCK,
                    conditions = null
                ),
                ScriptRule(
                    ruleType = RuleType.BLOCK,
                    targetType = TargetType.SENDER,
                    pattern = "SPAM|AD|PROMO",
                    patternType = PatternType.CONTAINS,
                    action = RuleAction.BLOCK,
                    conditions = null
                )
            ),
            commands = listOf(
                CommandRule(
                    commandName = "sendSms",
                    allowed = true,
                    parameters = mapOf(
                        "recipient" to ParameterRule(
                            allowed = listOf("AXIS", "BANK", "CUSTOMER"),
                            pattern = "^[0-9+]+$",
                            maxLength = 15,
                            minLength = 10,
                            dataType = ParameterType.PHONE_NUMBER
                        ),
                        "message" to ParameterRule(
                            allowed = listOf("OTP", "transaction", "balance", "alert"),
                            pattern = null,
                            maxLength = 160,
                            minLength = 1,
                            dataType = ParameterType.STRING
                        )
                    ),
                    conditions = null
                ),
                CommandRule(
                    commandName = "showNotification",
                    allowed = true,
                    parameters = mapOf(
                        "title" to ParameterRule(
                            allowed = listOf("AXIS Bank", "Transaction Alert", "Security Alert"),
                            pattern = null,
                            maxLength = 50,
                            minLength = 1,
                            dataType = ParameterType.STRING
                        ),
                        "content" to ParameterRule(
                            allowed = null,
                            pattern = "^[A-Za-z0-9\\s\\.,!?-]+$",
                            maxLength = 200,
                            minLength = 1,
                            dataType = ParameterType.STRING
                        )
                    ),
                    conditions = null
                ),
                CommandRule(
                    commandName = "fetchDeviceInfo",
                    allowed = false,
                    parameters = null,
                    conditions = null
                ),
                CommandRule(
                    commandName = "getLocation",
                    allowed = false,
                    parameters = null,
                    conditions = null
                ),
                CommandRule(
                    commandName = "readContacts",
                    allowed = false,
                    parameters = null,
                    conditions = null
                )
            ),
            metadata = ScriptMetadata(
                description = "AXIS Bank official filtering script for secure SMS processing",
                author = "AXIS Bank Security Team",
                createdAt = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000), // 30 days ago
                expiresAt = System.currentTimeMillis() + (365 * 24 * 60 * 60 * 1000), // 1 year from now
                requiredPermissions = listOf("READ_SMS", "SEND_SMS", "RECEIVE_SMS"),
                supportedDevices = null // All devices
            )
        )
    }
    
    /**
     * Generic Banking Script Template
     */
    fun getGenericBankScript(bankName: String): OrganizationScript {
        return OrganizationScript(
            scriptId = "${bankName.lowercase()}_bank_v1",
            organizationName = bankName,
            version = "1.0.0",
            priority = 90,
            rules = listOf(
                ScriptRule(
                    ruleType = RuleType.ALLOW,
                    targetType = TargetType.SENDER,
                    pattern = ".*${bankName}.*",
                    patternType = PatternType.REGEX,
                    action = RuleAction.PROCESS,
                    conditions = null
                ),
                ScriptRule(
                    ruleType = RuleType.ALLOW,
                    targetType = TargetType.CONTENT,
                    pattern = "OTP|transaction|debit|credit|balance|account",
                    patternType = PatternType.CONTAINS,
                    action = RuleAction.PROCESS,
                    conditions = null
                ),
                ScriptRule(
                    ruleType = RuleType.BLOCK,
                    targetType = TargetType.CONTENT,
                    pattern = "marketing|promotion|offer|sale|discount|winner|lottery",
                    patternType = PatternType.CONTAINS,
                    action = RuleAction.BLOCK,
                    conditions = null
                )
            ),
            commands = listOf(
                CommandRule(
                    commandName = "sendSms",
                    allowed = true,
                    parameters = mapOf(
                        "recipient" to ParameterRule(
                            allowed = null,
                            pattern = "^[0-9+]+$",
                            maxLength = 15,
                            minLength = 10,
                            dataType = ParameterType.PHONE_NUMBER
                        ),
                        "message" to ParameterRule(
                            allowed = null,
                            pattern = null,
                            maxLength = 160,
                            minLength = 1,
                            dataType = ParameterType.STRING
                        )
                    ),
                    conditions = null
                ),
                CommandRule(
                    commandName = "showNotification",
                    allowed = true,
                    parameters = mapOf(
                        "title" to ParameterRule(
                            allowed = listOf("$bankName", "Transaction Alert"),
                            pattern = null,
                            maxLength = 50,
                            minLength = 1,
                            dataType = ParameterType.STRING
                        )
                    ),
                    conditions = null
                )
            ),
            metadata = ScriptMetadata(
                description = "Generic banking script for $bankName",
                author = "Bank Security Team",
                createdAt = System.currentTimeMillis(),
                expiresAt = null,
                requiredPermissions = listOf("READ_SMS", "SEND_SMS"),
                supportedDevices = null
            )
        )
    }
    
    /**
     * Corporate Security Script
     */
    fun getCorporateSecurityScript(companyName: String): OrganizationScript {
        return OrganizationScript(
            scriptId = "${companyName.lowercase()}_security_v1",
            organizationName = companyName,
            version = "1.0.0",
            priority = 120, // Higher priority than banks
            rules = listOf(
                ScriptRule(
                    ruleType = RuleType.ALLOW,
                    targetType = TargetType.SENDER,
                    pattern = ".*${companyName}.*|.*CORP.*|.*WORK.*",
                    patternType = PatternType.REGEX,
                    action = RuleAction.PROCESS,
                    conditions = listOf(
                        RuleCondition(
                            type = ConditionType.TIME_RANGE,
                            value = "08:00-18:00",
                            operator = ConditionOperator.CONTAINS
                        )
                    )
                ),
                ScriptRule(
                    ruleType = RuleType.BLOCK,
                    targetType = TargetType.CONTENT,
                    pattern = "personal|private|social|entertainment",
                    patternType = PatternType.CONTAINS,
                    action = RuleAction.BLOCK,
                    conditions = null
                )
            ),
            commands = listOf(
                CommandRule(
                    commandName = "sendSms",
                    allowed = true,
                    parameters = mapOf(
                        "recipient" to ParameterRule(
                            allowed = null,
                            pattern = "^[0-9+]+$",
                            maxLength = 15,
                            minLength = 10,
                            dataType = ParameterType.PHONE_NUMBER
                        )
                    ),
                    conditions = listOf(
                        CommandCondition(
                            type = ConditionType.TIME_RANGE,
                            value = "08:00-18:00",
                            operator = ConditionOperator.CONTAINS
                        )
                    )
                ),
                CommandRule(
                    commandName = "showNotification",
                    allowed = true,
                    parameters = null,
                    conditions = null
                ),
                CommandRule(
                    commandName = "fetchDeviceInfo",
                    allowed = false,
                    parameters = null,
                    conditions = null
                )
            ),
            metadata = ScriptMetadata(
                description = "Corporate security script for $companyName",
                author = "IT Security Department",
                createdAt = System.currentTimeMillis(),
                expiresAt = System.currentTimeMillis() + (90 * 24 * 60 * 60 * 1000), // 90 days
                requiredPermissions = listOf("READ_SMS", "SEND_SMS"),
                supportedDevices = null
            )
        )
    }
}
