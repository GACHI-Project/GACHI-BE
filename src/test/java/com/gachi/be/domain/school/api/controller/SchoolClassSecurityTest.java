package com.gachi.be.domain.school.api.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gachi.be.domain.auth.service.JwtTokenProvider;
import com.gachi.be.domain.school.dto.response.SchoolClassItem;
import com.gachi.be.domain.school.dto.response.SchoolClassResponse;
import com.gachi.be.domain.school.service.SchoolClassService;
import com.gachi.be.domain.school.service.SchoolSearchService;
import com.gachi.be.domain.user.repository.UserRepository;
import com.gachi.be.global.config.SecurityConfig;
import com.gachi.be.global.security.JwtAuthenticationEntryPoint;
import com.gachi.be.global.security.JwtAuthenticationFilter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SchoolController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class SchoolClassSecurityTest {
  @Autowired private MockMvc mockMvc;

  @MockitoBean private SchoolSearchService schoolSearchService;
  @MockitoBean private SchoolClassService schoolClassService;
  @MockitoBean private JwtTokenProvider jwtTokenProvider;
  @MockitoBean private UserRepository userRepository;

  @Test
  void schoolClassesCanBeReadWithoutAccessToken() throws Exception {
    when(schoolClassService.search("B10", "7010057", null, 1))
        .thenReturn(
            new SchoolClassResponse(
                "B10", "7010057", "2026", 1, 1, List.of(new SchoolClassItem("2026", 1, "1"))));

    mockMvc
        .perform(
            get("/api/v1/schools/classes")
                .param("officeCode", "B10")
                .param("schoolCode", "7010057")
                .param("grade", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.classes[0].className").value("1"));

    verify(schoolClassService).search("B10", "7010057", null, 1);
  }

  @Test
  void otherProtectedEndpointsStillRequireAccessToken() throws Exception {
    mockMvc.perform(get("/api/v1/calendars/school-meals")).andExpect(status().isUnauthorized());
  }
}
