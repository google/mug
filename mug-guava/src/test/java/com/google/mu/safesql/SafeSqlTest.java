package com.google.mu.safesql;

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.safesql.SafeSql.template;
import static org.junit.Assert.assertThrows;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.collect.ImmutableList;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

@RunWith(TestParameterInjector.class)
public class SafeSqlTest {
  @BeforeClass  // Consistently set the system property across the test suite
  public static void setUpTrustedType() {
    System.setProperty(
        "com.google.mu.safesql.SafeQuery.trusted_sql_type",
        SafeQueryTest.TrustedSql.class.getName());
  }

  @Test
  public void emptyTemplate() {
    assertThat(template("").with()).isEqualTo(SafeSql.of(""));
  }

  @Test
  public void emptySql() {
    assertThat(SafeSql.EMPTY.toString()).isEmpty();
    assertThat(SafeSql.EMPTY.getParameters()).isEmpty();
  }

  @Test
  public void singleStringParameter() {
    SafeSql sql = SafeSql.of("select {str}", "foo");
    assertThat(sql.toString()).isEqualTo("select ?");
    assertThat(sql.getParameters()).containsExactly("foo");
  }

  @Test
  public void singleIntParameter() {
    SafeSql sql = SafeSql.of("select {i}", 123);
    assertThat(sql.toString()).isEqualTo("select ?");
    assertThat(sql.getParameters()).containsExactly(123);
  }

  @Test
  public void singleNullParameter() {
    SafeSql sql = SafeSql.of("select {i}", /* i */ (Integer) null);
    assertThat(sql.toString()).isEqualTo("select ?");
    assertThat(sql.getParameters()).containsExactly(null);
  }

  @Test
  public void twoParameters() {
    SafeSql sql =
        SafeSql.of("select {label} where id = {id}", /* label */ "foo", /* id */ 123);
    assertThat(sql.toString()).isEqualTo("select ? where id = ?");
    assertThat(sql.getParameters()).containsExactly("foo", 123).inOrder();
  }

  @Test
  public void parameterizeByTableName() {
    SafeSql sql =
        SafeSql.of("select * from {tbl} where id = {id}", /* tbl */ SafeSql.of("Users"), /* id */ 123);
    assertThat(sql.toString()).isEqualTo("select * from Users where id = ?");
    assertThat(sql.getParameters()).containsExactly(123);
  }

  @Test
  @SuppressWarnings("StringFormatArgsCheck")
  public void twoParametersWithSameName() {
    SafeSql sql =
        SafeSql.of("select * where id = {id} and partner_id = {id}", 123, 456);
    assertThat(sql.toString()).isEqualTo("select * where id = ? and partner_id = ?");
    assertThat(sql.getParameters()).containsExactly(123, 456).inOrder();
  }

  @Test
  public void twoParametersWithSameNameAndBothAreNulls() {
    SafeSql sql =
        SafeSql.of("select * where id = {id} and partner_id = {id}", /* id */ null, /* id */ null);
    assertThat(sql.toString()).isEqualTo("select * where id = ? and partner_id = ?");
    assertThat(sql.getParameters()).containsExactly(null, null).inOrder();
  }

