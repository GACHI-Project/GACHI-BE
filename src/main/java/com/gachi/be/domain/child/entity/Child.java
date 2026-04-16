package com.gachi.be.domain.child.entity;

import com.gachi.be.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 로그인 사용자가 소유하는 자녀 정보를 저장한다. */
@Getter
@Entity
@Table(name = "children")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Child {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, length = 50)
  private String name;

  @Column(name = "school_name", nullable = false, length = 120)
  private String schoolName;

  @Column(name = "school_code", length = 64)
  private String schoolCode;

  @Column(nullable = false)
  private Integer grade;

  @Column(name = "color_code", nullable = false, length = 7)
  private String colorCode;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Builder
  public Child(
      User user,
      String name,
      String schoolName,
      String schoolCode,
      Integer grade,
      String colorCode) {
    this.user = user;
    this.name = name;
    this.schoolName = schoolName;
    this.schoolCode = schoolCode;
    this.grade = grade;
    this.colorCode = colorCode;
  }

  @PrePersist
  protected void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    if (createdAt == null) {
      createdAt = now;
    }
    updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
