import java.io.IOException;
import java.util.*;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import org.stringtemplate.v4.*;

@SuppressWarnings("CheckReturnValue")
class Translator extends SafeLangBaseVisitor<String> {

    private String className;
    private STGroup templates;

    // ── Translator state ─────────────────────────────────────────────────
    private Set<String>                           declared;       // Java-declared variable names
    private Set<String>                           uninitialized;  // declared but not yet assigned
    private Map<String, String>                   varTypes;    // id → "BigInteger"|"Fraction"|"String"
    private Map<String, String>                   varSafeTypes; // id → SafeLang type name (for dimensional suffixes)
    private Map<String, String>                   dimensions;  // SafeLang dim name → Java base type
    private Map<String, String>                   dimSuffix;
    private Map<String, HashMap<String, Integer>> dimVectors;
    private Map<String, Integer>                  dimBits;  // dim name → bit width for integer[N]/real[N]
    private Map<String, Integer>                  varBits;  // variable name → bit width
    private boolean needsScanner;

    Translator(){
        this.className = "Output";
    }

    Translator(String className) {
        this.className = className;
    }

    // ── Program ──────────────────────────────────────────────────────────

    @Override
    public String visitProgram(SafeLangParser.ProgramContext ctx) {
        if (this.declared == null) {
            java.net.URL stgUrl = Translator.class.getResource("SafeLangToJava.stg");
            this.templates    = stgUrl != null
                ? new STGroupFile(stgUrl, "UTF-8", '<', '>')
                : new STGroupFile("src/SafeLangToJava.stg");
            this.declared       = new HashSet<>();
            this.uninitialized  = new HashSet<>();
            this.varTypes       = new HashMap<>();
            this.varSafeTypes   = new HashMap<>();
            this.dimensions     = new HashMap<>();
            this.dimSuffix    = new HashMap<>();
            this.dimVectors   = new HashMap<>();
            this.dimBits      = new HashMap<>();
            this.varBits      = new HashMap<>();
            this.needsScanner = false;
        }

        List<String> stats = new ArrayList<>();
        for (SafeLangParser.StatContext s : ctx.statList().stat()) {
            String r = visit(s);
            if (r != null && !r.isEmpty()) stats.add(r);
        }

        ST tmpl = templates.getInstanceOf("program");
        tmpl.add("className", className);
        tmpl.add("stats", stats);
        if (needsScanner) tmpl.add("needsScanner", true);
        return tmpl.render();
    }

    // Inline the included file — shares all state with the parent translator.
    @Override
    public String visitUse(SafeLangParser.UseContext ctx) {
        String filename = ctx.STRING().getText();
        filename = filename.substring(1, filename.length() - 1);
        try {
            CharStream input = CharStreams.fromFileName(filename);
            SafeLangLexer  lexer  = new SafeLangLexer(input);
            SafeLangParser parser = new SafeLangParser(new CommonTokenStream(lexer));
            SafeLangParser.ProgramContext prog = parser.program();

            List<String> parts = new ArrayList<>();
            for (SafeLangParser.StatContext s : prog.statList().stat()) {
                String r = visit(s);
                if (r != null && !r.isEmpty()) parts.add(r);
            }
            return String.join("\n", parts);
        } catch (IOException e) {
            System.err.println("ERROR: Cannot open file '" + filename + "': " + e.getMessage());
            return "";
        }
    }

    @Override
    public String visitStat(SafeLangParser.StatContext ctx) {
        if (ctx.RETRY()  != null) return "continue;";
        if (ctx.FAIL()   != null) return "throw new RuntimeException(\"fail\");";
        if (ctx.ASSERT() != null) {
            String cond = visit(ctx.expr());
            return "if (!(" + cond + ")) throw new RuntimeException(\"assertion failed\");";
        }
        return visitChildren(ctx);
    }

    // ── Assignments ──────────────────────────────────────────────────────

    @Override
    public String visitAssign(SafeLangParser.AssignContext ctx) {
        String id       = ctx.ID().getText();
        String slType   = ctx.type().getText();
        String javaType = toJavaType(slType);
        declared.add(id);
        uninitialized.add(id);
        varTypes.put(id, javaType);
        if (dimensions.containsKey(slType) || slType.startsWith("list[")) varSafeTypes.put(id, slType);
        Integer bits = dimBits.get(slType);
        if (bits != null) varBits.put(id, bits);

        ST tmpl = templates.getInstanceOf("varDecl");
        tmpl.add("javaType",   javaType);
        tmpl.add("id",         id);
        tmpl.add("defaultVal", defaultVal(javaType));
        return tmpl.render();
    }

