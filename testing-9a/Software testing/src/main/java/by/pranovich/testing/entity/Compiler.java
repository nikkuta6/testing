package by.pranovich.testing.entity;

public class Compiler {
    private String text;
    private SyntaxError syntaxError;

    public Compiler(String text) {
        this.text = text;
    }

    public boolean compile() {
        if (syntaxError == null || syntaxError.getMessage() == null) {
            return true;
        }
        return false;
    }

    public SyntaxError getSyntaxError() {
        return syntaxError;
    }

    public void setSyntaxError(SyntaxError syntaxError) {
        this.syntaxError = syntaxError;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
