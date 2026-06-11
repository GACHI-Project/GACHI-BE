package com.gachi.be.global.config.external;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NeisPropertiesTest {

  @Test
  void apiSpecificKeysOverrideLegacyCommonKey() {
    NeisProperties properties = new NeisProperties();
    properties.setApiKey("legacy-key");
    properties.setSchoolApiKey("school-key");
    properties.setScheduleApiKey("schedule-key");

    assertThat(properties.getSchoolApiKey()).isEqualTo("school-key");
    assertThat(properties.getScheduleApiKey()).isEqualTo("schedule-key");
  }

  @Test
  void legacyCommonKeyIsUsedAsFallback() {
    NeisProperties properties = new NeisProperties();
    properties.setApiKey("legacy-key");

    assertThat(properties.getSchoolApiKey()).isEqualTo("legacy-key");
    assertThat(properties.getScheduleApiKey()).isEqualTo("legacy-key");
  }

  @Test
  void blankApiSpecificKeysFallBackToLegacyCommonKey() {
    NeisProperties properties = new NeisProperties();
    properties.setApiKey("legacy-key");
    properties.setSchoolApiKey("");
    properties.setScheduleApiKey("   ");

    assertThat(properties.getSchoolApiKey()).isEqualTo("legacy-key");
    assertThat(properties.getScheduleApiKey()).isEqualTo("legacy-key");
  }

  @Test
  void mealTimetableAndClassInfoKeysDoNotFallBackToLegacyCommonKey() {
    NeisProperties properties = new NeisProperties();
    properties.setApiKey("legacy-key");
    properties.setMealApiKey(" meal-key ");
    properties.setTimetableApiKey(" timetable-key ");
    properties.setClassInfoApiKey(" class-key ");

    assertThat(properties.getMealApiKey()).isEqualTo("meal-key");
    assertThat(properties.getTimetableApiKey()).isEqualTo("timetable-key");
    assertThat(properties.getClassInfoApiKey()).isEqualTo("class-key");

    properties.setMealApiKey(null);
    properties.setTimetableApiKey(" ");
    properties.setClassInfoApiKey("");

    assertThat(properties.getMealApiKey()).isNull();
    assertThat(properties.getTimetableApiKey()).isNull();
    assertThat(properties.getClassInfoApiKey()).isNull();
  }
}
