package com.gachi.be.domain.calendar.service.impl;

import static java.util.Map.entry;

import com.gachi.be.domain.newsletter.pipeline.PapagoTranslateClient;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.domain.user.repository.UserRepository;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
class NeisCalendarTranslationService {
  private static final String DEFAULT_LANGUAGE = "KO";
  private static final Map<String, MealNameTranslations> MEAL_NAME_TRANSLATIONS =
      Map.of(
          "1", new MealNameTranslations("Breakfast", "早餐", "Bữa sáng"),
          "2", new MealNameTranslations("Lunch", "午餐", "Bữa trưa"),
          "3", new MealNameTranslations("Dinner", "晚餐", "Bữa tối"));
  private static final Map<String, LabelTranslations> NUTRITION_LABEL_TRANSLATIONS =
      Map.of(
          "탄수화물", new LabelTranslations("Carbohydrates", "碳水化合物", "Carbohydrate"),
          "단백질", new LabelTranslations("Protein", "蛋白质", "Chất đạm"),
          "지방", new LabelTranslations("Fat", "脂肪", "Chất béo"),
          "비타민A", new LabelTranslations("Vitamin A", "维生素A", "Vitamin A"),
          "티아민", new LabelTranslations("Thiamine", "硫胺素", "Thiamine"),
          "리보플라빈", new LabelTranslations("Riboflavin", "核黄素", "Riboflavin"),
          "비타민C", new LabelTranslations("Vitamin C", "维生素C", "Vitamin C"),
          "칼슘", new LabelTranslations("Calcium", "钙", "Canxi"),
          "철", new LabelTranslations("Iron", "铁", "Sắt"));
  private static final Map<String, FixedTranslations> TIMETABLE_CONTENT_TRANSLATIONS =
      Map.ofEntries(
          entry("국어", new FixedTranslations("Korean", "国语", "Tiếng Hàn")),
          entry("수학", new FixedTranslations("Mathematics", "数学", "Toán học")),
          entry("사회", new FixedTranslations("Social Studies", "社会", "Xã hội")),
          entry("과학", new FixedTranslations("Science", "科学", "Khoa học")),
          entry("영어", new FixedTranslations("English", "英语", "Tiếng Anh")),
          entry("체육", new FixedTranslations("Physical Education", "体育", "Thể dục")),
          entry("음악", new FixedTranslations("Music", "音乐", "Âm nhạc")),
          entry("미술", new FixedTranslations("Art", "美术", "Mỹ thuật")),
          entry("도덕", new FixedTranslations("Moral Education", "道德", "Đạo đức")),
          entry("실과", new FixedTranslations("Practical Arts", "实科", "Kỹ thuật thực hành")),
          entry(
              "창체",
              new FixedTranslations(
                  "Creative Experiential Activities", "创意体验活动", "Hoạt động trải nghiệm sáng tạo")),
          entry("동아리", new FixedTranslations("Club Activities", "社团活动", "Hoạt động câu lạc bộ")),
          entry("진로", new FixedTranslations("Career Activities", "职业活动", "Hoạt động hướng nghiệp")),
          entry("자율", new FixedTranslations("Autonomous Activities", "自主活动", "Hoạt động tự quản")),
          entry(
              "자치",
              new FixedTranslations("Self-Governance Activities", "自治活动", "Hoạt động tự trị")),
          entry(
              "자율/자치활동",
              new FixedTranslations(
                  "Autonomous Activities", "自主/自治活动", "Hoạt động tự quản/tự trị")),
          entry(
              "봉사",
              new FixedTranslations("Volunteer Activities", "志愿服务活动", "Hoạt động tình nguyện")),
          entry("안전", new FixedTranslations("Safety Education", "安全教育", "Giáo dục an toàn")),
          entry("정보", new FixedTranslations("Information", "信息", "Tin học")),
          entry("보건", new FixedTranslations("Health", "保健", "Sức khỏe")));
  private static final Map<String, FixedTranslations> SCHEDULE_TEXT_TRANSLATIONS =
      Map.ofEntries(
          entry(
              "3·1절",
              new FixedTranslations(
                  "Independence Movement Day", "三一节", "Ngày Phong trào Độc lập 1/3")),
          entry(
              "3.1절",
              new FixedTranslations(
                  "Independence Movement Day", "三一节", "Ngày Phong trào Độc lập 1/3")),
          entry(
              "삼일절",
              new FixedTranslations(
                  "Independence Movement Day", "三一节", "Ngày Phong trào Độc lập 1/3")),
          entry("대체공휴일", new FixedTranslations("Substitute Holiday", "补休假日", "Ngày nghỉ bù")),
          entry("대체휴일", new FixedTranslations("Substitute Holiday", "补休假日", "Ngày nghỉ bù")),
          entry("공휴일", new FixedTranslations("Public Holiday", "公休日", "Ngày nghỉ lễ")),
          entry("어린이날", new FixedTranslations("Children's Day", "儿童节", "Ngày Thiếu nhi")),
          entry("석가탄신일", new FixedTranslations("Buddha's Birthday", "佛诞节", "Lễ Phật Đản")),
          entry("부처님오신날", new FixedTranslations("Buddha's Birthday", "佛诞节", "Lễ Phật Đản")),
          entry("현충일", new FixedTranslations("Memorial Day", "显忠日", "Ngày Tưởng niệm")),
          entry(
              "광복절",
              new FixedTranslations("National Liberation Day", "光复节", "Ngày Giải phóng Quốc gia")),
          entry("개천절", new FixedTranslations("National Foundation Day", "开天节", "Ngày Lập quốc")),
          entry("한글날", new FixedTranslations("Hangeul Day", "韩文节", "Ngày Hangeul")),
          entry("성탄절", new FixedTranslations("Christmas Day", "圣诞节", "Lễ Giáng sinh")),
          entry("크리스마스", new FixedTranslations("Christmas Day", "圣诞节", "Lễ Giáng sinh")),
          entry("신정", new FixedTranslations("New Year's Day", "元旦", "Tết Dương lịch")),
          entry("설날", new FixedTranslations("Lunar New Year", "春节", "Tết Nguyên đán")),
          entry("추석", new FixedTranslations("Chuseok", "中秋节", "Tết Trung thu")),
          entry("제헌절", new FixedTranslations("Constitution Day", "制宪节", "Ngày Hiến pháp")),
          entry("시업식", new FixedTranslations("Opening Ceremony", "开学典礼", "Lễ khai giảng")),
          entry("입학식", new FixedTranslations("Entrance Ceremony", "入学典礼", "Lễ nhập học")),
          entry("졸업식", new FixedTranslations("Graduation Ceremony", "毕业典礼", "Lễ tốt nghiệp")),
          entry("종업식", new FixedTranslations("Closing Ceremony", "结业典礼", "Lễ bế giảng")),
          entry("방학식", new FixedTranslations("Vacation Ceremony", "放假典礼", "Lễ nghỉ học")),
          entry("여름방학식", new FixedTranslations("Summer Vacation Ceremony", "暑假典礼", "Lễ nghỉ hè")),
          entry("겨울방학식", new FixedTranslations("Winter Vacation Ceremony", "寒假典礼", "Lễ nghỉ đông")),
          entry("봄방학식", new FixedTranslations("Spring Vacation Ceremony", "春假典礼", "Lễ nghỉ xuân")),
          entry("여름방학", new FixedTranslations("Summer Vacation", "暑假", "Kỳ nghỉ hè")),
          entry("겨울방학", new FixedTranslations("Winter Vacation", "寒假", "Kỳ nghỉ đông")),
          entry("봄방학", new FixedTranslations("Spring Vacation", "春假", "Kỳ nghỉ xuân")),
          entry(
              "개학식", new FixedTranslations("Opening Ceremony of School", "返校典礼", "Lễ tựu trường")),
          entry(
              "개학일", new FixedTranslations("Opening Ceremony of School", "返校日", "Ngày tựu trường")),
          entry(
              "개교기념일",
              new FixedTranslations("School Anniversary", "建校纪念日", "Ngày thành lập trường")),
          entry(
              "학교자율휴업일",
              new FixedTranslations(
                  "School Discretionary Holiday", "学校自主休业日", "Ngày nghỉ tự chọn của trường")),
          entry(
              "재량휴업일",
              new FixedTranslations(
                  "School Discretionary Holiday", "学校自主休业日", "Ngày nghỉ tự chọn của trường")),
          entry("지방선거", new FixedTranslations("Local Elections", "地方选举", "Bầu cử địa phương")),
          entry(
              "전국동시지방선거",
              new FixedTranslations(
                  "National Local Elections", "全国同时地方选举", "Bầu cử địa phương toàn quốc")),
          entry("선거일", new FixedTranslations("Election Day", "选举日", "Ngày bầu cử")));

