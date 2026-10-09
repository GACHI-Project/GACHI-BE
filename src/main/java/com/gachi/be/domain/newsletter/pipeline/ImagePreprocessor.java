package com.gachi.be.domain.newsletter.pipeline;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 가정통신문 이미지 전처리 1. EXIF 회전 보정: 2. PDF → 이미지 변환: */
@Slf4j
@Component
public class ImagePreprocessor {

  /** 업로드된 파일을 OCR에 적합한 형태로 전처리 */
  public byte[] preprocessImage(byte[] fileBytes) throws IOException {
    log.debug("[ImagePreprocessor] EXIF 회전 보정 시작. size={}bytes", fileBytes.length);
    byte[] result = correctExifRotation(fileBytes);
    log.debug("[ImagePreprocessor] 전처리 완료. resultSize={}bytes", result.length);
    return result;
  }

  /** 화면 표시/OCR/AI 첨부 겸용 JPEG 품질. 텍스트 인식에 영향이 거의 없는 고화질 값으로 고정. */
  private static final float DISPLAY_JPEG_QUALITY = 0.95f;

  /**
   * 이미지 페이지 전처리 (페이지 단위 파이프라인 전용).
   *
   * <p>EXIF 회전이 필요한 이미지만 실제로 회전시킨 고화질 JPEG를 만들어 반환하고, 회전이 필요 없으면 jpegBytes=null을 반환한다. (원본을 그대로 쓰면
   * 되므로 S3에 한 벌 더 저장하지 않기 위함) 어느 경우든 최종 이미지의 가로/세로 픽셀 크기를 함께 반환해 오버레이 좌표를 0~1 비율로 바꿀 때 사용한다.
   *
   * <p>기존 preprocessImage()는 결과를 PNG 임시 파일로 OCR 후 삭제했지만, 이어서 진행(PAUSED → 재개) 시점에도 같은 이미지를
   * OCR/AI/화면에서 써야 하므로 결과를 보관 가능한 JPEG 한 벌로 통일한다.
   */
  public PreprocessedImage preprocessForDisplay(byte[] fileBytes) throws IOException {
    int orientation = readExifOrientation(fileBytes);
    BufferedImage original = ImageIO.read(new ByteArrayInputStream(fileBytes));

    if (original == null) {
      throw new IOException("이미지를 읽을 수 없습니다. 지원하지 않는 형식일 수 있습니다.");
    }

    int degrees =
        switch (orientation) {
          case 3 -> 180;
          case 6 -> 90;
          case 8 -> 270;
          default -> 0;
        };

    if (degrees == 0) {
      log.debug(
          "[ImagePreprocessor] 회전 보정 불필요. 원본을 그대로 사용합니다. orientation={}, size={}x{}",
          orientation,
          original.getWidth(),
          original.getHeight());
      return new PreprocessedImage(null, original.getWidth(), original.getHeight());
    }

    BufferedImage rotated = rotate(original, degrees);
    byte[] jpegBytes = toJpegByteArray(rotated);
    log.debug(
        "[ImagePreprocessor] 회전 보정 JPEG 생성. orientation={}, degrees={}, size={}x{}, bytes={}",
        orientation,
        degrees,
        rotated.getWidth(),
        rotated.getHeight(),
        jpegBytes.length);
    return new PreprocessedImage(jpegBytes, rotated.getWidth(), rotated.getHeight());
  }

