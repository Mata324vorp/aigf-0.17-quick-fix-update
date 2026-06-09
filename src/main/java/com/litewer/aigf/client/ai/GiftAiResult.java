package com.litewer.aigf.client.ai;

import com.litewer.aigf.entity.CompanionEmotion;

public record GiftAiResult(String spokenText, CompanionEmotion emotion, int moodDelta, int trustDelta, String memoryFact, String careHint) {
}
