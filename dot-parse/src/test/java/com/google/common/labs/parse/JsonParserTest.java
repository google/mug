package com.google.common.labs.parse;

import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.consecutive;
import static com.google.common.labs.parse.Parser.digits;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.labs.parse.Parser.string;
import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.util.CharPredicate.isNot;
import static java.util.stream.Collectors.joining;

import org.junit.Ignore;
import org.junit.Test;

public class JsonParserTest {
  private static Parser<String> QUOTED =
      consecutive(isNot('"'), "quoted").immediatelyBetween("\"", "\"").map(v -> '"' + v + '"');
  private static Parser<String> NULL =
      string("null").notImmediatelyFollowedBy(Character::isJavaIdentifierPart, "identifier char");

  private static Parser<String> json() {
    var value = new Parser.Rule<String>();
    var struct = new Parser.Rule<String>();
    value.definedAs(anyOf(QUOTED, NULL, digits(), listOf(value), struct));
    return struct.definedAs(sequence(QUOTED.followedBy(":"), value, (k, v) -> k + ":" + v)
        .zeroOrMoreDelimitedBy(",", joining(","))
        .between("{", "}")
        .map(s -> '{' + s + '}'));
  }

  private static Parser<String> listOf(Parser<String> item) {
    return item.zeroOrMoreDelimitedBy(",", joining(",")).between("[", "]").map(s -> "[" + s + "]");
  }

  @Ignore("manual")
  @Test public void testJsonParse() {
    assertThat(listOf(json()).parseSkipping(Character::isWhitespace, DATA)).isEqualTo("");
  }

  private static final String DATA =
      """
      [
        {
          "change_timestamp": "2043-10-01 23:00:01.000000 UTC",
          "project_id": "project-id1",
          "project_number": "12345678",
          "reservation_id": "reservation-id1",
          "reservation_name": "reservation-name1",
          "slot_capacity": "1000",
          "use_parent_reservation": "true",
          "action": "CREATE",
          "autoscale": null,
          "user_email": "google",
          "region": "US",
          "edition": "ENTERPRISE",
          "concurrency": "0",
          "change_type": null,
          "autoscale_version_timestamp": null,
          "on_demand_autoscale_changes": []
        },
        {
          "change_timestamp": "2043-10-01 23:10:02.000000 UTC",
          "project_id": "project-id1",
          "project_number": "12345678",
          "reservation_id": "reservation-id1",
          "reservation_name": "reservation-name1",
          "slot_capacity": "2000",
          "use_parent_reservation": "true",
          "action": "UPDATE",
          "autoscale": {
            "current_slots": 0,
            "max_slots": "300",
            "current_used_slots": null
          },
          "user_email": "google",
          "region": "US",
          "edition": "ENTERPRISE",
          "concurrency": "0",
          "change_type": null,
          "autoscale_version_timestamp": null,
          "on_demand_autoscale_changes": []
        },
        {
          "change_timestamp": "2043-10-15 11:00:00.000000 UTC",
          "project_id": "project-id1",
          "project_number": "12345678",
          "reservation_id": null,
          "reservation_name": null,
          "slot_capacity": null,
          "use_parent_reservation": null,
          "action": "UPDATE",
          "autoscale": null,
          "user_email": "google",
          "region": "US",
          "edition": "ENTERPRISE",
          "concurrency": "0",
          "change_type": "ON_DEMAND_SLOT_USAGE",
          "autoscale_version_timestamp": "2044-01-01 10:00:00.000000 UTC",
          "on_demand_autoscale_changes": [
            {
              "change_timestamp": "2043-10-15 11:00:01.000000 UTC",
              "reservation_id": "reservation-id1",
              "reservation_name": "reservation-name1",
              "autoscale": {
                "current_slots": "100",
                "max_slots": null,
                "current_used_slots": null
              }
            },
            {
              "change_timestamp": "2043-10-15 11:00:03.000000 UTC",
              "reservation_id": "reservation-id1",
              "reservation_name": "reservation-name1",
              "autoscale": {
                "current_slots": "200",
                "max_slots": null,
                "current_used_slots": null
              }
            }
          ]
        },
        {
          "change_timestamp": "2043-10-15 11:00:00.000000 UTC",
          "project_id": "project-id1",
          "project_number": "12345678",
          "reservation_id": null,
          "reservation_name": null,
          "slot_capacity": null,
          "use_parent_reservation": null,
          "action": "UPDATE",
          "autoscale": null,
          "user_email": "google",
          "region": "US",
          "edition": "ENTERPRISE",
          "concurrency": "0",
          "change_type": "ON_DEMAND_SLOT_USAGE",
          "autoscale_version_timestamp": "2044-01-01 11:01:00.000000 UTC",
          "on_demand_autoscale_changes": [
            {
              "change_timestamp": "2043-10-15 11:00:02.000000 UTC",
              "reservation_id": "reservation-id1",
              "reservation_name": "reservation-name1",
              "autoscale": {
                "current_slots": "300",
                "max_slots": null,
                "current_used_slots": null
              }
            },
            {
              "change_timestamp": "2043-10-15 11:00:04.000000 UTC",
              "reservation_id": "reservation-id1",
              "reservation_name": "reservation-name1",
              "autoscale": {
                "current_slots": "250",
                "max_slots": null,
                "current_used_slots": null
              }
            }
          ]
        },
        {
          "change_timestamp": "2043-10-15 11:01:00.000000 UTC",
          "project_id": "project-id1",
          "project_number": "12345678",
          "reservation_id": null,
          "reservation_name": null,
          "slot_capacity": null,
          "use_parent_reservation": null,
          "action": "UPDATE",
          "autoscale": null,
          "user_email": "google",
          "region": "US",
          "edition": "ENTERPRISE",
          "concurrency": "0",
          "change_type": "ON_DEMAND_SLOT_USAGE",
          "autoscale_version_timestamp": "2044-01-01 11:02:00.000000 UTC",
          "on_demand_autoscale_changes": [
            {
              "change_timestamp": "2043-10-15 11:01:12.000000 UTC",
              "reservation_id": "reservation-id1",
              "reservation_name": "reservation-name1",
              "autoscale": {
                "current_slots": "100",
                "max_slots": null,
                "current_used_slots": null
              }
            },
            {
              "change_timestamp": "2043-10-15 11:01:30.000000 UTC",
              "reservation_id": "reservation-id1",
              "reservation_name": "reservation-name1",
              "autoscale": {
                "current_slots": "50",
                "max_slots": null,
                "current_used_slots": null
              }
            }
          ]
        },
        {
          "change_timestamp": "2043-10-01 23:00:15.000000 UTC",
          "project_id": "project-id1",
          "project_number": "12345678",
          "reservation_id": "reservation-id1",
          "reservation_name": "reservation-name1",
          "slot_capacity": "1000",
          "use_parent_reservation": "true",
          "action": "SNAPSHOT",
          "autoscale": null,
          "user_email": "google",
          "region": "US",
          "edition": "ENTERPRISE",
          "concurrency": "0",
          "change_type": null,
          "autoscale_version_timestamp": null,
          "on_demand_autoscale_changes": []
        }
      ]
      """;
}
