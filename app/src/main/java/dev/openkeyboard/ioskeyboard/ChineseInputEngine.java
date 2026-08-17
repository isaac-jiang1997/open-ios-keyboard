package dev.openkeyboard.ioskeyboard;

import java.util.List;

interface ChineseInputEngine {
    void setLayout(ChineseKeyboardLayout layout);

    void setLearningAllowed(boolean allowed);

    void reset();

    boolean hasComposition();

    String preedit();

    List<String> candidates();

    List<String> candidateAnnotations();

    void appendAsciiLetter(String letter);

    void appendNineKeyDigit(String digit);

    String consumePendingCommit();

    boolean backspace();

    String commitCandidate(String text);

    String commitBestCandidateOrRaw();

    String commitRaw();
}
