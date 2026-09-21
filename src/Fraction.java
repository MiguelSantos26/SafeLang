import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

class Fraction {
    final BigInteger num, den;

    Fraction(BigInteger n, BigInteger d) {
        if (d.signum() < 0) { n = n.negate(); d = d.negate(); }
        BigInteger g = n.abs().gcd(d.abs());
        if (g.signum() == 0) g = BigInteger.ONE;
        num = n.divide(g);
        den = d.divide(g);
    }

    static final Fraction ZERO = new Fraction(BigInteger.ZERO, BigInteger.ONE);
    static final Fraction ONE  = new Fraction(BigInteger.ONE,  BigInteger.ONE);

    Fraction add(Fraction o) {
        return new Fraction(
            num.multiply(o.den).add(o.num.multiply(den)),
            den.multiply(o.den));
    }

    Fraction sub(Fraction o) {
        return new Fraction(
            num.multiply(o.den).subtract(o.num.multiply(den)),
            den.multiply(o.den));
    }

    Fraction mul(Fraction o) {
        return new Fraction(num.multiply(o.num), den.multiply(o.den));
    }

    Fraction mul(BigInteger i) {
        return new Fraction(num.multiply(i), den);
    }

    Fraction div(Fraction o) {
        return new Fraction(num.multiply(o.den), den.multiply(o.num));
    }

    BigInteger toBigInteger() {
        return num.divide(den);
    }

    int compareTo(Fraction o) {
        return num.multiply(o.den).compareTo(o.num.multiply(den));
    }

    // Parses a decimal string (with optional e-notation) into an exact Fraction.
    // e.g. "1.333" -> 1333/1000,  "0.3e10" -> 3000000000/1,  "2/3" is NOT handled here.
    static Fraction fromDecimal(String s) {
        s = s.trim().toLowerCase();
        BigInteger exp = BigInteger.ZERO;
        int eIdx = s.indexOf('e');
        if (eIdx >= 0) {
            exp = new BigInteger(s.substring(eIdx + 1));
            s = s.substring(0, eIdx);
        }
        BigInteger n, d;
        int dot = s.indexOf('.');
        if (dot < 0) {
            n = new BigInteger(s);
            d = BigInteger.ONE;
        } else {
            int dec = s.length() - dot - 1;
            n = new BigInteger(s.replace(".", ""));
            d = BigInteger.TEN.pow(dec);
        }
        if (exp.signum() >= 0) {
            n = n.multiply(BigInteger.TEN.pow(exp.intValue()));
        } else {
            d = d.multiply(BigInteger.TEN.pow(exp.negate().intValue()));
        }
        return new Fraction(n, d);
    }

    @Override
    public String toString() {
        if (den.equals(BigInteger.ONE)) return num.toString();
        BigDecimal result = new BigDecimal(num)
            .divide(new BigDecimal(den), 10, RoundingMode.HALF_UP)
            .stripTrailingZeros();
        return result.toPlainString();
    }
}