  private final UserRepository userRepository;
  private final PapagoTranslateClient papagoTranslateClient;

  /**
   * NEIS 캘린더 응답 번역에 사용할 사용자 언어와 요청 단위 번역 캐시를 만든다.
   *
   * <p>같은 급식명/행사명/수업명이 반복될 수 있어, 한 요청 안에서는 동일 문구를 한 번만 번역한다.
   */
  TranslationContext contextFor(Long userId) {
    String language =
        userRepository
            .findById(userId)
            .map(User::getLanguageCode)
            .filter(StringUtils::hasText)
            .map(value -> value.trim().toUpperCase())
            .orElse(DEFAULT_LANGUAGE);
    return new TranslationContext(language, new HashMap<>());
  }

  String translate(TranslationContext context, String text) {
    if (context == null || !context.shouldTranslate() || !StringUtils.hasText(text)) {
      return text;
    }
    return context
        .cache()
        .computeIfAbsent(text, value -> translateOrOriginal(value, context.language()));
  }

  String translateMealName(TranslationContext context, String mealCode, String mealName) {
    if (context == null || !context.shouldTranslate() || !StringUtils.hasText(mealName)) {
      return mealName;
    }
    MealNameTranslations translations = MEAL_NAME_TRANSLATIONS.get(mealCode);
    if (translations == null) {
      translations = MEAL_NAME_TRANSLATIONS.get(mealCodeByName(mealName));
    }
    return translations == null
        ? translate(context, mealName)
        : translations.get(context.language());
  }

