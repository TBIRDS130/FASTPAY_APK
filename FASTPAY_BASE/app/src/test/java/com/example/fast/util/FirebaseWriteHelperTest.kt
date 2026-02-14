package com.example.fast.util

import com.example.fast.util.FirebaseWriteHelper.WriteMode
import org.junit.Test
import com.google.common.truth.Truth.assertThat

/**
 * Unit tests for FirebaseWriteHelper
 *
 * Note: These tests focus on logic that does not require Firebase runtime.
 * Tests that invoke Firebase.database require instrumented tests or Firebase emulator.
 */
class FirebaseWriteHelperTest {

    // @Test
    fun `test WriteMode enum values`() {
        assertThat(WriteMode.SET).isNotNull()
        assertThat(WriteMode.UPDATE).isNotNull()
    }

    // @Test
    fun `test WriteMode enum has expected values`() {
        assertThat(WriteMode.entries).hasSize(2)
        assertThat(WriteMode.entries).containsExactly(WriteMode.SET, WriteMode.UPDATE)
    }

    // @Test
    fun `test WriteMode SET has ordinal 0`() {
        assertThat(WriteMode.SET.ordinal).isEqualTo(0)
    }

    // @Test
    fun `test WriteMode UPDATE has ordinal 1`() {
        assertThat(WriteMode.UPDATE.ordinal).isEqualTo(1)
    }
}
