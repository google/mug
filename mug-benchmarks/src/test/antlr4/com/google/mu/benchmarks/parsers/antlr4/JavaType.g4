grammar JavaType;

// Parser Rules

entry : javaType EOF ;

javaType : packagePrefix? typeSegment (dot typeSegment)* arrayDimension* ;

packagePrefix : (packageSegment dot)+ ;

packageSegment : LOWER_IDENTIFIER ;

typeSegment : annotation* typeName typeArguments? ;

typeName : PRIMITIVE_TYPE | UPPER_IDENTIFIER ;

typeArguments : lt javaType (comma javaType)* gt ;

annotation : at annotationName annotationParams? ;

annotationName : (packageSegment dot)* typeName ;

annotationParams : lp (namedParams | singleParam) rp ;

namedParams : namedParam (comma namedParam)* ;

namedParam : (IDENTIFIER | LOWER_IDENTIFIER | UPPER_IDENTIFIER) eq annotationValue ;

singleParam : annotationValue ;

annotationValue
    : STRING_LITERAL
    | javaType dot CLASS
    | NUMBER_LITERAL
    | annotation
    | lbrace annotationValue (comma annotationValue)* rbrace
    ;

// Terminals / Lexer Rules

PRIMITIVE_TYPE : 'int' | 'double' | 'float' | 'long' | 'short' | 'byte' | 'char' | 'boolean' | 'void' ;
CLASS : 'class' ;

LOWER_IDENTIFIER : [a-z] [a-zA-Z0-9]* ;
UPPER_IDENTIFIER : [A-Z] [a-zA-Z0-9]* ;
IDENTIFIER : [a-zA-Z_] [a-zA-Z0-9_]* ;

STRING_LITERAL : '"' ( ESCAPED_CHAR | ~["\\] )* '"' ;
fragment ESCAPED_CHAR : '\\' ( [ntrfb"'\\] | 'u' [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] ) ;

NUMBER_LITERAL : '-'? [0-9]+ ('.' [0-9]+)? ;

arrayDimension : '[' ']' ;

at : '@' ;
dot : '.' ;
lt : '<' ;
gt : '>' ;
comma : ',' ;
eq : '=' ;
lp : '(' ;
rp : ')' ;
lbrace : '{' ;
rbrace : '}' ;

WS : [ \t\r\n]+ -> skip ;
