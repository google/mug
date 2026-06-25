package com.google.mu.benchmarks.parsers;

import static java.util.Map.entry;

import java.util.List;
import java.util.Map;

public final class BenchmarkInputs {
  public static final String IP = "192.168.1.1";
  public static final String STRING_SIMPLE = "\"hello world!\"";
  public static final String STRING_ESCAPED = "\"hello \\\"world\\\"!\"";

  public static final List<String> KEYWORDS =
      List.of(
          "select", "insert", "update", "delete", "create", "drop", "alter", "where", "group",
          "order", "having", "limit");

  public static final String KEYWORDS_LIST_CS =
      "select,insert,update,delete,create,drop,alter,where,group,order,having,limit,"
          + "insert,update,delete,create,drop,alter,where,group,order,having,limit,select,"
          + "update,delete,create,drop,alter,where,group,order,having,limit,select,insert,"
          + "delete,create,drop,alter,where,group,order,having,limit,select,insert,update,"
          + "create,drop,alter,where,group,order,having,limit,select,insert,update,delete,"
          + "drop,alter,where,group,order,having,limit,select,insert,update,delete,create,"
          + "alter,where,group,order,having,limit,select,insert,update,delete,create,drop,"
          + "where,group,order,having,limit,select,insert,update,delete,create,drop,alter,"
          + "group,order,having,limit,select,insert,update,delete,create,drop,alter,where,"
          + "order,having,limit,select,insert,update,delete,create,drop,alter,where,group";

  public static final String KEYWORDS_LIST_INVALID = KEYWORDS_LIST_CS + ",selecto";

  public static final String KEYWORDS_LIST_CI =
      "Select,INSERT,uPdate,deLeTe,CrEaTe,dRoP,aLtEr,WhErE,gRoUp,OrDeR,hAvInG,lImIt,"
          + "Insert,UPDATE,dElete,crEaTe,dRoP,aLtEr,WhErE,gRoUp,OrDeR,hAvInG,lImIt,sElEcT,"
          + "Update,DELETE,cReAtE,dRoP,aLtEr,WhErE,gRoUp,OrDeR,hAvInG,lImIt,sElEcT,iNsErT,"
          + "Delete,CREATE,dRoP,aLtEr,WhErE,gRoUp,OrDeR,hAvInG,lImIt,sElEcT,iNsErT,uPdAtE,"
          + "Create,DROP,aLtEr,WhErE,gRoUp,OrDeR,hAvInG,lImIt,sElEcT,iNsErT,uPdAtE,dElEtE,"
          + "Drop,ALTER,wHeRe,gRoUp,OrDeR,hAvInG,lImIt,sElEcT,iNsErT,uPdAtE,dElEtE,cReAtE,"
          + "Alter,WHERE,gRoUp,OrDeR,hAvInG,lImIt,sElEcT,iNsErT,uPdAtE,dElEtE,cReAtE,dRoP,"
          + "Where,GROUP,oRdEr,hAvInG,lImIt,sElEcT,iNsErT,uPdAtE,dElEtE,cReAtE,dRoP,aLtEr,"
          + "Group,ORDER,hAvInG,lImIt,sElEcT,iNsErT,uPdAtE,dElEtE,cReAtE,dRoP,aLtEr,wHeRe,"
          + "Order,hAvInG,lImIt,sElEcT,iNsErT,uPdAtE,dElEtE,cReAtE,dRoP,aLtEr,wHeRe,gRoUp";

  public static final String KEYWORDS_LIST_INVALID_CI = KEYWORDS_LIST_CI + ",selecto";

  public static final String CALCULATOR =
      " ( 1000+2 * 3000 - 4000 / (500+600) ) * -700 - 8000 / 9000";
  public static final int CALCULATOR_EXPECTED = -4897900;

  public static final String NESTED_COMMENT = "/* comment /* nested */ */";
  public static final String NESTED_COMMENT_EXPECTED_INNER = " comment /* nested */ ";
  public static final String NESTED_COMMENT_INVALID = "/* comment /* nested */";

  public enum Keyword {
    SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, ALTER, WHERE, GROUP, ORDER, HAVING, LIMIT
  }

  public static final Map<String, Keyword> KEYWORD_MAP =
      Map.ofEntries(
          entry("select", Keyword.SELECT),
          entry("insert", Keyword.INSERT),
          entry("update", Keyword.UPDATE),
          entry("delete", Keyword.DELETE),
          entry("create", Keyword.CREATE),
          entry("drop", Keyword.DROP),
          entry("alter", Keyword.ALTER),
          entry("where", Keyword.WHERE),
          entry("group", Keyword.GROUP),
          entry("order", Keyword.ORDER),
          entry("having", Keyword.HAVING),
          entry("limit", Keyword.LIMIT));

  public static String unescape(String s) {
    if (s.length() < 2 || s.charAt(0) != '"' || s.charAt(s.length() - 1) != '"') {
      return s;
    }
    String inner = s.substring(1, s.length() - 1);
    if (inner.indexOf('\\') == -1) {
      return inner;
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < inner.length(); i++) {
      char c = inner.charAt(i);
      if (c == '\\' && i + 1 < inner.length() && inner.charAt(i + 1) == 'u') {
        int uIndex = i + 1;
        while (uIndex < inner.length() && inner.charAt(uIndex) == 'u') {
          uIndex++;
        }
        if (uIndex + 4 <= inner.length()) {
          String hex = inner.substring(uIndex, uIndex + 4);
          sb.append((char) Integer.parseInt(hex, 16));
          i = uIndex + 3;
        } else {
          sb.append(c);
        }
      } else {
        sb.append(c);
      }
    }
    return sb.toString().translateEscapes();
  }

  private BenchmarkInputs() {}
}
