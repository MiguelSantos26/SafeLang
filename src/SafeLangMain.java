import java.io.IOException;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

public class SafeLangMain {
   public static void main(String[] args) {
      try {
         CharStream input = CharStreams.fromStream(System.in);
         SafeLangLexer lexer = new SafeLangLexer(input);
         CommonTokenStream tokens = new CommonTokenStream(lexer);
         SafeLangParser parser = new SafeLangParser(tokens);
         ParseTree tree = parser.program();
         if (parser.getNumberOfSyntaxErrors() == 0) {
            if (args.length > 0) {
               Translator translator = new Translator(args[0]);
               System.out.println(translator.visit(tree));
            } else {
               Translator translator = new Translator();
               System.out.println(translator.visit(tree));
            }
         }
      }
      catch(IOException e) {
         e.printStackTrace();
         System.exit(1);
      }
      catch(RecognitionException e) {
         e.printStackTrace();
         System.exit(1);
      }
   }
}