  @Test
  public void paramNameWithQuestionMark_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> SafeSql.of("select * where id = {foo?}", 123));
    assertThat(thrown).hasMessageThat().contains("instead of '?'");
  }

  @Test
  public void sqlWithQuestionMark() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> SafeSql.of("select * where id = ?"));
    assertThat(thrown).hasMessageThat().contains("instead of '?'");
  }

  @Test
  public void singleStringParameterValueHasQuestionMark() {
    SafeSql sql = SafeSql.of("select {str}", "?");
    assertThat(sql.toString()).isEqualTo("select ?");
    assertThat(sql.getParameters()).containsExactly("?");
  }

  @Test
  public void subqueryHasQuestionMark_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> SafeSql.of("select * from {tbl}", /* tbl */ SafeSql.of("?")));
    assertThat(thrown).hasMessageThat().contains("instead of '?'");
  }


  @Test
  public void when_conditionalIsFalse_returnsEmpty() {
    assertThat(SafeSql.when(false, "WHERE id = {id}", 1)).isEqualTo(SafeSql.EMPTY);
  }

  @Test
  public void when_conditionalIsTrue_returnsQuery() {
    assertThat(SafeSql.when(true, "WHERE id = {id}", 1))
        .isEqualTo(SafeSql.of("WHERE id = {id}", 1));
  }

  @Test
  public void optionally_optionalArgIsEmpty_returnsEmpty() {
    assertThat(SafeSql.optionally("WHERE id = {id}", /* id */ Optional.empty()))
        .isEqualTo(SafeSql.EMPTY);
  }

  @Test
  public void optionally_optionalArgIsPresent_returnsQuery() {
    assertThat(SafeSql.optionally("WHERE id = {id}", /* id */ Optional.of(1)))
        .isEqualTo(SafeSql.of("WHERE id = {id}", 1));
  }


  @Test
  public void joiningByStringConstant() {
    assertThat(Stream.of(SafeSql.of("a"), SafeSql.of("b")).collect(SafeSql.joining(" AND ")))
        .isEqualTo(SafeSql.of("a AND b"));
  }

  @Test
  public void joining_ignoresEmpty() {
    assertThat(Stream.of(SafeSql.of("a"), SafeSql.EMPTY).collect(SafeSql.joining(" AND ")))
        .isEqualTo(SafeSql.of("a"));
  }

  @Test
  public void andCollector_empty() {
    ImmutableList<SafeSql> queries = ImmutableList.of();
    assertThat(queries.stream().collect(SafeSql.and())).isEqualTo(SafeSql.of("1 = 1"));
  }

  @Test
  public void andCollector_singleCondition() {
    ImmutableList<SafeSql> queries = ImmutableList.of(SafeSql.of("a = 1"));
    assertThat(queries.stream().collect(SafeSql.and())).isEqualTo(SafeSql.of("(a = 1)"));
  }

  @Test
  public void andCollector_twoConditions() {
    ImmutableList<SafeSql> queries =
        ImmutableList.of(SafeSql.of("a = 1"), SafeSql.of("b = 2 OR c = 3"));
    assertThat(queries.stream().collect(SafeSql.and()))
        .isEqualTo(SafeSql.of("(a = 1) AND (b = 2 OR c = 3)"));
  }

  @Test
  public void andCollector_threeConditions() {
    ImmutableList<SafeSql> queries =
        ImmutableList.of(
            SafeSql.of("a = 1"), SafeSql.of("b = 2 OR c = 3"), SafeSql.of("d = 4"));
    assertThat(queries.stream().collect(SafeSql.and()))
        .isEqualTo(SafeSql.of("(a = 1) AND (b = 2 OR c = 3) AND (d = 4)"));
  }

  @Test
  public void andCollector_threeConditionsWithParameters() {
    ImmutableList<SafeSql> queries =
        ImmutableList.of(
            SafeSql.of("a = {v1}", 1), SafeSql.of("b = {v2} OR c = {v3}", 2, 3), SafeSql.of("d = {v4}", 4));
    SafeSql sql = queries.stream().collect(SafeSql.and());
    assertThat(sql.toString()).isEqualTo("(a = ?) AND (b = ? OR c = ?) AND (d = ?)");
    assertThat(sql.getParameters()).containsExactly(1, 2, 3, 4).inOrder();
  }

  @Test
  public void andCollector_ignoresEmpty() {
    ImmutableList<SafeSql> queries =
        ImmutableList.of(SafeSql.EMPTY, SafeSql.of("b = 2 OR c = 3"), SafeSql.of("d = 4"));
    assertThat(queries.stream().collect(SafeSql.and()))
        .isEqualTo(SafeSql.of("(b = 2 OR c = 3) AND (d = 4)"));
  }

  @Test
  public void orCollector_empty() {
    ImmutableList<SafeSql> queries = ImmutableList.of();
    assertThat(queries.stream().collect(SafeSql.or())).isEqualTo(SafeSql.of("1 = 0"));
  }

  @Test
  public void orCollector_singleCondition() {
    ImmutableList<SafeSql> queries = ImmutableList.of(SafeSql.of("a = 1"));
    assertThat(queries.stream().collect(SafeSql.or())).isEqualTo(SafeSql.of("(a = 1)"));
  }

  @Test
  public void orCollector_twoConditions() {
    ImmutableList<SafeSql> queries =
        ImmutableList.of(SafeSql.of("a = 1"), SafeSql.of("b = 2 AND c = 3"));
    assertThat(queries.stream().collect(SafeSql.or()))
        .isEqualTo(SafeSql.of("(a = 1) OR (b = 2 AND c = 3)"));
  }

  @Test
  public void orCollector_threeConditions() {
    ImmutableList<SafeSql> queries =
        ImmutableList.of(
            SafeSql.of("a = 1"), SafeSql.of("b = 2 AND c = 3"), SafeSql.of("d = 4"));
    assertThat(queries.stream().collect(SafeSql.or()))
        .isEqualTo(SafeSql.of("(a = 1) OR (b = 2 AND c = 3) OR (d = 4)"));
  }

  @Test
  public void orCollector_threeConditionsWithParameters() {
    ImmutableList<SafeSql> queries =
        ImmutableList.of(
            SafeSql.of("a = {v1}", 1), SafeSql.of("b = {v2} AND c = {v3}", 2, 3), SafeSql.of("d = {v4}", 4));
    SafeSql sql = queries.stream().collect(SafeSql.or());
    assertThat(sql.toString()).isEqualTo("(a = ?) OR (b = ? AND c = ?) OR (d = ?)");
    assertThat(sql.getParameters()).containsExactly(1, 2, 3, 4).inOrder();
  }

  @Test
  public void orCollector_ignoresEmpty() {
    ImmutableList<SafeSql> queries =
        ImmutableList.of(SafeSql.EMPTY, SafeSql.of("b = 2 AND c = 3"), SafeSql.of("d = 4"));
    assertThat(queries.stream().collect(SafeSql.or()))
        .isEqualTo(SafeSql.of("(b = 2 AND c = 3) OR (d = 4)"));
  }

  @Test
  public void namesInAnonymousSubqueriesAreIndependent() {
    SafeSql sql =
        Stream.of(1, 2, 3).map(id -> SafeSql.of("id = {id}", id)).collect(SafeSql.or());
    assertThat(sql.toString()).isEqualTo("(id = ?) OR (id = ?) OR (id = ?)");
    assertThat(sql.getParameters()).containsExactly(1, 2, 3);
  }

  @Test
  public void namesInSubqueryAndParentQueryDontConflict() {
    SafeSql sql = SafeSql.of(
        "select * from ({tbl}) where id = {id}",
        SafeSql.of("select * from tbl where id = {id}", 1), /* id */ 2);
    assertThat(sql.toString()).isEqualTo("select * from (select * from tbl where id = ?) where id = ?");
    assertThat(sql.getParameters()).containsExactly(1, 2).inOrder();
  }

  @Test
  public void namesInSubqueriesDontConflict() {
    SafeSql sql = SafeSql.of(
        "select * from ({tbl1}), ({tbl2}) where id = {id}",
        /* tbl1 */ SafeSql.of("select * from tbl where id = {id}", 1),
        /* tbl2 */ SafeSql.of("select * from tbl where id = {id}", 2),
        /* id */ 3);
    assertThat(sql.toString()).isEqualTo("select * from (select * from tbl where id = ?), (select * from tbl where id = ?) where id = ?");
    assertThat(sql.getParameters()).containsExactly(1, 2, 3).inOrder();
  }

  @Test
  public void cannotUseSafeQueryAsSubquery() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> SafeSql.of("select * from {tbl}", SafeQuery.of("tbl")));
    assertThat(thrown).hasMessageThat().contains("SafeQuery");
  }
}
