package com.gachi.be.domain.notification.service;

import com.gachi.be.domain.notification.entity.Notification;
import com.gachi.be.domain.notification.entity.PushDeviceToken;
import java.util.Map;

/** 외부 푸시 provider별 발송 구현체가 맞춰야 하는 공통 계약. */
public interface PushNotificationClient {

  /**
   * 푸시 제공자 식별자를 반환한다.
   *
   * @return delivery log provider 컬럼에 저장할 식별자. 예: {@code EXPO}, {@code FCM}
   */
  String providerName();

  /**
   * 단일 디바이스 토큰으로 푸시 알림을 발송한다.
   *
   * <p>구현체는 provider 응답을 {@link PushSendResult}로 변환해야 하며, 호출자가 delivery log를 남길 수 있도록 복구 가능한 발송 실패는
   * 예외 대신 실패 결과로 반환하는 것을 기본 계약으로 한다.
   *
   * @param notification 발송할 알림 엔티티
   * @param pushDeviceToken 대상 디바이스 토큰
   * @param payload 앱에서 알림 클릭 후 라우팅 등에 사용할 추가 데이터
   * @param title 사용자 현재 언어로 렌더링된 푸시 제목
   * @param body 사용자 현재 언어로 렌더링된 푸시 본문
   * @return 성공/실패, provider 메시지 ID, 토큰 무효화 여부를 포함한 발송 결과
   */
  PushSendResult send(
      Notification notification,
      PushDeviceToken pushDeviceToken,
      Map<String, Object> payload,
      String title,
      String body);
}
