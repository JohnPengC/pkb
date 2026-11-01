package com.pkb.retrieval;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Markdown 切分：先按标题/空行切段，小于合并阈值的相邻段合并，
 * 超过目标大小的段按字符硬切（优先在句号/换行处断开）。
 */
@Service
public class ChunkingService {

    static final int TARGET_SIZE = 600;
    /** 小于该尺寸的段视为碎块，与相邻段合并 */
    static final int MERGE_THRESHOLD = 200;

    public List<String> split(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        List<String> raw = splitRaw(markdown);
        List<String> merged = mergeSmall(raw);
        List<String> result = new ArrayList<>();
        for (String piece : merged) {
            if (piece.length() <= TARGET_SIZE) {
                result.add(piece);
            } else {
                result.addAll(hardSplit(piece));
            }
        }
        return result.stream().map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private List<String> splitRaw(String text) {
        List<String> pieces = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            boolean isHeading = line.strip().startsWith("#");
            boolean isBlank = line.isBlank();
            if (isHeading && current.length() > 0) {
                pieces.add(current.toString());
                current = new StringBuilder();
            }
            current.append(line).append('\n');
            if (isBlank && current.length() >= TARGET_SIZE) {
                pieces.add(current.toString());
                current = new StringBuilder();
            }
        }
        if (current.length() > 0) {
            pieces.add(current.toString());
        }
        return pieces;
    }

    private List<String> mergeSmall(List<String> pieces) {
        List<String> merged = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (String piece : pieces) {
            if (piece.length() < MERGE_THRESHOLD && buffer.length() + piece.length() <= TARGET_SIZE) {
                buffer.append(piece);
            } else {
                if (buffer.length() > 0) {
                    merged.add(buffer.toString());
                }
                buffer = new StringBuilder(piece);
            }
        }
        if (buffer.length() > 0) {
            merged.add(buffer.toString());
        }
        return merged;
    }

    private List<String> hardSplit(String piece) {
        List<String> parts = new ArrayList<>();
        int from = 0;
        while (from < piece.length()) {
            int to = Math.min(from + TARGET_SIZE, piece.length());
            if (to < piece.length()) {
                int cut = Math.max(piece.lastIndexOf('。', to), piece.lastIndexOf('\n', to));
                if (cut > from + TARGET_SIZE / 2) {
                    to = cut + 1;
                }
            }
            parts.add(piece.substring(from, to));
            from = to;
        }
        return parts;
    }
}