  String translateNutritionInfo(TranslationContext context, String nutritionInfo) {
    if (context == null || !context.shouldTranslate() || !StringUtils.hasText(nutritionInfo)) {
      return nutritionInfo;
    }
    String[] lines = nutritionInfo.split("\\R", -1);
    StringBuilder translated = new StringBuilder();
    for (int i = 0; i < lines.length; i++) {
      if (i > 0) {
        translated.append('\n');
      }
      translated.append(translateNutritionLine(context, lines[i]));
    }
    return translated.toString();
  }

  String translateTimetableContent(TranslationContext context, String content) {
    return translateFixedOrPapago(context, content, TIMETABLE_CONTENT_TRANSLATIONS);
  }

  String translateScheduleText(TranslationContext context, String text) {
    return translateFixedOrPapago(context, text, SCHEDULE_TEXT_TRANSLATIONS);
  }

  private String translateOrOriginal(String text, String language) {
    try {
      String translated = papagoTranslateClient.translate(text, language);
      return StringUtils.hasText(translated) ? translated : text;
    } catch (RuntimeException e) {
      // 외부 번역 실패가 급식/시간표/학사일정 조회 실패로 전파되지 않도록 원문을 유지한다.
      log.warn("[NEIS Calendar] 파파고 번역 실패로 원문을 사용합니다. language={}", language, e);
      return text;
    }
  }

  private String translateNutritionLine(TranslationContext context, String line) {
    int separatorIndex = line.indexOf(':');
    if (separatorIndex < 0) {
      return translate(context, line);
    }
    String label = line.substring(0, separatorIndex).trim();
    String value = line.substring(separatorIndex);
    String normalizedLabel = label.replaceAll("\\s+", "").replaceAll("\\([^)]*\\)", "");
    LabelTranslations translations = NUTRITION_LABEL_TRANSLATIONS.get(normalizedLabel);
    if (translations == null) {
      return translate(context, line);
    }
    String unit = extractUnit(label);
    return translations.get(context.language()) + unit + value;
  }

  private String translateFixedOrPapago(
      TranslationContext context, String text, Map<String, FixedTranslations> translationsByText) {
    if (context == null || !context.shouldTranslate() || !StringUtils.hasText(text)) {
      return text;
    }
    FixedTranslations translations = translationsByText.get(normalizeKey(text));
    return translations == null ? translate(context, text) : translations.get(context.language());
  }

  private String extractUnit(String label) {
    int start = label.indexOf('(');
    int end = label.lastIndexOf(')');
    if (start < 0 || end <= start) {
      return "";
    }
    return " " + label.substring(start, end + 1);
  }

  private String mealCodeByName(String mealName) {
    String normalized = normalizeKey(mealName);
    return switch (normalized) {
      case "조식" -> "1";
      case "중식" -> "2";
      case "석식" -> "3";
      default -> null;
    };
  }

  private String normalizeKey(String value) {
    return value == null ? "" : value.replaceAll("\\s+", "").trim();
  }

  record TranslationContext(String language, Map<String, String> cache) {
    boolean shouldTranslate() {
      return StringUtils.hasText(language) && !DEFAULT_LANGUAGE.equalsIgnoreCase(language);
    }
  }

  private record MealNameTranslations(String us, String zh, String vi) {
    String get(String language) {
      return switch (language) {
        case "ZH" -> zh;
        case "VI" -> vi;
        default -> us;
      };
    }
  }

  private record LabelTranslations(String us, String zh, String vi) {
    String get(String language) {
      return switch (language) {
        case "ZH" -> zh;
        case "VI" -> vi;
        default -> us;
      };
    }
  }

  private record FixedTranslations(String us, String zh, String vi) {
    String get(String language) {
      return switch (language) {
        case "ZH" -> zh;
        case "VI" -> vi;
        default -> us;
      };
    }
  }
}
