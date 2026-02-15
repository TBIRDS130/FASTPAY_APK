package com.example.fast.script

import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Test Suite for all script-related functionality
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    ScriptInitializerTest::class,
    ScriptTestCommandsTest::class,
    com.example.fast.service.ScriptCommandsIntegrationTest::class
)
class ScriptTestSuite {
    // Test suite class - no implementation needed
}
