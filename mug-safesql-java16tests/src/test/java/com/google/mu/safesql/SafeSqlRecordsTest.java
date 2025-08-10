package com.google.mu.safesql;

import static com.google.common.truth.Truth.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import javax.sql.DataSource;

import org.dbunit.DataSourceBasedDBTestCase;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.operation.DatabaseOperation;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.hash.Hashing;

@RunWith(JUnit4.class)
public class SafeSqlRecordsTest extends DataSourceBasedDBTestCase {
  @Rule public final TestName testName = new TestName();

  @After public void cleanDb() throws Exception {
    SafeSql.of("TRUNCATE TABLE ITEMS").update(connection());
  }

  @Override
  protected DataSource getDataSource() {
      JdbcDataSource dataSource = new JdbcDataSource();
      dataSource.setURL(
        "jdbc:h2:mem:default;MODE=LEGACY;DB_CLOSE_DELAY=-1;init=runscript from 'classpath:/com/google/mu/safesql/schema.sql'");
      dataSource.setUser("sa");
      dataSource.setPassword("sa");
      return dataSource;
  }

  @Override
  protected IDataSet getDataSet() throws Exception {
    return new FlatXmlDataSetBuilder().build(new ByteArrayInputStream(new byte[0]));
  }

  @Override
  protected DatabaseOperation getSetUpOperation() {
      return DatabaseOperation.REFRESH;
  }

  @Override
  protected DatabaseOperation getTearDownOperation() {
      return DatabaseOperation.TRUNCATE_TABLE;
  }


  @Test public void query_withRecordResultType() throws Exception {
    ZonedDateTime barTime = ZonedDateTime.of(2024, 11, 1, 10, 20, 30, 0, ZoneId.of("UTC"));
    assertThat(
            SafeSql.of("insert into ITEMS(id, title, time) VALUES({id}, {title}, {time})", testId(), "bar", barTime)
                .update(connection()))
        .isEqualTo(1);
    assertThat(
            SafeSql.of("select id, time, title from ITEMS where id = {id}", testId())
                .query(connection(), Item.class))
        .containsExactly(new Item(testId(), "bar", barTime.toInstant()));
  }

  record Item(int id, String title, Instant time) {}

  private int testId() {
    return Hashing.goodFastHash(32).hashString(testName.getMethodName(), StandardCharsets.UTF_8).asInt();
  }

  private Connection connection() throws Exception {
    return getConnection().getConnection();
  }
}