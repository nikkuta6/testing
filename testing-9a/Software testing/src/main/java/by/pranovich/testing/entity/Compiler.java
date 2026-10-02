package by.pranovich.testing.entity;

public class Compiler {
    private String text;
    private SyntaxError syntaxError;

    public Compiler(String text) {
        this.text = text;
    }

    public boolean compile() {
        boolean result;
        if (syntaxError == null || syntaxError.getMessage() == null) {
            result = true;
        } else {
            result = false;
        }
        return result;
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
