package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ChatDepModel
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.ResponseStyle
import com.example.data.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name and firebase database id resources are configured`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        val dbId = context.getString(R.string.firestore_database_id)
        assertEquals("ChatDep", appName)
        assertTrue(dbId.isNotBlank())
    }

    @Test
    fun `models resolve defaults and enum fallbacks safely`() {
        assertEquals(ChatDepModel.PRO_FLASH, ChatDepModel.fromId("unknown_model"))
        assertEquals(ChatDepModel.TURBO_LITE, ChatDepModel.fromId("gemini-3.5-flash-lite"))
        assertEquals(ResponseStyle.BALANCED, ResponseStyle.fromKey("invalid"))
        assertEquals(ResponseStyle.CODING, ResponseStyle.fromKey("coding"))

        val profile = UserProfile()
        val conv = Conversation()
        val msg = ChatMessage()
        assertFalse(conv.pinned)
        assertFalse(msg.hasAttachment)
        assertEquals(ChatDepModel.PRO_FLASH.modelId, profile.preferredModel)
    }
}