   @Override
    public String visitAssignDecl(SafeLangParser.AssignDeclContext ctx) {
        String  id    = ctx.ID().getText();
        boolean isTry = ctx.assign_op().getText().equals(":=?");
        int     line  = ctx.getStart().getLine();
        String  expr  = visit(ctx.expr());

        String javaType;
        if (ctx.type() != null) {
            javaType = toJavaType(ctx.type().getText());
        } else if (varTypes.containsKey(id)) {
            javaType = varTypes.get(id);
        } else {
            javaType = inferJavaType(ctx.expr());
        }

        // Coerce when Java types don't match.
        String exprType = inferJavaType(ctx.expr());
        if (javaType.equals("BigInteger") && exprType.equals("Fraction"))
            expr = "(" + expr + ").toBigInteger()";
        else if (javaType.equals("Fraction") && exprType.equals("BigInteger"))
            expr = "_toFrac(" + expr + ")";

        Integer bits = null;
        if (ctx.type() != null) {
            String slType = ctx.type().getText();
            if (dimensions.containsKey(slType) || slType.startsWith("list[")) {
                varSafeTypes.put(id, slType);
            }
            bits = dimBits.get(slType);
        } else {
            String st = inferSafeType(ctx.expr());
            if (st != null) varSafeTypes.put(id, st);
        }
        if (bits == null) bits = varBits.get(id);
        if (bits != null) varBits.put(id, bits);

        if (bits != null && javaType.equals("BigInteger"))
            expr = "_checkBits(" + expr + ", " + bits + ")";

        boolean firstDecl = !declared.contains(id);
        declared.add(id);
        uninitialized.remove(id);
        varTypes.put(id, javaType);

        if (firstDecl) {
            if (isTry) {
                ST tmpl = templates.getInstanceOf("varDeclAssignTry");
                tmpl.add("javaType",   javaType);
                tmpl.add("id",         id);
                tmpl.add("expr",       expr);
                tmpl.add("line",       line);
                tmpl.add("defaultVal", defaultVal(javaType));
                return tmpl.render();
            } else {
                ST tmpl = templates.getInstanceOf("varDeclAssign");
                tmpl.add("javaType", javaType);
                tmpl.add("id",       id);
                tmpl.add("expr",     expr);
                return tmpl.render();
            }
        } else {
            if (isTry) {
                ST tmpl = templates.getInstanceOf("varAssignTry");
                tmpl.add("id",   id);
                tmpl.add("expr", expr);
                tmpl.add("line", line);
                return tmpl.render();
            } else {
                ST tmpl = templates.getInstanceOf("varAssign");
                tmpl.add("id",   id);
                tmpl.add("expr", expr);
                return tmpl.render();
            }
        }
    }

    @Override
    public String visitAppendStat(SafeLangParser.AppendStatContext ctx) {
        // expr >> expr (O índice 0 é o valor, o índice 1 é a lista alvo)
        String val = visit(ctx.expr(0));
        String list = visit(ctx.expr(1));

        String valJavaType = inferJavaType(ctx.expr(0));
        String listJavaType = inferJavaType(ctx.expr(1));

        // Conversão forçada estrita, por exemplo um array de NMEC(BigInteger) que 
        // interage temporariamente com unit(Fraction).
        if (listJavaType.startsWith("java.util.ArrayList<")) {
            String innerType = listJavaType.substring(20, listJavaType.length() - 1);
            if (innerType.equals("BigInteger") && valJavaType.equals("Fraction")) {
                val = "(" + val + ").toBigInteger()";
            } else if (innerType.equals("Fraction") && valJavaType.equals("BigInteger")) {
                val = "_toFrac(" + val + ")";
            }
        }

        ST tmpl = templates.getInstanceOf("statAppend");
        tmpl.add("list", list);
        tmpl.add("val", val);
        return tmpl.render();
    }

    @Override
    public String visitExprNewList(SafeLangParser.ExprNewListContext ctx) {
        return "new java.util.ArrayList<>()";
       }

    @Override
    public String visitExprLength(SafeLangParser.ExprLengthContext ctx) {
        String list = visit(ctx.expr());
        ST tmpl = templates.getInstanceOf("exprLength");
        tmpl.add("list", list);
        return tmpl.render();
    }

    @Override
    public String visitExprListAccess(SafeLangParser.ExprListAccessContext ctx) {
        String listId = "v_" + ctx.ID().getText();
        String indexExpr = visit(ctx.expr());
        ST tmpl = templates.getInstanceOf("exprListAccess");
        tmpl.add("list", listId);
        tmpl.add("index", indexExpr);
        return tmpl.render();
    }

    // Método opcional do ForStat (crucial para o loop da lista no des-02.sl funcionar corretamente)
    @Override
    public String visitForStat(SafeLangParser.ForStatContext ctx) {
        String id = ctx.ID().getText();
        String start = visit(ctx.expr(0));
        String end = visit(ctx.expr(1));
        
        uninitialized.remove(id);

        List<String> stats = collectStats(ctx.statList());

        ST tmpl = templates.getInstanceOf("forStat");
        tmpl.add("id", id);
        tmpl.add("start", start);
        tmpl.add("end", end);
        tmpl.add("stats", stats);
        return tmpl.render();
    }

