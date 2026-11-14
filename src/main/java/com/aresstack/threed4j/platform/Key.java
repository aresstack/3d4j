package com.aresstack.threed4j.platform;

/** Platform-independent key codes. Extend as needed and map the new keys in the window classes. */
public enum Key {
    ESCAPE, ENTER, SPACE, TAB, BACKSPACE, SHIFT, CONTROL, CAPS_LOCK,
    LEFT, RIGHT, UP, DOWN,
    A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W, X, Y, Z,
    DIGIT_0, DIGIT_1, DIGIT_2, DIGIT_3, DIGIT_4, DIGIT_5, DIGIT_6, DIGIT_7, DIGIT_8, DIGIT_9;

    private static final Key[] VALUES = values();

    /** {@code 'A'..'Z'} (also lower case) to the matching key, otherwise {@code null}. */
    public static Key letter(int character) {
        int upper = character >= 'a' && character <= 'z' ? character - 'a' + 'A' : character;
        return upper >= 'A' && upper <= 'Z' ? VALUES[A.ordinal() + (upper - 'A')] : null;
    }

    /** {@code '0'..'9'} to the matching key, otherwise {@code null}. */
    public static Key digit(int character) {
        return character >= '0' && character <= '9' ? VALUES[DIGIT_0.ordinal() + (character - '0')] : null;
    }
}
