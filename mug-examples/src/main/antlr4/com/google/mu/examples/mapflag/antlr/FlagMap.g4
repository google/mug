grammar FlagMap;

map    : '{' (entry (',' entry)* ','?)? '}' EOF ;
entry  : key '=' value ;
// Purely numeric keys like "123" lex as INT; splitting INT and DECIMAL rejects "1.5" as a key.
key    : KEY | INT ;
value  : scalar                                  # single
       | '[' (scalar (',' scalar)* ','?)? ']'    # list
       ;
scalar : INT | DECIMAL | STRING ;

INT     : '-'? ('0' | [1-9] [0-9]*) ;
DECIMAL : INT '.' [0-9]+ ;
KEY     : [a-zA-Z0-9_-]+ ;
STRING  : '"' ( '\\' . | ~["\\] )* '"' ;
WS      : [\p{White_Space}]+ -> skip ;
