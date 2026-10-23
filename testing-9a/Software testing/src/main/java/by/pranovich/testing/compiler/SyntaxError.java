package by.pranovich.testing.compiler;

/** Human-readable location of a source-code compilation error. */
public final class SyntaxError {

    private String message;
    private int lineNumber;
    private int position;

    public SyntaxError(String message, int lineNumber, int position) {
        this.message = message;
        this.lineNumber = lineNumber;
        this.position = position;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    @Override
    public String toString() {
        return "Строка " + lineNumber + ", позиция " + position + ": " + message;
    }
}
