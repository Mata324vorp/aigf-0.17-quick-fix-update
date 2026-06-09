package com.litewer.aigf.client.ai;

import com.litewer.aigf.entity.CompanionActionIntent;
import com.litewer.aigf.entity.CompanionEmotion;

public record CompanionAiResult(String spokenText, CompanionEmotion emotion, CompanionActionIntent actionIntent, String memoryFact, String careHint) {
}