  /**
   * BufferedImage를 고화질 JPEG 바이트 배열로 변환. JPEG는 알파 채널을 지원하지 않으므로 흰 배경의 RGB 이미지로 옮긴 뒤 저장한다. (회전 시
   * TYPE_INT_ARGB로 만들어진 이미지도 안전하게 저장하기 위함)
   */
  private byte[] toJpegByteArray(BufferedImage image) throws IOException {
    BufferedImage rgbImage =
        new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D g2d = rgbImage.createGraphics();
    g2d.setColor(Color.WHITE);
    g2d.fillRect(0, 0, image.getWidth(), image.getHeight());
    g2d.drawImage(image, 0, 0, null);
    g2d.dispose();

    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
    if (!writers.hasNext()) {
      throw new IOException("JPEG ImageWriter를 찾을 수 없습니다.");
    }
    ImageWriter writer = writers.next();
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
      writer.setOutput(ios);
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(DISPLAY_JPEG_QUALITY);
      writer.write(null, new IIOImage(rgbImage, null, null), param);
      ios.flush();
      return baos.toByteArray();
    } finally {
      writer.dispose();
    }
  }

  /**
   * 페이지 이미지 전처리 결과.
   *
   * @param jpegBytes 회전 보정한 JPEG. 회전이 필요 없으면 null (원본 파일을 그대로 사용)
   * @param width 최종 이미지 가로 픽셀
   * @param height 최종 이미지 세로 픽셀
   */
  public record PreprocessedImage(byte[] jpegBytes, int width, int height) {
    public boolean rotated() {
      return jpegBytes != null;
    }
  }

  /**
   * EXIF 메타데이터의 회전 정보를 읽어 이미지를 실제로 회전시킴 EXIF Orientation 값: - 1: 정상 (회전 없음) - 3: 180도 회전 - 6: 시계 방향
   * 90도 회전 (핸드폰 세로 촬영 후 가로로 찍힌 경우) - 8: 반시계 방향 90도 회전 EXIF 읽기에 실패하더라도 원본 이미지를 그대로 반환하여 OCR 파이프라인
   * 자체가 중단되지 않도록 처리
   */
  private byte[] correctExifRotation(byte[] imageBytes) throws IOException {
    int orientation = readExifOrientation(imageBytes);
    BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));

    if (original == null) {
      throw new IOException("이미지를 읽을 수 없습니다. 지원하지 않는 형식일 수 있습니다.");
    }

    // orientation=1 이면 정상 상태이므로 회전 없이 바로 반환
    if (orientation == 1) {
      log.debug("[ImagePreprocessor] EXIF 회전 없음 (orientation=1)");
      return toByteArray(original);
    }

    // EXIF orientation 값에 따라 회전 각도 결정
    int degrees =
        switch (orientation) {
          case 3 -> 180;
          case 6 -> 90;
          case 8 -> 270;
          default -> 0;
        };

    if (degrees == 0) {
      log.debug("[ImagePreprocessor] 처리하지 않는 orientation={}. 원본 그대로 반환.", orientation);
      return toByteArray(original);
    }

    log.debug("[ImagePreprocessor] EXIF 회전 보정. orientation={}, degrees={}", orientation, degrees);
    BufferedImage rotated = rotate(original, degrees);
    return toByteArray(rotated);
  }

  /**
   * EXIF 메타데이터에서 Orientation 값을 읽음 실패 시 기본값 1(정상)을 반환하여 파이프라인이 계속 진행되도록 함 PNG 파일은 대부분 EXIF 정보가 없으므로
   * 예외가 발생하는 것이 정상.
   */
  private int readExifOrientation(byte[] imageBytes) {
    try {
      Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(imageBytes));
      ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);

      if (directory != null && directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
        return directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
      }
    } catch (ImageProcessingException | IOException e) {
      // EXIF 없는 이미지(PNG 등)에서는 정상적으로 발생할 수 있음. WARN이 아닌 DEBUG로 처리.
      log.debug("[ImagePreprocessor] EXIF 읽기 실패 (정상 케이스). {}", e.getMessage());
    } catch (MetadataException e) {
      log.warn("[ImagePreprocessor] EXIF 메타데이터 파싱 실패. 기본값(1)으로 진행합니다. {}", e.getMessage());
    }
    return 1; // 기본값: 정상 방향
  }

  /**
   * BufferedImage를 지정한 각도(degrees)만큼 회전시킴 90도/270도 회전 시 가로/세로가 바뀌므로 새 이미지의 width/height를 반전시켜야 함.
   */
  private BufferedImage rotate(BufferedImage original, int degrees) {
    boolean swap = (degrees == 90 || degrees == 270);
    int newWidth = swap ? original.getHeight() : original.getWidth();
    int newHeight = swap ? original.getWidth() : original.getHeight();

    int imageType =
        (original.getType() == BufferedImage.TYPE_CUSTOM)
            ? BufferedImage.TYPE_INT_ARGB
            : original.getType();
    BufferedImage rotated = new BufferedImage(newWidth, newHeight, imageType);
    Graphics2D g2d = rotated.createGraphics();

    // 새 이미지의 중심으로 좌표계를 이동한 후 회전
    g2d.translate((newWidth - original.getWidth()) / 2.0, (newHeight - original.getHeight()) / 2.0);
    g2d.rotate(Math.toRadians(degrees), original.getWidth() / 2.0, original.getHeight() / 2.0);
    g2d.drawImage(original, 0, 0, null);
    g2d.dispose();

    return rotated;
  }

  /**
   * BufferedImage를 PNG 바이트 배열로 변환함. 클로바 OCR API로 전달할 때 PNG를 사용함. JPEG는 손실 압축이 발생할 수 있어 텍스트 인식률에 영향을
   * 줄 수 있으므로 PNG 선택.
   */
  private byte[] toByteArray(BufferedImage image) throws IOException {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      boolean written = ImageIO.write(image, "PNG", baos);
      if (!written) {
        throw new IOException("PNG 형식으로 이미지 변환 실패");
      }
      return baos.toByteArray();
    }
  }
}