    @Override
    public String visitExprFormat3(SafeLangParser.ExprFormat3Context ctx) {
        String expr = visit(ctx.expr(0));
        String width = visit(ctx.expr(1));
        String align = visit(ctx.expr(2));
        
        // Lógica inline para injetar um alinhamento rudimentar no código gerado (suporta left, center e right)
        return "( " + align + ".contains(\"left\") ? String.format(\"%-\" + (" + width + ").intValue() + \"s\", _str(" + expr + ")) : " +
               align + ".contains(\"center\") ? \" \".repeat(Math.max(0, ((" + width + ").intValue() - _str(" + expr + ").length()) / 2)) + _str(" + expr + ") + \" \".repeat(Math.max(0, (" + width + ").intValue() - _str(" + expr + ").length() - ((" + width + ").intValue() - _str(" + expr + ").length()) / 2)) : " +
               "String.format(\"%\" + (" + width + ").intValue() + \"s\", _str(" + expr + ")) )";
    }

    @Override public String visitType     (SafeLangParser.TypeContext      ctx) { return null; }
    @Override public String visitAssign_op(SafeLangParser.Assign_opContext ctx) { return null; }

    // ── Output ───────────────────────────────────────────────────────────

    @Override
    public String visitWriteExpr(SafeLangParser.WriteExprContext ctx) {
        List<String> parts = new ArrayList<>();
        for (SafeLangParser.ExprContext e : ctx.expr())
            parts.add(wrapStr(e));
        ST tmpl = templates.getInstanceOf("writeExpr");
        tmpl.add("parts", parts);
        return tmpl.render();
    }

    @Override
    public String visitWriteNewline(SafeLangParser.WriteNewlineContext ctx) {
        if (ctx.expr().isEmpty())
            return templates.getInstanceOf("writelnEmpty").render();
        List<String> parts = new ArrayList<>();
        for (SafeLangParser.ExprContext e : ctx.expr())
            parts.add(wrapStr(e));
        ST tmpl = templates.getInstanceOf("writelnExpr");
        tmpl.add("parts", parts);
        return tmpl.render();
    }

    private String wrapStr(SafeLangParser.ExprContext e) {
        String code = visit(e);
        if (inferJavaType(e).equals("String")) return code;
        return withSuffix("_str(" + code + ")", inferSafeType(e));
    }

    // ── Type and unit declarations ────────────────────────────────────────

    @Override
    public String visitTypeDecl(SafeLangParser.TypeDeclContext ctx) {
        String dimName  = ctx.ID().getText();
        String slType   = ctx.type().getText();
        String javaType = toJavaType(slType);

        dimensions.put(dimName, javaType);

        if (slType.contains("[")) {
            int lo = slType.indexOf('[') + 1, hi = slType.indexOf(']');
            if (lo > 0 && hi > lo) dimBits.put(dimName, Integer.parseInt(slType.substring(lo, hi)));
        }

        HashMap<String, Integer> vec;
        if (ctx.dimExpr() != null) {
            vec = evalDimExpr(ctx.dimExpr());
        } else {
            vec = new HashMap<>();
            vec.put(dimName, 1);
        }
        dimVectors.put(dimName, vec);

        if (ctx.unitSpec() == null && ctx.dimExpr() != null) {
            ST tmpl = templates.getInstanceOf("typeDeclDerived");
            tmpl.add("name",    dimName);
            tmpl.add("javaType", javaType);
            tmpl.add("dimExpr", ctx.dimExpr().getText());
            return tmpl.render();
        }

        if (ctx.unitSpec() != null) {
            List<TerminalNode> ids      = ctx.unitSpec().ID();
            String             unitName = ids.get(0).getText();
            declared.add(unitName);
            varTypes.put(unitName, "Fraction"); // unit constants are always Fraction
            varSafeTypes.put(unitName, dimName);

            if (ids.size() > 1) {
                String suffix = ids.get(1).getText();
                dimSuffix.put(dimName, suffix);
                ST tmpl = templates.getInstanceOf("typeDeclBase");
                tmpl.add("name",     dimName);
                tmpl.add("javaType", javaType);
                tmpl.add("unit",     unitName);
                tmpl.add("suffix",   suffix);
                return tmpl.render();
            } else {
                ST tmpl = templates.getInstanceOf("typeDeclBaseNoSuffix");
                tmpl.add("name",     dimName);
                tmpl.add("javaType", javaType);
                tmpl.add("unit",     unitName);
                return tmpl.render();
            }
        }

        return "// type " + dimName + ": " + slType;
    }

    @Override public String visitUnitSpec(SafeLangParser.UnitSpecContext ctx) { return null; }

    @Override
    public String visitUnitDecl(SafeLangParser.UnitDeclContext ctx) {
        List<TerminalNode> ids      = ctx.ID();
        String             dimName  = ids.get(0).getText();
        String             unitName = ids.get(1).getText();
        String             expr     = visit(ctx.expr());
        declared.add(unitName);
        varTypes.put(unitName, "Fraction"); // unit constants are always Fraction
        varSafeTypes.put(unitName, dimName);

        ST tmpl = templates.getInstanceOf("unitDecl");
        tmpl.add("unitName", unitName);
        tmpl.add("expr",     expr);
        return tmpl.render();
    }

