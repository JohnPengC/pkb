package com.pkb.distillation;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DraftParser {

    private static final Pattern PATTERN = Pattern.compile(
            "<<<TITLE>>>\\s*(.*?)\\s*<<<SUMMARY>>>\\s*(.*?)\\s*<<<CONTENT>>>\\s*(.*)",
            Pattern.DOTALL);

    public DistilledDraft parse(String llmOutput, String fallbackTitle) {
        if (llmOutput == null) {
            return new DistilledDraft(fallbackTitle, "", "");
        }
        Matcher m = PATTERN.matcher(llmOutput);
        if (!m.find()) {
            return new DistilledDraft(fallbackTitle, "", llmOutput.strip());
        }
        String title = m.group(1).strip();
        if (title.isEmpty()) {
            title = fallbackTitle;
        }
        return new DistilledDraft(title, m.group(2).strip(), m.group(3).strip());
    }
}
