package com.gachi.be.domain.todo.repository;

import com.gachi.be.domain.todo.entity.TodoItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TodoItemRepository extends JpaRepository<TodoItem, Long> {

  /** 특정 가정통신문의 해야 할 일 항목 전체 조회 (생성 순서대로) */
  List<TodoItem> findByNewsletterIdOrderByIdAsc(Long newsletterId);

  /** 특정 가정통신문의 해야 할 일 항목 전체 삭제 (재분석 시 사용) */
  void deleteByNewsletterId(Long newsletterId);
}