    // ── Expressions ──────────────────────────────────────────────────────

    @Override
    public String visitExprInteger(SafeLangParser.ExprIntegerContext ctx) {
        ST tmpl = templates.getInstanceOf("litInt");
        tmpl.add("val", ctx.INTEGER().getText());
        return tmpl.render();
    }

    @Override
    public String visitExprRealPoint(SafeLangParser.ExprRealPointContext ctx) {
        ST tmpl = templates.getInstanceOf("litReal");
        tmpl.add("val", ctx.REALPOINT().getText());
        return tmpl.render();
    }

    @Override
    public String visitExprRealFrac(SafeLangParser.ExprRealFracContext ctx) {
        String[] parts = ctx.REALFRAC().getText().split("/");
        ST tmpl = templates.getInstanceOf("litRealFrac");
        tmpl.add("n", parts[0]);
        tmpl.add("d", parts[1]);
        return tmpl.render();
    }

    @Override
    public String visitExprString(SafeLangParser.ExprStringContext ctx) {
        return ctx.STRING().getText();
    }

    @Override
    public String visitExprBoolLiteral(SafeLangParser.ExprBoolLiteralContext ctx) {
        return ctx.BOOL_LITERAL().getText();
    }

    @Override
    public String visitExprCastUser(SafeLangParser.ExprCastUserContext ctx) {
        String typeName = ctx.ID().getText();
        String expr     = visit(ctx.expr());
        Integer bits    = dimBits.get(typeName);
        if (bits != null && toJavaType(typeName).equals("BigInteger"))
            return "_truncBits(" + expr + ", " + bits + ")";
        return expr; // dimensionless or real type — identity
    }

    @Override
    public String visitExprID(SafeLangParser.ExprIDContext ctx) {
        String id = ctx.ID().getText();
        if (uninitialized.contains(id)) {
            System.err.println("[line " + ctx.getStart().getLine() + "] ERROR: Variable '" + id + "' used before being initialized");
            System.exit(1);
        }
        ST tmpl = templates.getInstanceOf("exprId");
        tmpl.add("name", id);
        return tmpl.render();
    }

    @Override
    public String visitExprParent(SafeLangParser.ExprParentContext ctx) {
        ST tmpl = templates.getInstanceOf("exprParen");
        tmpl.add("expr", visit(ctx.expr()));
        return tmpl.render();
    }

    @Override
    public String visitExprAddSub(SafeLangParser.ExprAddSubContext ctx) {
        String left  = visit(ctx.e1);
        String right = visit(ctx.e2);
        String op    = ctx.op.getText();

        if (op.equals("+")) {
            String lt = inferJavaType(ctx.e1);
            String rt = inferJavaType(ctx.e2);
            if (lt.equals("String") || rt.equals("String")) {
                ST tmpl = templates.getInstanceOf("exprConcat");
                tmpl.add("left",  left);
                tmpl.add("right", right);
                return tmpl.render();
            }
        }

        ST tmpl = templates.getInstanceOf(op.equals("+") ? "exprAdd" : "exprSub");
        tmpl.add("left",  left);
        tmpl.add("right", right);
        return tmpl.render();
    }

    @Override
    public String visitExprMultDiv(SafeLangParser.ExprMultDivContext ctx) {
        String left  = visit(ctx.e1);
        String right = visit(ctx.e2);
        String op    = ctx.op.getText();

        if (op.equals("/")) {
            ST tmpl = templates.getInstanceOf("exprDiv");
            tmpl.add("left",  left);
            tmpl.add("right", right);
            return tmpl.render();
        }

        String lt = inferJavaType(ctx.e1);
        String rt = inferJavaType(ctx.e2);

        if (lt.equals("BigInteger") && rt.equals("BigInteger")) {
            ST tmpl = templates.getInstanceOf("exprMulInt");
            tmpl.add("left",  left);
            tmpl.add("right", right);
            return tmpl.render();
        }

        ST tmpl = templates.getInstanceOf("exprMulFrac");
        tmpl.add("left",  "_toFrac(" + left  + ")");
        tmpl.add("right", "_toFrac(" + right + ")");
        return tmpl.render();
    }

    @Override
    public String visitExprQuoRem(SafeLangParser.ExprQuoRemContext ctx) {
        boolean isDiv = ctx.op.getType() == SafeLangParser.QUO;
        ST tmpl = templates.getInstanceOf(isDiv ? "exprIntDiv" : "exprIntMod");
        tmpl.add("left",  visit(ctx.e1));
        tmpl.add("right", visit(ctx.e2));
        return tmpl.render();
    }

