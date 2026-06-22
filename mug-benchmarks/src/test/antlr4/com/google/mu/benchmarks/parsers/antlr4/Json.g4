grammar Json;

// Parser Rules

entry : jsonValue EOF ;

jsonValue
    : jsonNull
    | jsonBoolean
    | jsonNumber
    | jsonString
    | jsonArray
    | jsonObject
    ;

jsonNull : 'null' ;
jsonBoolean : 'true' | 'false' ;
jsonNumber : JSON_NUMBER ;
jsonString : JSON_STRING ;

jsonArray : '[' ( jsonValue ( ',' jsonValue )* )? ']' ;

jsonObject : '{' ( member ( ',' member )* )? '}' ;

member : jsonString ':' jsonValue ;

// Lexer Rules

JSON_NUMBER : '-'? ( '0' | [1-9] [0-9]* ) ( '.' [0-9]+ )? ( [eE] [+-]? [0-9]+ )? ;

JSON_STRING : '"' ( ESCAPED_CHAR | ~["\\] )* '"' ;

fragment ESCAPED_CHAR : '\\' ( ["\\/bfnrt] | 'u' [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] ) ;

WS : [ \t\r\n]+ -> skip ;
