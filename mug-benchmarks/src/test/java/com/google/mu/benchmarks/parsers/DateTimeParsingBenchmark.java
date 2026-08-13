/*****************************************************************************
 * ------------------------------------------------------------------------- *
 * Licensed under the Apache License, Version 2.0 (the "License");           *
 * you may not use this file except in compliance with the License.          *
 * You may obtain a copy of the License at                                   *
 *                                                                           *
 * http://www.apache.org/licenses/LICENSE-2.0                                *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing, software       *
 * distributed under the License is distributed on an "AS IS" BASIS,         *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  *
 * See the License for the specific language governing permissions and       *
 * limitations under the License.                                            *
 *****************************************************************************/
package com.google.mu.benchmarks.parsers;

import cn.hutool.core.date.DateUtil;
import com.google.mu.time.DateTimeFormats;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class DateTimeParsingBenchmark {

  private static final String DATETIME_INPUT = "2026-08-12 10:30:00";
  private static final String ZONE_INPUT = "2020-01-01T00:00:01-07:00[America/New_York]";
  private static final String INSTANT_INPUT = "2020-01-01T00:00:01-07:00";

  private static final DateTimeFormatter PRE_ALLOCATED_DATETIME_FORMATTER =
      DateTimeFormats.formatOf(DATETIME_INPUT);
  private static final DateTimeFormatter PRE_ALLOCATED_ZONE_FORMATTER =
      DateTimeFormats.formatOf(ZONE_INPUT);
  private static final DateTimeFormatter PRE_ALLOCATED_INSTANT_FORMATTER =
      DateTimeFormats.formatOf(INSTANT_INPUT);

  @Benchmark
  public Object hutool_parseDateTime_dynamic() {
    return DateUtil.parse(DATETIME_INPUT);
  }

  @Benchmark
  public Object mug_parseDateTime_dynamic() {
    return LocalDateTime.parse(DATETIME_INPUT, DateTimeFormats.formatOf(DATETIME_INPUT));
  }

  @Benchmark
  public Object mug_parseDateTime_preAllocated() {
    return LocalDateTime.parse(DATETIME_INPUT, PRE_ALLOCATED_DATETIME_FORMATTER);
  }

  @Benchmark
  public Object hutool_parseDateTimeWithZone_dynamic() {
    return DateUtil.parse(ZONE_INPUT);
  }

  @Benchmark
  public Object mug_parseDateTimeWithZone_preAllocated() {
    return ZonedDateTime.parse(ZONE_INPUT, PRE_ALLOCATED_ZONE_FORMATTER);
  }

  @Benchmark
  public Object hutool_parseInstant_dynamic() {
    return DateUtil.parse(INSTANT_INPUT);
  }

  @Benchmark
  public Object mug_parseInstant_preAllocated() {
    return PRE_ALLOCATED_INSTANT_FORMATTER.parse(INSTANT_INPUT, Instant::from);
  }
}