    @Override
    public String visitExprCompare(SafeLangParser.ExprCompareContext ctx) {
        String left  = visit(ctx.e1);
        String right = visit(ctx.e2);
        String op    = ctx.op.getText();
        String lt    = inferJavaType(ctx.e1);
        String rt    = inferJavaType(ctx.e2);

        if (lt.equals("boolean"))
            return op.equals("=") ? "(" + left + " == " + right + ")"
                                   : "(" + left + " != " + right + ")";
        if (lt.equals("String"))
            return op.equals("=") ? "(" + left + ").equals(" + right + ")"
                                   : "!(" + left + ").equals(" + right + ")";

        if (!lt.equals(rt)) {
            if (lt.equals("BigInteger")) left  = "_toFrac(" + left  + ")";
            else                         right = "_toFrac(" + right + ")";
        }
        switch (op) {
            case "=":  return "(" + left + ").compareTo(" + right + ") == 0";
            case "<>": return "(" + left + ").compareTo(" + right + ") != 0";
            case "<":  return "(" + left + ").compareTo(" + right + ") < 0";
            case ">":  return "(" + left + ").compareTo(" + right + ") > 0";
            case "<=": return "(" + left + ").compareTo(" + right + ") <= 0";
            case ">=": return "(" + left + ").compareTo(" + right + ") >= 0";
            default:   return "false";
        }
    }

    @Override
    public String visitExprAnd(SafeLangParser.ExprAndContext ctx) {
        return "(" + visit(ctx.e1) + " && " + visit(ctx.e2) + ")";
    }

    @Override
    public String visitExprOr(SafeLangParser.ExprOrContext ctx) {
        return "(" + visit(ctx.e1) + " || " + visit(ctx.e2) + ")";
    }

    @Override
    public String visitExprNot(SafeLangParser.ExprNotContext ctx) {
        return "!(" + visit(ctx.expr()) + ")";
    }

    @Override
    public String visitExprCast2Int(SafeLangParser.ExprCast2IntContext ctx) {
        String expr    = visit(ctx.expr());
        String srcType = inferJavaType(ctx.expr());
        if (srcType.equals("String")) {
            ST tmpl = templates.getInstanceOf("castStrToInt");
            tmpl.add("expr", expr);
            return tmpl.render();
        } else if (srcType.equals("Fraction")) {
            ST tmpl = templates.getInstanceOf("castFracToInt");
            tmpl.add("expr", expr);
            return tmpl.render();
        }
        return expr; // already BigInteger
    }

    @Override
    public String visitExprCast2Real(SafeLangParser.ExprCast2RealContext ctx) {
        String expr    = visit(ctx.expr());
        String srcType = inferJavaType(ctx.expr());
        if (srcType.equals("String")) {
            ST tmpl = templates.getInstanceOf("castStrToReal");
            tmpl.add("expr", expr);
            return tmpl.render();
        } else if (srcType.equals("BigInteger")) {
            ST tmpl = templates.getInstanceOf("castBigIntToReal");
            tmpl.add("expr", expr);
            return tmpl.render();
        }
        return expr; // already Fraction
    }

    @Override
    public String visitExprCast2String(SafeLangParser.ExprCast2StringContext ctx) {
        return withSuffix("_str(" + visit(ctx.expr()) + ")", inferSafeType(ctx.expr()));
    }

    @Override
    public String visitExprRead(SafeLangParser.ExprReadContext ctx) {
        needsScanner = true;
        ST tmpl = templates.getInstanceOf("exprRead");
        tmpl.add("prompt", ctx.STRING().getText());
        return tmpl.render();
    }

    @Override
    public String visitExprFormat(SafeLangParser.ExprFormatContext ctx) {
        ST tmpl = templates.getInstanceOf("exprFormat");
        tmpl.add("expr",  visit(ctx.expr(0)));
        tmpl.add("width", visit(ctx.expr(1)));
        return tmpl.render();
    }

    // ── Tratamento de erros ──────────────────────────────────────────────

    @Override
    public String visitTryStat(SafeLangParser.TryStatContext ctx) {
        List<String> hoisted  = preHoist(ctx.statList(0));
        List<String> tryStats = collectStats(ctx.statList(0));

        String tryBlock;
        if (ctx.statList().size() > 1) {
            List<String> rescueStats = collectStats(ctx.statList(1));
            ST tmpl = templates.getInstanceOf("tryRescueStat");
            tmpl.add("tryStats",    tryStats);
            tmpl.add("rescueStats", rescueStats);
            tryBlock = tmpl.render();
        } else {
            ST tmpl = templates.getInstanceOf("tryStat");
            tmpl.add("stats", tryStats);
            tryBlock = tmpl.render();
        }

        if (hoisted.isEmpty()) return tryBlock;
        return String.join("\n", hoisted) + "\n" + tryBlock;
    }

