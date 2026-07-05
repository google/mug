grammar Showdown;

// 1. IP Address Rule
ip: INT '.' INT '.' INT '.' INT EOF;

// 2. Quoted String Rule
quotedString: QUOTED_STRING EOF;

// 3. Case-Sensitive Keywords Rule
keyword: SELECT | INSERT | UPDATE | DELETE | CREATE | DROP | ALTER | WHERE | GROUP | ORDER | HAVING | LIMIT;
keywords: keyword (',' keyword)* EOF;

// 4. Case-Insensitive Keywords Rule
keywordIgnoreCase: select_ic | insert_ic | update_ic | delete_ic | create_ic | drop_ic | alter_ic | where_ic | group_ic | order_ic | having_ic | limit_ic;
keywordsIgnoreCase: keywordIgnoreCase (',' keywordIgnoreCase)* EOF;

// 5. Calculator Rule
calculator: calcExpr EOF;

calcExpr: calcExpr ('*'|'/') calcExpr
        | calcExpr ('+'|'-') calcExpr
        | '(' calcExpr ')'
        | '-'? INT
        ;

nestedCommentRoot: nestedComment EOF;
nestedComment: OPEN_COMMENT ( TEXT | nestedComment )* CLOSE_COMMENT;

usPhone: US_PHONE_TOKEN EOF;
usPhoneList: US_PHONE_TOKEN* EOF;

select_ic: SELECT | SELECT_IC;
insert_ic: INSERT | INSERT_IC;
update_ic: UPDATE | UPDATE_IC;
delete_ic: DELETE | DELETE_IC;
create_ic: CREATE | CREATE_IC;
drop_ic: DROP | DROP_IC;
alter_ic: ALTER | ALTER_IC;
where_ic: WHERE | WHERE_IC;
group_ic: GROUP | GROUP_IC;
order_ic: ORDER | ORDER_IC;
having_ic: HAVING | HAVING_IC;
limit_ic: LIMIT | LIMIT_IC;

// Lexer Rules
QUOTED_STRING: '"' (ESC | ~[\\"])* '"';

fragment ESC: '\\' .;
INT: [0-9]+;

SELECT: 'select';
INSERT: 'insert';
UPDATE: 'update';
DELETE: 'delete';
CREATE: 'create';
DROP: 'drop';
ALTER: 'alter';
WHERE: 'where';
GROUP: 'group';
ORDER: 'order';
HAVING: 'having';
LIMIT: 'limit';

SELECT_IC: [sS][eE][lL][eE][cC][tT];
INSERT_IC: [iI][nN][sS][eE][rR][tT];
UPDATE_IC: [uU][pP][dD][aA][tT][eE];
DELETE_IC: [dD][eE][lL][eE][tT][eE];
CREATE_IC: [cC][rR][eE][aA][tT][eE];
DROP_IC: [dD][rR][oO][pP];
ALTER_IC: [aA][lL][tT][eE][rR];
WHERE_IC: [wW][hH][eE][rR][eE];
GROUP_IC: [gG][rR][oO][uU][pP];
ORDER_IC: [oO][rR][dD][eE][rR];
HAVING_IC: [hH][aA][vV][iI][nN][gG];
LIMIT_IC: [lL][iI][mM][iI][tT];

US_PHONE_TOKEN: '(' [0-9] [0-9] [0-9] ')' [0-9] [0-9] [0-9] '-' [0-9] [0-9] [0-9] [0-9];

WS: [ \t\r\n]+ -> skip;

OPEN_COMMENT: '/*';
CLOSE_COMMENT: '*/';
TEXT: [a-zA-Z ]+;

