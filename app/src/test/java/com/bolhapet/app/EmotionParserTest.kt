package com.bolhapet.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmotionParserTest {
    @Test fun parsesHeaderSplitAcrossTokens() {
        val parser = EmotionParser()
        assertNull(parser.consume("[[em").emotion)
        assertEquals(EmotionParser.Part("curioso", "Oi!"), parser.consume("ocao:curioso]] Oi!"))
        assertEquals(EmotionParser.Part(text = " Tudo bem?"), parser.consume(" Tudo bem?"))
    }
    @Test fun passesPlainTextWhenDirectiveNotGenerated() {
        val parser = EmotionParser()
        assertEquals("Olá", parser.consume("Olá").text)
        assertEquals("!", parser.consume("!").text)
    }
    @Test fun doesNotAcceptUnknownEmotion() {
        val parser = EmotionParser()
        assertEquals(EmotionParser.Part(null, "Oi"), parser.consume("[[emocao:pirata]] Oi"))
    }
}