    // Hoist first-time variable declarations out of a try block so they are
    // visible in the enclosing scope after the try/rescue block ends.
    private List<String> preHoist(SafeLangParser.StatListContext ctx) {
        List<String> hoisted = new ArrayList<>();
        for (SafeLangParser.StatContext s : ctx.stat()) {
            if (s.assignment() == null) continue;
            if (!(s.assignment() instanceof SafeLangParser.AssignDeclContext)) continue;
            SafeLangParser.AssignDeclContext ad = (SafeLangParser.AssignDeclContext) s.assignment();
            String id = ad.ID().getText();
            if (declared.contains(id)) continue;

            String javaType;
            if (ad.type() != null) {
                String slType = ad.type().getText();
                javaType = toJavaType(slType);
                if (dimensions.containsKey(slType)) varSafeTypes.put(id, slType);
                Integer bits = dimBits.get(slType);
                if (bits != null) varBits.put(id, bits);
            } else {
                javaType = inferJavaType(ad.expr());
                String st = inferSafeType(ad.expr());
                if (st != null) varSafeTypes.put(id, st);
            }
            declared.add(id);
            varTypes.put(id, javaType);
            uninitialized.add(id);

            ST tmpl = templates.getInstanceOf("varDecl");
            tmpl.add("javaType",   javaType);
            tmpl.add("id",         id);
            tmpl.add("defaultVal", defaultVal(javaType));
            hoisted.add(tmpl.render());
        }
        return hoisted;
    }

    // ── Controlo de fluxo ────────────────────────────────────────────────

    @Override
    public String visitIfStat(SafeLangParser.IfStatContext ctx) {
        String cond           = visit(ctx.expr());
        List<String> thenStats = collectStats(ctx.statList(0));

        if (ctx.statList().size() > 1) {
            List<String> elseStats = collectStats(ctx.statList(1));
            ST tmpl = templates.getInstanceOf("ifElseStat");
            tmpl.add("cond",      cond);
            tmpl.add("thenStats", thenStats);
            tmpl.add("elseStats", elseStats);
            return tmpl.render();
        } else {
            ST tmpl = templates.getInstanceOf("ifStat");
            tmpl.add("cond",      cond);
            tmpl.add("thenStats", thenStats);
            return tmpl.render();
        }
    }

    @Override
    public String visitWhileStat(SafeLangParser.WhileStatContext ctx) {
        String cond = visit(ctx.expr());
        List<String> stats = collectStats(ctx.statList());
        ST tmpl = templates.getInstanceOf("whileStat");
        tmpl.add("cond",  cond);
        tmpl.add("stats", stats);
        return tmpl.render();
    }

    @Override
    public String visitUntilStat(SafeLangParser.UntilStatContext ctx) {
        String cond = visit(ctx.expr());
        List<String> stats = collectStats(ctx.statList());
        ST tmpl = templates.getInstanceOf("untilStat");
        tmpl.add("cond",  cond);
        tmpl.add("stats", stats);
        return tmpl.render();
    }

    private List<String> collectStats(SafeLangParser.StatListContext ctx) {
        List<String> stats = new ArrayList<>();
        for (SafeLangParser.StatContext s : ctx.stat()) {
            String r = visit(s);
            if (r != null && !r.isEmpty()) stats.add(r);
        }
        return stats;
    }

    // ── Prefix declarations ──────────────────────────────────────────────

    @Override
    public String visitPrefixStat(SafeLangParser.PrefixStatContext ctx) {
        String id   = ctx.ID().getText();
        String expr;
        if (ctx.DIM() != null) {
            expr = "Fraction.fromDecimal(\"" + ctx.DIM().getText() + "\")";
        } else {
            expr = "_toFrac(new BigInteger(\"" + ctx.INTEGER().getText() + "\"))";
        }
        declared.add(id);
        varTypes.put(id, "Fraction");
        ST tmpl = templates.getInstanceOf("unitDecl");
        tmpl.add("unitName", id);
        tmpl.add("expr",     expr);
        return tmpl.render();
    }

    // dimExpr nodes are only used via evalDimExpr; never visited directly
    @Override public String visitDimRef   (SafeLangParser.DimRefContext    ctx) { return null; }
    @Override public String visitDimMulDiv(SafeLangParser.DimMulDivContext ctx) { return null; }
    @Override public String visitDimPow   (SafeLangParser.DimPowContext    ctx) { return null; }
    @Override public String visitDimParen (SafeLangParser.DimParenContext  ctx) { return null; }

    // ── Dimensional suffix helpers ────────────────────────────────────────

