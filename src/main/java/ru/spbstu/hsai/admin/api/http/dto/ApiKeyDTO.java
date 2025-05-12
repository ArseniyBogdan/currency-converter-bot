package ru.spbstu.hsai.admin.api.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.bson.types.ObjectId;

@Data
@AllArgsConstructor
public class ApiKeyDTO {
    @Schema(description = "Секретный ключ", example = "sk_test_1234567890abcdef")
    @JsonProperty("apiKey") String apiKey;
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "ID администратора", example = "507f1f77bcf86cd799439011")
    @JsonProperty("adminId") ObjectId adminId;
}
