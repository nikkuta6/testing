package by.pranovich.testing.runtime;

/** Value of the {@code complex} type. Negative zeros are normalized, so equals() and toString() ignore them. */
public record Complex(double re, double im) {

    public static final Complex ZERO = new Complex(0, 0);
    public static final Complex ONE = new Complex(1, 0);

    public Complex {
        re += 0.0;
        im += 0.0;
    }

    public Complex plus(Complex other) {
        return new Complex(re + other.re, im + other.im);
    }

    public Complex minus(Complex other) {
        return new Complex(re - other.re, im - other.im);
    }

    public Complex times(Complex other) {
        return new Complex(re * other.re - im * other.im, re * other.im + im * other.re);
    }

    /**
     * The divisor is first scaled by a power of two (exactly, unlike Smith's ratio d / c), so that its
     * squared modulus neither overflows for 1e200 nor underflows for 1e-200; (5 + 5i) / (3 - 1i) stays exact.
     */
    public Complex dividedBy(Complex other) {
        if (other.re == 0.0 && other.im == 0.0) {
            throw new ArithmeticException("Деление на ноль");
        }
        int scale = Math.getExponent(Math.max(Math.abs(other.re), Math.abs(other.im)));
        double c = Math.scalb(other.re, -scale);
        double d = Math.scalb(other.im, -scale);
        double denominator = c * c + d * d;
        return new Complex(Math.scalb((re * c + im * d) / denominator, -scale),
                Math.scalb((im * c - re * d) / denominator, -scale));
    }

    /**
     * Integer exponents are multiplied out (so (1i)^2 is exactly -1), the rest goes through the polar form.
     * A negative exponent inverts the base first, so 2^-2000 underflows to 0 instead of 1 / Infinity.
     */
    public Complex pow(Complex exponent) {
        if (exponent.im == 0 && exponent.re == Math.rint(exponent.re)
                && Math.abs(exponent.re) <= Integer.MAX_VALUE) {
            Complex result = ONE;
            Complex base = exponent.re < 0 ? ONE.dividedBy(this) : this;
            for (long n = (long) Math.abs(exponent.re); n > 0; n >>= 1) {
                if ((n & 1) == 1) {
                    result = result.times(base);
                }
                base = base.times(base);
            }
            return result;
        }
        if (equals(ZERO)) {
            if (exponent.re > 0) {
                return ZERO;
            }
            throw new ArithmeticException("Деление на ноль");
        }
        Complex power = exponent.times(new Complex(Math.log(abs()), arg()));
        double modulus = Math.exp(power.re);
        return new Complex(modulus * Math.cos(power.im), modulus * Math.sin(power.im));
    }

    public Complex negate() {
        return new Complex(-re, -im);
    }

    public Complex conj() {
        return new Complex(re, -im);
    }

    public double abs() {
        return Math.hypot(re, im);
    }

    public double arg() {
        return Math.atan2(im, re);
    }

    @Override
    public String toString() {
        return re + (im < 0 ? "-" : "+") + Math.abs(im) + "i";
    }
}