    private String inferSafeType(SafeLangParser.ExprContext ctx) {
        if (ctx instanceof SafeLangParser.ExprIDContext) {
            return varSafeTypes.get(((SafeLangParser.ExprIDContext) ctx).ID().getText());
        }
        if (ctx instanceof SafeLangParser.ExprListAccessContext) {
            SafeLangParser.ExprListAccessContext c = (SafeLangParser.ExprListAccessContext) ctx;
            String listSafeType = varSafeTypes.get(c.ID().getText());
            if (listSafeType != null && listSafeType.startsWith("list[")) {
                return listSafeType.substring(5, listSafeType.length() - 1);
            }
            return null;
        }
        if (ctx instanceof SafeLangParser.ExprIDContext) {
            return varSafeTypes.get(((SafeLangParser.ExprIDContext) ctx).ID().getText());
        }
        if (ctx instanceof SafeLangParser.ExprParentContext)
            return inferSafeType(((SafeLangParser.ExprParentContext) ctx).expr());
        if (ctx instanceof SafeLangParser.ExprAddSubContext)
            return inferSafeType(((SafeLangParser.ExprAddSubContext) ctx).e1);
        if (ctx instanceof SafeLangParser.ExprMultDivContext) {
            SafeLangParser.ExprMultDivContext c = (SafeLangParser.ExprMultDivContext) ctx;
            String lt = inferSafeType(c.e1);
            String rt = inferSafeType(c.e2);
            boolean leftDim  = isDimensional(lt);
            boolean rightDim = isDimensional(rt);
            if (!leftDim && !rightDim) return null;
            if (!leftDim) return rt;
            if (!rightDim) return lt;
            return findDimProduct(lt, rt, c.op.getText().equals("*"));
        }
        return null;
    }

    private boolean isDimensional(String safeType) {
        return safeType != null && dimensions.containsKey(safeType);
    }

    private String getDimensionSuffix(String dimName) {
        if (dimSuffix.containsKey(dimName)) return dimSuffix.get(dimName);

        HashMap<String, Integer> vec = dimVectors.get(dimName);
        if (vec == null || vec.isEmpty()) return null;

        List<Map.Entry<String, Integer>> entries = new ArrayList<>(vec.entrySet());
        entries.sort((a, b) -> {
            int cmp = Integer.compare(b.getValue(), a.getValue());
            return cmp != 0 ? cmp : a.getKey().compareTo(b.getKey());
        });

        StringBuilder num = new StringBuilder();
        StringBuilder den = new StringBuilder();
        for (Map.Entry<String, Integer> e : entries) {
            String su = dimSuffix.get(e.getKey());
            if (su == null) continue;
            int p = e.getValue();
            if (p > 0) {
                if (num.length() > 0) num.append("*");
                num.append(su);
                if (p > 1) num.append("^").append(p);
            } else {
                if (den.length() > 0) den.append("*");
                den.append(su);
                if (p < -1) den.append("^").append(-p);
            }
        }
        String result = den.length() == 0 ? num.toString() : num + "/" + den;
        return result.isEmpty() ? null : result;
    }

    private String withSuffix(String strExpr, String safeType) {
        String suffix = isDimensional(safeType) ? getDimensionSuffix(safeType) : null;
        if (suffix != null && !suffix.isEmpty())
            return "(" + strExpr + " + \"" + suffix + "\")";
        return strExpr;
    }

    private String findDimProduct(String leftType, String rightType, boolean multiply) {
        HashMap<String, Integer> leftVec  = dimVectors.get(leftType);
        HashMap<String, Integer> rightVec = dimVectors.get(rightType);
        if (leftVec == null || rightVec == null) return null;

        HashMap<String, Integer> result = new HashMap<>(leftVec);
        for (Map.Entry<String, Integer> e : rightVec.entrySet())
            result.merge(e.getKey(), multiply ? e.getValue() : -e.getValue(), Integer::sum);
        result.entrySet().removeIf(entry -> entry.getValue() == 0);

        for (Map.Entry<String, HashMap<String, Integer>> dim : dimVectors.entrySet())
            if (dim.getValue().equals(result)) return dim.getKey();
        return null;
    }

    // ── Type helpers ─────────────────────────────────────────────────────

    private String toJavaType(String slType) {
        // Novo suporte para listas
        if (slType.startsWith("list[")) {
            String innerType = slType.substring(5, slType.length() - 1);
            return "java.util.ArrayList<" + toJavaType(innerType) + ">";
        }
        
        switch (slType) {
            case "integer": return "BigInteger";
            case "real":    return "Fraction";
            case "string":  return "String";
            case "boolean": return "boolean";
            default:
                if (slType.startsWith("integer[")) return "BigInteger";
                if (slType.startsWith("real["))    return "Fraction";
                String base = dimensions.get(slType);
                return base != null ? base : "Fraction";
        }
    }

    private String defaultVal(String javaType) {
        if (javaType.startsWith("java.util.ArrayList")) return "new java.util.ArrayList<>()";
        
        switch (javaType) {
            case "BigInteger": return "BigInteger.ZERO";
            case "Fraction":   return "Fraction.ZERO";
            case "String":     return "\"\"";
            case "boolean":    return "false";
            default:           return "Fraction.ZERO";
        }
    }

