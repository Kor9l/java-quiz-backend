package com.korl.javaquiz.api.dto;

public class LocalizedTextDto {

    public String en;
    public String ru;

    public static LocalizedTextDto of(String en, String ru) {
        LocalizedTextDto dto = new LocalizedTextDto();
        dto.en = en;
        dto.ru = ru;
        return dto;
    }
}
