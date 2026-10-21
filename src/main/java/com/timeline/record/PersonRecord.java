package com.timeline.record;

public record PersonRecord(
  String title,
  String description,
  String extract,
  String imageUrl,
  String wikipediaUrl,
  String wikidataId,
  Integer birthYear,
  Integer deathYear,
  boolean birthApproximate,
  boolean deathApproximate
) {
}
