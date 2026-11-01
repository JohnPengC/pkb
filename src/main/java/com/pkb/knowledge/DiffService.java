package com.pkb.knowledge;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class DiffService {

    public List<DiffLine> diff(String oldContent, String newContent) {
        List<String> oldLines = splitLines(oldContent);
        List<String> newLines = splitLines(newContent);
        Patch<String> patch = DiffUtils.diff(oldLines, newLines);

        boolean[] oldChanged = new boolean[oldLines.size()];
        boolean[] newChanged = new boolean[newLines.size()];
        List<DiffLine> result = new ArrayList<>();

        // 先输出所有行（KEEP），再按 delta 修正；简单起见按位置重放
        int oldPos = 0;
        int newPos = 0;
        List<AbstractDelta<String>> deltas = patch.getDeltas();
        for (AbstractDelta<String> delta : deltas) {
            int oldStart = delta.getSource().getPosition();
            int newStart = delta.getTarget().getPosition();
            while (oldPos < oldStart) {
                result.add(new DiffLine("KEEP", oldLines.get(oldPos++)));
                newPos++;
            }
            for (String line : delta.getSource().getLines()) {
                result.add(new DiffLine("DEL", line));
                oldPos++;
            }
            for (String line : delta.getTarget().getLines()) {
                result.add(new DiffLine("ADD", line));
                newPos++;
            }
        }
        while (oldPos < oldLines.size()) {
            result.add(new DiffLine("KEEP", oldLines.get(oldPos++)));
        }
        return result;
    }

    private List<String> splitLines(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(content.split("\n", -1));
    }
}
