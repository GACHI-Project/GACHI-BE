package com.gachi.be.domain.school.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gachi.be.domain.school.client.NeisSchoolClassClient;
import com.gachi.be.domain.school.dto.response.NeisSchoolClassItem;
import com.gachi.be.global.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SchoolClassServiceTest {

  private final NeisSchoolClassClient neisSchoolClassClient = mock(NeisSchoolClassClient.class);
  private final SchoolClassService service = new SchoolClassService(neisSchoolClassClient);

  @Test
  void searchReturnsDistinctSortedClassesAndCachesSameSchoolGrade() {
    when(neisSchoolClassClient.search("B10", "7051173", "2026", 4))
        .thenReturn(
            List.of(
                new NeisSchoolClassItem("2026", 4, "10", "초등학교"),
                new NeisSchoolClassItem("2026", 4, "2", "초등학교"),
                new NeisSchoolClassItem("2026", 4, "2", "초등학교"),
                new NeisSchoolClassItem("2026", 4, "1", "초등학교")));

    var first = service.search(" B10 ", " 7051173 ", "2026", 4);
    var second = service.search("B10", "7051173", "2026", 4);

    assertThat(first).isSameAs(second);
    assertThat(first.officeCode()).isEqualTo("B10");
    assertThat(first.schoolCode()).isEqualTo("7051173");
    assertThat(first.academicYear()).isEqualTo("2026");
    assertThat(first.grade()).isEqualTo(4);
    assertThat(first.totalCount()).isEqualTo(3);
    assertThat(first.classes())
        .extracting("grade", "className")
        .containsExactly(tuple(4, "1"), tuple(4, "2"), tuple(4, "10"));
    verify(neisSchoolClassClient, times(1)).search("B10", "7051173", "2026", 4);
  }

  @Test
  void searchAllowsGradeToBeOmitted() {
    when(neisSchoolClassClient.search("B10", "7051173", "2026", null))
        .thenReturn(
            List.of(
                new NeisSchoolClassItem("2026", 2, "1", "초등학교"),
                new NeisSchoolClassItem("2026", 1, "3", "초등학교")));

    var response = service.search("B10", "7051173", "2026", null);

    assertThat(response.grade()).isNull();
    assertThat(response.classes())
        .extracting("grade", "className")
        .containsExactly(tuple(1, "3"), tuple(2, "1"));
  }

  @Test
  void searchFillsSameCacheKeyAtomicallyForConcurrentRequests() throws Exception {
    AtomicInteger callCount = new AtomicInteger();
    CountDownLatch clientEntered = new CountDownLatch(1);
    CountDownLatch releaseClient = new CountDownLatch(1);
    when(neisSchoolClassClient.search("B10", "7051173", "2026", 4))
        .thenAnswer(
            invocation -> {
              callCount.incrementAndGet();
              clientEntered.countDown();
              releaseClient.await(3, TimeUnit.SECONDS);
              return List.of(new NeisSchoolClassItem("2026", 4, "1", "초등학교"));
            });

    var executor = Executors.newFixedThreadPool(2);
    try {
      List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
      futures.add(executor.submit(() -> service.search("B10", "7051173", "2026", 4)));
      assertThat(clientEntered.await(1, TimeUnit.SECONDS)).isTrue();
      futures.add(executor.submit(() -> service.search("B10", "7051173", "2026", 4)));
      releaseClient.countDown();

      for (java.util.concurrent.Future<?> future : futures) {
        future.get(3, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }

    assertThat(callCount).hasValue(1);
    verify(neisSchoolClassClient, times(1)).search("B10", "7051173", "2026", 4);
  }

  @Test
  void searchRejectsInvalidGrade() {
    assertThatThrownBy(() -> service.search("B10", "7051173", "2026", 7))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void searchRejectsInvalidAcademicYear() {
    assertThatThrownBy(() -> service.search("B10", "7051173", "26", 4))
        .isInstanceOf(BusinessException.class);
  }
}