    private String inferJavaType(SafeLangParser.ExprContext ctx) {
        if (ctx instanceof SafeLangParser.ExprNewListContext) {
            SafeLangParser.ExprNewListContext c = (SafeLangParser.ExprNewListContext) ctx;
            return toJavaType("list[" + c.type().getText() + "]");
        }
        if (ctx instanceof SafeLangParser.ExprLengthContext) return "BigInteger";
        if (ctx instanceof SafeLangParser.ExprListAccessContext) {
            SafeLangParser.ExprListAccessContext c = (SafeLangParser.ExprListAccessContext) ctx;
            String listType = varTypes.get(c.ID().getText());
            if (listType != null && listType.startsWith("java.util.ArrayList<")) {
                return listType.substring(20, listType.length() - 1);
            }
            return "Object";
        }
        if (ctx instanceof SafeLangParser.ExprIntegerContext)     return "BigInteger";
        if (ctx instanceof SafeLangParser.ExprRealPointContext)   return "Fraction";
        if (ctx instanceof SafeLangParser.ExprRealFracContext)    return "Fraction";
        if (ctx instanceof SafeLangParser.ExprStringContext)      return "String";
        if (ctx instanceof SafeLangParser.ExprReadContext)        return "String";
        if (ctx instanceof SafeLangParser.ExprFormatContext)      return "String";
        if (ctx instanceof SafeLangParser.ExprCast2IntContext)    return "BigInteger";
        if (ctx instanceof SafeLangParser.ExprCast2RealContext)   return "Fraction";
        if (ctx instanceof SafeLangParser.ExprCast2StringContext) return "String";
        if (ctx instanceof SafeLangParser.ExprQuoRemContext)      return "BigInteger";

        if (ctx instanceof SafeLangParser.ExprBoolLiteralContext) return "boolean";
        if (ctx instanceof SafeLangParser.ExprCompareContext)     return "boolean";
        if (ctx instanceof SafeLangParser.ExprAndContext)         return "boolean";
        if (ctx instanceof SafeLangParser.ExprOrContext)          return "boolean";
        if (ctx instanceof SafeLangParser.ExprNotContext)         return "boolean";
        if (ctx instanceof SafeLangParser.ExprCastUserContext) {
            String typeName = ((SafeLangParser.ExprCastUserContext) ctx).ID().getText();
            return toJavaType(typeName);
        }
        if (ctx instanceof SafeLangParser.ExprIDContext) {
            String id = ((SafeLangParser.ExprIDContext) ctx).ID().getText();
            return varTypes.getOrDefault(id, "Fraction");
        }

        if (ctx instanceof SafeLangParser.ExprParentContext)
            return inferJavaType(((SafeLangParser.ExprParentContext) ctx).expr());

        if (ctx instanceof SafeLangParser.ExprAddSubContext) {
            SafeLangParser.ExprAddSubContext c = (SafeLangParser.ExprAddSubContext) ctx;
            String lt = inferJavaType(c.e1);
            String rt = inferJavaType(c.e2);
            if (lt.equals("String") || rt.equals("String")) return "String";
            if (lt.equals("Fraction") || rt.equals("Fraction")) return "Fraction";
            return "BigInteger";
        }

        if (ctx instanceof SafeLangParser.ExprMultDivContext) {
            SafeLangParser.ExprMultDivContext c = (SafeLangParser.ExprMultDivContext) ctx;
            if (c.op.getText().equals("/")) return "Fraction"; // always real
            String lt = inferJavaType(c.e1);
            String rt = inferJavaType(c.e2);
            if (lt.equals("BigInteger") && rt.equals("BigInteger")) return "BigInteger";
            return "Fraction";
        }

        return "Fraction"; // safe fallback
    }

    // ── Dimension vector arithmetic ───────────────────────────────────────

    private HashMap<String, Integer> evalDimExpr(SafeLangParser.DimExprContext ctx) {
        if (ctx instanceof SafeLangParser.DimRefContext) {
            String name = ((SafeLangParser.DimRefContext) ctx).ID().getText();
            HashMap<String, Integer> vec = dimVectors.get(name);
            if (vec != null) return new HashMap<>(vec);
            HashMap<String, Integer> v = new HashMap<>();
            v.put(name, 1);
            return v;
        } else if (ctx instanceof SafeLangParser.DimMulDivContext) {
            SafeLangParser.DimMulDivContext c = (SafeLangParser.DimMulDivContext) ctx;
            HashMap<String, Integer> left  = evalDimExpr(c.dimExpr(0));
            HashMap<String, Integer> right = evalDimExpr(c.dimExpr(1));
            boolean isMul = c.getChild(1).getText().equals("*");
            for (Map.Entry<String, Integer> e : right.entrySet())
                left.merge(e.getKey(), isMul ? e.getValue() : -e.getValue(), Integer::sum);
            left.entrySet().removeIf(entry -> entry.getValue() == 0);
            return left;
        } else if (ctx instanceof SafeLangParser.DimPowContext) {
            SafeLangParser.DimPowContext c = (SafeLangParser.DimPowContext) ctx;
            HashMap<String, Integer> base = evalDimExpr(c.dimExpr());
            int power = Integer.parseInt(c.INTEGER().getText());
            base.replaceAll((k, v) -> v * power);
            return base;
        } else if (ctx instanceof SafeLangParser.DimParenContext) {
            return evalDimExpr(((SafeLangParser.DimParenContext) ctx).dimExpr());
        }
        return new HashMap<>();
    }
}
