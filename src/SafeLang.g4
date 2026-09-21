grammar SafeLang;

program: statList EOF;


statList: (stat? ';')* stat?;

use: USE STRING;

stat: assignment
    | write
    | typeDecl
    | unitDecl
    | use
    | tryStat
    | RETRY
    | forStat
    | appendStat
    | ifStat
    | whileStat
    | untilStat
    | prefixStat
    | ASSERT expr
    | FAIL
    ;

assignment: ID COLON type                      #Assign
          | ID assign_op expr (COLON type)?    #AssignDecl
          ;

type: INTEGER_T ('[' INTEGER ']')?
    | REAL_T ('[' INTEGER ']')?
    | STRING_T
    | BOOL_T
    | LIST '[' type ']'
    | ID
    ;
    
assign_op:  ASSIGN
          | ASSIGN_TRY
          ;
          
write:    WRITE expr (',' expr)*            #WriteExpr
        | WRITELN (expr(',' expr)*)?        #WriteNewline
        ;
        
typeDecl: TYPE ID unitSpec? (ASSIGN dimExpr)? COLON type ;

unitSpec: '[' ID (',' ID)? ']' ;

unitDecl: UNIT ID '[' ID (',' ID)? ']' ASSIGN expr ;

expr: e1=expr op=('*'|'/') e2=expr                      #ExprMultDiv
    | e1=expr op=(QUO|REM) e2=expr                      #ExprQuoRem
    | e1=expr op=('+'|'-') e2=expr                      #ExprAddSub
    | e1=expr op=('='|'<>'|'<'|'>'|'<='|'>=') e2=expr   #ExprCompare
    | e1=expr AND e2=expr                               #ExprAnd
    | e1=expr OR e2=expr                                #ExprOr
    | NOT expr                                          #ExprNot
    | INTEGER_T '(' expr ')'                            #ExprCast2Int
    | STRING_T '(' expr ')'                             #ExprCast2String
    | REAL_T '(' expr ')'                               #ExprCast2Real
    | READ STRING                                       #ExprRead
    | FORMAT '(' expr ',' expr ')'                      #ExprFormat
    | FORMAT '(' expr ',' expr ',' expr ')'             #ExprFormat3
    | NEW LIST '[' type ']'                             #ExprNewList
    | LENGTH '(' expr ')'                               #ExprLength
    | ID '[' expr ']'                                   #ExprListAccess
    | ID '(' expr ')'                                   #ExprCastUser
    | '(' expr ')'                                      #ExprParent
    | BOOL_LITERAL                                      #ExprBoolLiteral
    | STRING                                            #ExprString
    | ID                                                #ExprID
    | REALPOINT                                         #ExprRealPoint
    | REALFRAC                                          #ExprRealFrac
    | INTEGER                                           #ExprInteger
    ;
    
dimExpr
    : dimExpr '^' INTEGER         #DimPow
    | dimExpr ('*'|'/') dimExpr   #DimMulDiv
    | '(' dimExpr ')'             #DimParen
    | ID                          #DimRef
    ;

tryStat:    TRY statList (RESCUE statList)? END ;

forStat:    FOR ID ASSIGN expr TO expr DO statList END ;

appendStat: expr APPEND expr ;

ifStat:     IF expr THEN statList (ELSE statList)? END ;

whileStat:  WHILE expr DO statList END ;

untilStat:  UNTIL expr DO statList END ;

prefixStat: PREFIX ID ASSIGN (DIM | INTEGER) COLON type ;

IF:           'if';
THEN:         'then';
ELSE:         'else';
WHILE:        'while';
UNTIL:        'until';
AND:          'and';
OR:           'or';
NOT:          'not';
ASSERT:       'assert';
FAIL:         'fail';
BOOL_LITERAL: 'true' | 'false';
TRY:          'try';
RESCUE:       'rescue';
END:          'end';
RETRY:        'retry';
FOR:          'for';
TO:           'to';
DO:           'do';
NEW:          'new';
LIST:         'list';
LENGTH:       'length';
APPEND:       '>>';
ASSIGN_TRY:   ':=?';
ASSIGN:       ':=';
COLON:        ':';
QUO:          '//';
REM:          '\\\\';
BOOL_T:       'boolean';
INTEGER_T:    'integer';
STRING_T:     'string';
REAL_T:       'real';
READ:         'read';
WRITE:        'write';
WRITELN:      'writeln';
TYPE:         'type';
FORMAT:       'format';
USE:          'use';
UNIT:         'unit';
PREFIX:       'prefix';
DIM:          '1e' '-'? [0-9]+ ;


ID:           [a-zA-Z_][a-zA-Z0-9_]*;
STRING:       '"' .*? '"';
INTEGER:      [0-9]+;
REALPOINT:    [0-9]+ '.' [0-9]+ ([eE] [+-]? [0-9]+)?;
REALFRAC:     [0-9]+ '/' [0-9]+;
MLCOMMENT:    '##' .*? '##' -> skip;
SLCOMMENT:    '#' ~[\n\r]*  -> skip;
NEWLINE:      [\n\r]        -> skip;
WHITESPACE:   [ \t]         -> skip;
