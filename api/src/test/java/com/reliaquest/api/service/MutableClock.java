package com.reliaquest.api.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

class MutableClock extends Clock {
  private Instant instant;
  private final ZoneId zone;

  MutableClock(Instant start, ZoneId zone) {
    this.instant = start;
    this.zone = zone;
  }

  void advance(Duration d) {
    instant = instant.plus(d);
  }

  @Override
  public ZoneId getZone() {
    return zone;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return new MutableClock(instant, zone);
  }

  @Override
  public Instant instant() {
    return instant;
  }
}
