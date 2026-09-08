package com.example.llama

import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AiReportClientTest {
    @Test fun acceptsOnlyAcknowledgementForThisReport() {
        AiReportClient.validateAcknowledgement("""{"ok":true,"report_id":"test-id"}""", "test-id")
        for (response in listOf("", "<html>Sign in</html>", "{}", """{"ok":false}""",
            """{"ok":true,"report_id":"other"}""", """{"ok":"true","report_id":"test-id"}""")) {
            try {
                AiReportClient.validateAcknowledgement(response, "test-id")
                fail("Unconfirmed delivery must not be reported as successful")
            } catch (_: IllegalArgumentException) { }
              catch (_: IllegalStateException) { }
        }
    }
}
