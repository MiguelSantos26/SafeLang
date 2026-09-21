package Measures;

public class Value {
    protected double val;
    protected String type;
    protected String strVal;

    public Value(String type, double val) {
        this.type = type;
        this.val = val;
        this.strVal = null;
    }

    public Value(String type, String strVal) {
        this.type = type;
        this.strVal = strVal;
        this.val = 0;
    }

    public double getVal() {
        return this.val;
    }

    public String getType() {
        return this.type;
    }

    public String getStrVal() {
        return this.strVal;
    }

    public Value add(Value other) {
        return new Value(this.type, this.val + other.val);
    }

    public Value sub(Value other) {
        return new Value(this.type, this.val - other.val);
    }

    public Value mul(Value other) {
        return new Value(this.type, this.val * other.val);
    }

    public Value div(Value other) {
        return new Value(this.type, this.val / other.val);
    }

    public String toString() {
        if (type.equals("string")) return strVal != null ? strVal : "";
        if (type.equals("integer")) return String.valueOf((long) val);
        return String.valueOf(val);
    }
}
