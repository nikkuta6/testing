package by.pranovich.testing.parser;

/** Syntax error with the absolute character position in the source text. */
public final class ParseException extends RuntimeException {

    private final String detail;
    private final int position;

    public ParseException(String message, int position) {
        super(message + " (позиция " + position + ")");
        this.detail = message;
        this.position = position;
    }

    public String detail() {
        return detail;
    }

    public int position() {
        return position;
    }
}
