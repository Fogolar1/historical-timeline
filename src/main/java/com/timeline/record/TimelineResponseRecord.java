package com.timeline.record;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public record TimelineResponseRecord(
  List<PersonRecord> people,
  Integer earliestYear,
  Integer latestYear
) {

  public static TimelineResponseRecord from(List<PersonRecord> people) {
      List<Integer> years = people.stream()
        .flatMap(p -> Stream.of(p.birthYear(), p.deathYear()))
        .filter(Objects::nonNull).toList();

      return new TimelineResponseRecord(people,
        years.stream().min(Integer::compareTo).orElse(null),
        years.stream().max(Integer::compareTo).orElse(null));
  }
}
