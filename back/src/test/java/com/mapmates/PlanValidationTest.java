package com.mapmates;

import static org.junit.jupiter.api.Assertions.*;

import com.mapmates.catalog.dto.PlaceWrite;
import com.mapmates.common.exception.ApiException;
import com.mapmates.common.security.PasswordHasher;
import com.mapmates.travel.dto.PlanWrite;
import com.mapmates.travel.service.TravelServiceImpl;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PlanValidationTest {
  private PlanWrite plan(int day, int duration, double lat) {
    return new PlanWrite(
        "여행",
        LocalDate.of(2026, 11, 8),
        LocalDate.of(2026, 11, 8).plusDays(duration),
        4,
        0,
        "서울",
        "문화",
        "소개",
        List.of(
            new PlanWrite.Stop(
                day, "10:00", "", new PlaceWrite(125452, "장소", lat, 127.0, "", "서울", 12))));
  }

  @Test
  void validatesDayAgainstActualPeriod() {
    assertDoesNotThrow(() -> TravelServiceImpl.validate(plan(1, 0, 37)));
    assertThrows(ApiException.class, () -> TravelServiceImpl.validate(plan(2, 0, 37)));
  }

  @Test
  void capsPeriodAtFourteenDays() {
    assertDoesNotThrow(() -> TravelServiceImpl.validate(plan(14, 13, 37)));
    assertThrows(ApiException.class, () -> TravelServiceImpl.validate(plan(15, 14, 37)));
  }

  @Test
  void rejectsNonFiniteCoordinates() {
    assertThrows(ApiException.class, () -> TravelServiceImpl.validate(plan(1, 0, Double.NaN)));
    assertThrows(ApiException.class, () -> TravelServiceImpl.validate(plan(1, 0, 0)));
  }

  @Test
  void saltedPasswordHashesVerify() {
    String a = PasswordHasher.hash("travel1234"), b = PasswordHasher.hash("travel1234");
    assertNotEquals(a, b);
    assertTrue(PasswordHasher.matches("travel1234", a));
    assertFalse(PasswordHasher.matches("wrong", a));
  }
}
