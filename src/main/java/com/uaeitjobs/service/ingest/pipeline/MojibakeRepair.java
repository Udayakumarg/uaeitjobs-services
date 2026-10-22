package com.uaeitjobs.service.ingest.pipeline;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Repairs text whose UTF-8 bytes were mis-read as Latin-1 somewhere upstream, which turns a
 * right quote into "â" + two invisible control characters. Some feeds (RemoteOK is the known
 * one: ~3 in 4 of its jobs) publish text already in that state, so the damage is in the source
 * data and cannot be fixed by choosing a charset when reading the response.
 *
 * <p>Conservative on purpose: a string is only rewritten when every character fits in Latin-1,
 * it contains at least one character in the 0x80-0xFF range, and the re-decode is strict UTF-8
 * that actually shortens it. Clean text, genuine non-Latin text (Arabic, CJK, emoji) and strings
 * that merely contain an accented letter come back unchanged.
 */
public final class MojibakeRepair {

    private MojibakeRepair() {}

    public static String fix(String text) {
        if (text == null || text.length() < 2) return text;

        boolean hasHighLatin1 = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c > 0xFF) return text;           // not a pure Latin-1 reading: leave alone
            if (c >= 0x80) hasHighLatin1 = true;
        }
        if (!hasHighLatin1) return text;

        try {
            String repaired = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(text.getBytes(StandardCharsets.ISO_8859_1)))
                    .toString();
            return repaired.length() < text.length() ? repaired : text;
        } catch (CharacterCodingException notUtf8) {
            return text;                          // e.g. a plain "café" is valid Latin-1, not UTF-8 bytes
        }
    }
}
