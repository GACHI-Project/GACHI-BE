package com.gachi.be.domain.school.service;

import com.gachi.be.domain.school.client.NeisSchoolClient;
import com.gachi.be.domain.school.dto.response.SchoolSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SchoolSearchService {
  private final NeisSchoolClient neisSchoolClient;

  public SchoolSearchResponse search(String keyword, int size) {
    return neisSchoolClient.searchByName(keyword, size);
  }
}
