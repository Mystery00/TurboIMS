package io.github.vvb2060.ims.privileged

import java.lang.reflect.InvocationTargetException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivilegedErrorTest {
    @Test
    fun unwrapsInvocationTargetException() {
        val cause = SecurityException("denied")
        val wrapped = InvocationTargetException(cause)

        assertEquals(cause, wrapped.privilegedRootCause())
    }

    @Test
    fun formatsRootCauseTypeAndMessage() {
        val wrapped = InvocationTargetException(SecurityException("denied"))

        assertEquals("SecurityException: denied", wrapped.toPrivilegedErrorMessage())
    }

    @Test
    fun recognizesCarrierConfigPermissionFailure() {
        assertTrue(
            isCarrierConfigPermissionError(
                "SecurityException: No permission to write to carrier config"
            )
        )
        assertTrue(
            isCarrierConfigPermissionError(
                "SecurityException: missing android.permission.MODIFY_PHONE_STATE"
            )
        )
    }

    @Test
    fun ignoresUnrelatedFailure() {
        assertFalse(
            isCarrierConfigPermissionError(
                "IllegalStateException: phone service unavailable"
            )
        )
    }
}
