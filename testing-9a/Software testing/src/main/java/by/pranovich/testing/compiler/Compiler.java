package by.pranovich.testing.compiler;

import by.pranovich.testing.bytecode.Program;
import by.pranovich.testing.parser.ParseException;
import by.pranovich.testing.parser.RecursiveDescentParser;

import java.util.Objects;

/** Public facade that turns source text into an executable byte-code program. */
public final class Compiler {

    private String text;
    private SyntaxError syntaxError;
    private Program program;

    public Compiler(String text) {
        this.text = Objects.requireNonNull(text, "text");
    }

    public boolean compile() {
        if (syntaxError != null && syntaxError.getMessage() != null) {
            return false;
        }

        try {
            RecursiveDescentParser parser = new RecursiveDescentParser(text);
            parser.parseProgram();
            program = parser.getProgram();
            syntaxError = null;
            return true;
        } catch (ParseException exception) {
            syntaxError = toSyntaxError(exception);
            program = null;
            return false;
        }
    }

    public Program getProgram() {
        return program;
    }

    public SyntaxError getSyntaxError() {
        return syntaxError;
    }

    /** Retained for compatibility with the original public API. */
    public void setSyntaxError(SyntaxError syntaxError) {
        this.syntaxError = syntaxError;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = Objects.requireNonNull(text, "text");
        this.syntaxError = null;
        this.program = null;
    }

    private SyntaxError toSyntaxError(ParseException exception) {
        int absolutePosition = Math.min(exception.position(), text.length());
        int lineNumber = 1;
        int lineStart = 0;

        for (int index = 0; index < absolutePosition; index++) {
            if (text.charAt(index) == '\n') {
                lineNumber++;
                lineStart = index + 1;
            }
        }

        int positionInLine = absolutePosition - lineStart + 1;
        return new SyntaxError(exception.detail(), lineNumber, positionInLine);
    }
}
